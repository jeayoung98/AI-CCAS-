package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.api.dto.RetrievalRequest;
import com.example.ccas.retrieval.api.mapper.RetrievalApiMapper;
import com.example.ccas.retrieval.application.search.*;
import com.example.ccas.retrieval.application.text.*;
import com.example.ccas.retrieval.config.*;
import com.example.ccas.retrieval.domain.*;
import com.example.ccas.retrieval.domain.type.*;
import com.example.ccas.retrieval.embedding.*;
import com.example.ccas.retrieval.embedding.openai.OpenAiEmbeddingAdapter;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validation;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Isolated vector-pool experiment. Does not promote DRAFT knowledge, access DB, or score unknown evidence labels. */
public final class PublicPoolExperimentMain {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    record Document(String key, RetrievalItemType type, String text, RetrievalSearchHit prototype) {}
    record Query(String key, String split, String group, String text, RetrievalMetadata metadata) {}
    public record Metrics(int queries, int relevantQueries, Double recallAtK, double precisionAtK, Double mrr, Double hitAt1) {}
    record Scores(String strategy, double weight, String split, String channel, Metrics metrics) {}
    record VectorJob(String key, String text, Path cache) {}

    public static void main(String[] args) {
        try { run(); }
        catch (Exception error) {
            System.err.println("Pool experiment failed (" + error.getClass().getSimpleName() + "); cached vectors retained; provider/body text omitted.");
            System.exit(1);
        }
    }

    static void run() throws Exception {
        Path input = Path.of(required("pool.queries")), corpus = Path.of(required("pool.corpus"));
        Path qrels = Path.of(required("pool.qrels")), output = Path.of(required("pool.output"));
        var props = new Properties();
        try (var stream = PublicPoolExperimentMain.class.getResourceAsStream("/application.properties")) { props.load(stream); }
        var embedding = new EmbeddingProperties(props.getProperty("retrieval.embedding.model"),
                Integer.parseInt(props.getProperty("retrieval.embedding.dimensions")), props.getProperty("retrieval.embedding.version"));
        var builder = new SearchTextBuilder();
        var documents = documents(corpus, builder);
        var queries = new ArrayList<Query>();
        var resolved = new HashMap<String, JsonNode>();
        for (var row : readLines(qrels.resolve("resolved-queries.jsonl"))) {
            if (resolved.put(row.path("queryKey").asText(), row) != null) throw new IllegalArgumentException("Duplicate resolved key");
        }
        var summary = JSON.readTree(qrels.resolve("summary.json").toFile());
        if (!summary.path("inputHashes").path("corpus").asText().equals(hash(Files.readAllBytes(corpus))))
            throw new IllegalArgumentException("Corpus/qrels mismatch");
        var groups = new HashMap<String,String>();
        var seen = new HashSet<String>();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (var row : readLines(input)) {
                String key = row.path("queryKey").asText(), split = row.path("splitProposal").asText(), group = row.path("groupId").asText();
                if (!seen.add(key) || !Set.of("dev","test").contains(split) || group.isBlank()) throw new IllegalArgumentException("Invalid query identity");
                String prior = groups.putIfAbsent(group, split);
                if (prior != null && !prior.equals(split)) throw new IllegalArgumentException("Split leakage");
                var request = JSON.treeToValue(row.path("request"), RetrievalRequest.class);
                if (!validator.validate(request).isEmpty()) throw new IllegalArgumentException("Invalid DTO");
                var command = new RetrievalApiMapper().toCommand(request);
                String text = builder.buildForComplaint(command.structuredComplaint());
                var label = resolved.get(key);
                if (!text.equals(row.path("searchText").asText()) || !hash(text.getBytes(StandardCharsets.UTF_8)).equals(row.path("searchTextSha256").asText())
                        || label == null || !label.path("sourceTextSha256").asText().equals(hash(request.maskedText().getBytes(StandardCharsets.UTF_8)))
                        || !split.equals(label.path("splitProposal").asText()) || !group.equals(label.path("groupId").asText()))
                    throw new IllegalArgumentException("Query snapshot changed");
                queries.add(new Query(key, split, group, text, new StructuredMetadataExtractor().extract(command.structuredComplaint())));
            }
        }
        if (queries.size()!=500 || resolved.size()!=500) throw new IllegalArgumentException("Expected 500 resolved queries");
        Map<String,Map<String,Integer>> labels = new HashMap<>();
        Set<String> documentKeys = new HashSet<>();
        documents.forEach(d -> documentKeys.add(d.key));
        for (var row : readLines(qrels.resolve("qrels.jsonl"))) {
            String key=row.path("queryKey").asText(), doc=row.path("itemKey").asText();
            if (!seen.contains(key) || !documentKeys.contains(doc) || !row.path("relevance").isInt()
                    || !Set.of(0,1).contains(row.path("relevance").asInt())) throw new IllegalArgumentException("Invalid qrel");
            if (labels.computeIfAbsent(key, k -> new HashMap<>()).put(doc,row.path("relevance").asInt())!=null) throw new IllegalArgumentException("Duplicate qrel");
        }
        if (labels.size()!=500 || labels.values().stream().anyMatch(m -> !m.keySet().equals(documentKeys))) throw new IllegalArgumentException("Incomplete pool judgments");
        var fingerprint = Map.of("queries",hash(Files.readAllBytes(input)),"corpus",hash(Files.readAllBytes(corpus)),
                "qrels",hash(Files.readAllBytes(qrels.resolve("qrels.jsonl"))),
                "preparedDocumentPool",hash(JSON.writeValueAsBytes(documents)),
                "model",embedding.model(),"dimensions",String.valueOf(embedding.dimensions()),"embeddingVersion",embedding.version());
        Files.createDirectories(output);
        Path manifest=output.resolve("manifest.json");
        if (Files.exists(manifest) && !JSON.readTree(manifest.toFile()).equals(JSON.valueToTree(fingerprint))) throw new IllegalArgumentException("Use new experiment directory");
        JSON.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), fingerprint);
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("prepared-document-pool.json").toFile(), documents);
        Files.createDirectories(output.resolve("cache"));
        var texts=new LinkedHashMap<String,String>();
        documents.forEach(d -> texts.put("document:"+d.key,d.text));
        queries.forEach(q -> texts.put("query:"+q.key,q.text));
        var vectors=embed(texts,embedding,output.resolve("cache"));
        var scores=new ArrayList<Scores>();
        // Weights and selection rule are fixed before test scores are computed.
        scores.addAll(score(queries,documents,labels,vectors,"dev",RetrievalSearchProperties.Strategy.BASELINE,0));
        var selected=new LinkedHashMap<RetrievalSearchProperties.Strategy,Double>();
        selected.put(RetrievalSearchProperties.Strategy.BASELINE,0.0);
        for (var strategy : List.of(RetrievalSearchProperties.Strategy.METADATA, RetrievalSearchProperties.Strategy.COSINE_CODE_RRF)) {
            double best=-1, weight=0;
            for(double w:new double[]{0.05,0.1,0.3}) {
                var trial=score(queries,documents,labels,vectors,"dev",strategy,w);
                scores.addAll(trial);
                var m=trial.stream().filter(s -> s.channel.equals("OFFICIAL_GUIDE")).findFirst().orElseThrow().metrics;
                double value=m.mrr==null ? -1 : m.mrr;
                if(value>best) { best=value; weight=w; }
            }
            selected.put(strategy,weight);
        }
        selected.put(RetrievalSearchProperties.Strategy.CODE_RRF,0.0);
        scores.addAll(score(queries,documents,labels,vectors,"dev",RetrievalSearchProperties.Strategy.CODE_RRF,0));
        for(var entry:selected.entrySet()) scores.addAll(score(queries,documents,labels,vectors,"test",entry.getKey(),entry.getValue()));
        var report=new LinkedHashMap<String,Object>();
        report.put("scope","OFFLINE_REAL_EMBEDDING_LIMITED_POOL_NOT_DATABASE_BENCHMARK");
        report.put("inputHashesAndModel",fingerprint); report.put("queryCount",queries.size()); report.put("documentCount",documents.size());
        report.put("embeddingVectorsSha256",hash(JSON.writeValueAsBytes(new TreeMap<>(vectors))));
        report.put("labelSourceCounts",summary.path("judgmentSourceCounts")); report.put("selectedWeights",selected); report.put("scores",scores);
        report.put("selectionRule","Dev OFFICIAL_GUIDE MRR; tie chooses smallest weight; test evaluated once after selection");
        report.put("limitations",List.of("DRAFT corpus remains unapproved for production; no DB ingestion",
            "AI judgments are not independently verified ground truth","No VERIFIED_CASE corpus or evidence-status ground truth",
            "CATEGORY_REFERENCE has 1 candidate: Recall@3 is saturated","Only 6 guide candidates; results do not establish large-corpus performance",
            "Exact cosine and production CandidateRanker used in memory; no PostgreSQL/HTTP latency claim",
            "Document code mapping is a fixed experimental interpretation, not official classification"));
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("report.json").toFile(),report);
        System.out.println("Completed real embedding pool experiment: 500 queries, 7 candidate documents");
    }

    static List<Document> documents(Path corpus, SearchTextBuilder builder) throws Exception {
        var result=new ArrayList<Document>();
        for(var node:JSON.readTree(corpus.toFile()).path("documents")) {
            String key=node.path("itemKey").asText();
            List<SubjectMatterCode> subjects;
            List<RequestedActionCode> actions;
            switch(key) {
                case "police-investigation-objection-v1" -> {subjects=List.of(SubjectMatterCode.POLICE_SERVICE_COMPLAINT);actions=List.of(RequestedActionCode.APPLY);}
                case "police-application-status-v1" -> {subjects=List.of(SubjectMatterCode.CASE_STATUS);actions=List.of(RequestedActionCode.CHECK_STATUS);}
                case "police-information-disclosure-v1" -> {subjects=List.of(SubjectMatterCode.OTHER);actions=List.of(RequestedActionCode.APPLY,RequestedActionCode.SEARCH);}
                case "police-loss-compensation-v1" -> {subjects=List.of(SubjectMatterCode.OTHER);actions=List.of(RequestedActionCode.APPLY);}
                case "acrc-grievance-definition-v1", "acrc-grievance-application-v1", "acrc-epeople-introduction-v1" -> {
                    subjects=List.of(SubjectMatterCode.POLICE_SERVICE_COMPLAINT,SubjectMatterCode.OTHER);actions=List.of(RequestedActionCode.APPLY,RequestedActionCode.CONSULT);}
                default -> throw new IllegalArgumentException("Unmapped candidate document");
            }
            RetrievalItemType type=RetrievalItemType.valueOf(node.path("itemType").asText());
            Object payload; String text;
            if(type==RetrievalItemType.CATEGORY_REFERENCE) {
                var category=new CategoryReference("GRIEVANCE",node.path("title").asText(),node.path("text").asText(),
                    List.of(node.path("text").asText()),List.of(node.path("boundary").asText()),List.of(),subjects,actions,true);
                payload=category; text=builder.buildForCategoryReference(category);
            } else if(type==RetrievalItemType.OFFICIAL_GUIDE) {
                var guide=new OfficialGuideChunk(node.path("title").asText(),node.path("section").asText(),node.path("text").asText(),subjects,actions,List.of());
                payload=guide;text=builder.buildForOfficialGuide(guide);
            } else throw new IllegalArgumentException("Unsupported pool channel");
            try(var factory=Validation.buildDefaultValidatorFactory()) {
                if(!factory.getValidator().validate(payload).isEmpty()) throw new IllegalArgumentException("Invalid document payload");
            }
            var hit=new RetrievalSearchHit(result.size()+1,type,node.path("title").asText(),JSON.valueToTree(payload),
                subjects.stream().map(Enum::name).toList(),actions.stream().map(Enum::name).toList(),List.of(),null,null,0,0);
            result.add(new Document(key,type,text,hit));
        }
        if(result.size()!=7 || result.stream().map(Document::key).distinct().count()!=7) throw new IllegalArgumentException("Expected seven unique documents");
        return result;
    }

    static Map<String,float[]> embed(Map<String,String> texts, EmbeddingProperties config, Path cache) throws Exception {
        var clientFactory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
        clientFactory.setReadTimeout(Duration.ofSeconds(90));
        String base=Objects.requireNonNull(System.getenv("OPENAI_BASE_URL"));
        var adapter=new OpenAiEmbeddingAdapter(RestClient.builder().baseUrl(base).requestFactory(clientFactory).build(),config,
                new OpenAiProperties(base,System.getenv("OPENAI_API_KEY")));
        var result=new LinkedHashMap<String,float[]>(); var jobs=new ArrayList<VectorJob>();
        for(var entry:texts.entrySet()) {
            String digest=hash((config.toString()+entry.getValue()).getBytes(StandardCharsets.UTF_8));
            Path file=cache.resolve(digest+".json");
            if(Files.exists(file)) {var vector=JSON.readValue(file.toFile(),float[].class);validateVector(vector,config.dimensions());result.put(entry.getKey(),vector);}
            else jobs.add(new VectorJob(entry.getKey(),entry.getValue(),file));
        }
        try(var pool=Executors.newFixedThreadPool(4)) {
            var tasks=new LinkedHashMap<VectorJob,Future<float[]>>();
            for(var job:jobs) tasks.put(job,pool.submit(() -> adapter.embed(job.text).vector()));
            int failures=0;
            for(var entry:tasks.entrySet()) {
                try {
                    var vector=entry.getValue().get();validateVector(vector,config.dimensions());
                    Path temp=entry.getKey().cache.resolveSibling(entry.getKey().cache.getFileName()+".tmp");
                    JSON.writeValue(temp.toFile(),vector);Files.move(temp,entry.getKey().cache,StandardCopyOption.REPLACE_EXISTING);
                    result.put(entry.getKey().key,vector);
                } catch(Exception error) { failures++; }
                if((result.size()+failures)%50==0) System.out.println("Embedding vectors ready: "+result.size()+"; failures: "+failures);
            }
            if(failures>0) throw new IllegalStateException("Embedding failures; rerun with cache");
        }
        return result;
    }

    static void validateVector(float[] v,int dims) {if(v==null||v.length!=dims)throw new IllegalArgumentException();double n=0;for(float x:v){if(!Float.isFinite(x))throw new IllegalArgumentException();n+=(double)x*x;}if(n==0)throw new IllegalArgumentException();}
    static double cosine(float[] a,float[] b) {if(a.length!=b.length)throw new IllegalArgumentException();double dot=0,x=0,y=0;for(int i=0;i<a.length;i++){dot+=(double)a[i]*b[i];x+=(double)a[i]*a[i];y+=(double)b[i]*b[i];}return dot/Math.sqrt(x*y);}
    static int codeScore(RetrievalMetadata q, RetrievalSearchHit h) {return (overlap(q.subjectMatterCodes(),h.subjectMatterCodes())?1:0)+(overlap(q.requestedActionCodes(),h.requestedActionCodes())?1:0)+(overlap(q.riskSignalCodes(),h.riskSignalCodes())?1:0);}
    static boolean overlap(List<String>a,List<String>b){return a.stream().anyMatch(x -> !x.equals("UNKNOWN")&&b.contains(x));}

    static List<Scores> score(List<Query> queries,List<Document> docs,Map<String,Map<String,Integer>> labels,Map<String,float[]> vectors,
            String split,RetrievalSearchProperties.Strategy strategy,double weight) {
        var result=new ArrayList<Scores>();
        for(var type:List.of(RetrievalItemType.CATEGORY_REFERENCE,RetrievalItemType.OFFICIAL_GUIDE)) {
            var predictions=new ArrayList<List<String>>(); var expected=new ArrayList<Set<String>>();
            var keys=new HashMap<Long,String>();docs.forEach(d -> keys.put(d.prototype.itemId(),d.key));
            for(var q:queries) {
                if(!q.split.equals(split))continue;
                var hits=new ArrayList<RetrievalSearchHit>();var gold=new HashSet<String>();
                for(var d:docs) {if(d.type!=type)continue;var h=d.prototype;
                    hits.add(new RetrievalSearchHit(h.itemId(),type,h.title(),h.structuredPayload(),h.subjectMatterCodes(),h.requestedActionCodes(),h.riskSignalCodes(),null,null,
                        cosine(vectors.get("query:"+q.key),vectors.get("document:"+d.key)),0));
                    if(labels.get(q.key).get(d.key)==1)gold.add(d.key);
                }
                hits.sort(Comparator.comparingDouble(RetrievalSearchHit::cosineSimilarity).reversed().thenComparingLong(RetrievalSearchHit::itemId));
                var codeHits=hits.stream().filter(h -> codeScore(q.metadata,h)>0).sorted(Comparator.<RetrievalSearchHit>comparingInt(h -> codeScore(q.metadata,h)).reversed()
                    .thenComparing(Comparator.comparingDouble(RetrievalSearchHit::cosineSimilarity).reversed()).thenComparingLong(RetrievalSearchHit::itemId)).toList();
                var config=new RetrievalSearchProperties(new RetrievalSearchProperties.TopK(5,3,3),strategy,20,weight,60);
                boolean fusion=strategy==RetrievalSearchProperties.Strategy.CODE_RRF||strategy==RetrievalSearchProperties.Strategy.COSINE_CODE_RRF;
                predictions.add(CandidateRanker.rank(hits,fusion?codeHits:List.of(),q.metadata,config,3).stream().map(h -> keys.get(h.itemId())).toList());
                expected.add(gold);
            }
            result.add(new Scores(strategy.name(),weight,split,type.name(),metrics(predictions,expected,3)));
        }
        return result;
    }

    static Metrics metrics(List<List<String>> predictions,List<Set<String>> expected,int k) {
        if(k<1||predictions.isEmpty()||predictions.size()!=expected.size())throw new IllegalArgumentException();
        double recall=0,precision=0,mrr=0,hit=0;int relevant=0;
        for(int i=0;i<predictions.size();i++) {var top=predictions.get(i).stream().limit(k).toList();var gold=expected.get(i);
            long count=top.stream().distinct().filter(gold::contains).count();precision+=count/(double)k;
            if(gold.isEmpty())continue;relevant++;recall+=count/(double)gold.size();
            for(int j=0;j<top.size();j++)if(gold.contains(top.get(j))){mrr+=1.0/(j+1);if(j==0)hit++;break;}
        }
        return new Metrics(predictions.size(),relevant,relevant==0?null:recall/relevant,precision/predictions.size(),relevant==0?null:mrr/relevant,relevant==0?null:hit/relevant);
    }
    static List<JsonNode> readLines(Path path)throws Exception{var out=new ArrayList<JsonNode>();for(String line:Files.readAllLines(path,StandardCharsets.UTF_8))if(!line.isBlank())out.add(JSON.readTree(line));return out;}
    static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static String required(String name){return Objects.requireNonNull(System.getProperty(name),"Missing experiment argument");}
}
