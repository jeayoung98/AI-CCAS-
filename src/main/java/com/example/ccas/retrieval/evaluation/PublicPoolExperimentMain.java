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
        Path output = Path.of(required("pool.output"));
        var props = new Properties();
        try (var stream = PublicPoolExperimentMain.class.getResourceAsStream("/application.properties")) { props.load(stream); }
        var embedding = new EmbeddingProperties(props.getProperty("retrieval.embedding.model"),
                Integer.parseInt(props.getProperty("retrieval.embedding.dimensions")), props.getProperty("retrieval.embedding.version"));
        var builder = new SearchTextBuilder();
        var documents = documents(corpus, builder);
        if(Boolean.getBoolean("pool.prepareOnly")) {
            var texts=new LinkedHashMap<String,String>();
            documents.forEach(d -> texts.put("document:"+d.key,d.text));
            try(var factory=Validation.buildDefaultValidatorFactory()) {
                for(var row:readLines(input)) {
                    var request=JSON.treeToValue(row.path("request"),RetrievalRequest.class);
                    if(!factory.getValidator().validate(request).isEmpty() || !request.maskingCompleted()) throw new IllegalArgumentException("Invalid prepared request");
                    String text=builder.buildForComplaint(new RetrievalApiMapper().toCommand(request).structuredComplaint());
                    if(!text.equals(row.path("searchText").asText()) || !hash(text.getBytes(StandardCharsets.UTF_8)).equals(row.path("searchTextSha256").asText())) throw new IllegalArgumentException("Prepared text mismatch");
                    if(texts.put("query:"+row.path("queryKey").asText(),text)!=null) throw new IllegalArgumentException("Duplicate prepared key");
                }
            }
            Path cache=Path.of(System.getProperty("pool.cache",output.resolve("cache").toString()));
            Files.createDirectories(cache);
            embed(texts,embedding,cache);
            System.out.println("Prepared vectors only: "+texts.size()+"; no labels or metrics evaluated");
            return;
        }
        Path qrels = Path.of(required("pool.qrels"));
        String benchmarkId="UNLOCKED_EXPERIMENT";
        if(System.getProperty("pool.benchmarkLock")!=null) {
            var actual=new LinkedHashMap<String,String>();
            actual.put("queries",hash(Files.readAllBytes(input)));actual.put("corpus",hash(Files.readAllBytes(corpus)));
            actual.put("qrels",hash(Files.readAllBytes(qrels.resolve("qrels.jsonl"))));
            actual.put("resolvedQueries",hash(Files.readAllBytes(qrels.resolve("resolved-queries.jsonl"))));
            actual.put("qrelsSummary",hash(Files.readAllBytes(qrels.resolve("summary.json"))));
            try(var stream=PublicPoolExperimentMain.class.getResourceAsStream("/application.properties")) {
                actual.put("embeddingAndThresholdConfig",hash(stream.readAllBytes()));
            }
            benchmarkId=verifyBenchmark(JSON.readTree(Path.of(System.getProperty("pool.benchmarkLock")).toFile()),actual);
        }
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
        int expectedCount = expectedQueryCount();
        if (queries.size()!=expectedCount || resolved.size()!=expectedCount) throw new IllegalArgumentException("Unexpected resolved query count");
        Map<String,Map<String,Integer>> labels = new HashMap<>();
        Set<String> documentKeys = new HashSet<>();
        documents.forEach(d -> documentKeys.add(d.key));
        for (var row : readLines(qrels.resolve("qrels.jsonl"))) {
            String key=row.path("queryKey").asText(), doc=row.path("itemKey").asText();
            if (!seen.contains(key) || !documentKeys.contains(doc) || !row.path("relevance").isInt()
                    || !Set.of(0,1).contains(row.path("relevance").asInt())) throw new IllegalArgumentException("Invalid qrel");
            if (labels.computeIfAbsent(key, k -> new HashMap<>()).put(doc,row.path("relevance").asInt())!=null) throw new IllegalArgumentException("Duplicate qrel");
        }
        if (labels.size()!=expectedCount || labels.values().stream().anyMatch(m -> !m.keySet().equals(documentKeys))) throw new IllegalArgumentException("Incomplete pool judgments");
        var fingerprint = Map.of("queries",hash(Files.readAllBytes(input)),"corpus",hash(Files.readAllBytes(corpus)),
                "qrels",hash(Files.readAllBytes(qrels.resolve("qrels.jsonl"))),
                "preparedDocumentPool",hash(JSON.writeValueAsBytes(documents)),
                "model",embedding.model(),"dimensions",String.valueOf(embedding.dimensions()),"embeddingVersion",embedding.version());
        Files.createDirectories(output);
        Path manifest=output.resolve("manifest.json");
        if (Files.exists(manifest) && !JSON.readTree(manifest.toFile()).equals(JSON.valueToTree(fingerprint))) throw new IllegalArgumentException("Use new experiment directory");
        JSON.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), fingerprint);
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("prepared-document-pool.json").toFile(), documents);
        Path cache=Path.of(System.getProperty("pool.cache", output.resolve("cache").toString()));
        Files.createDirectories(cache);
        var texts=new LinkedHashMap<String,String>();
        documents.forEach(d -> texts.put("document:"+d.key,d.text));
        queries.forEach(q -> texts.put("query:"+q.key,q.text));
        var vectors=embed(texts,embedding,cache);
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
        report.put("benchmarkId",benchmarkId);
        report.put("scope","OFFLINE_REAL_EMBEDDING_LIMITED_POOL_NOT_DATABASE_BENCHMARK");
        report.put("inputHashesAndModel",fingerprint); report.put("queryCount",queries.size()); report.put("documentCount",documents.size());
        report.put("embeddingVectorsSha256",hash(JSON.writeValueAsBytes(new TreeMap<>(vectors))));
        report.put("labelSourceCounts",summary.path("judgmentSourceCounts")); report.put("selectedWeights",selected); report.put("scores",scores);
        report.put("selectionRule","Dev OFFICIAL_GUIDE MRR; tie chooses smallest weight; test evaluated after per-run selection; same benchmark may be reused across versions");
        report.put("limitations",List.of("DRAFT corpus remains unapproved for production; no DB ingestion",
            "AI judgments are not independently verified ground truth","No VERIFIED_CASE corpus or evidence-status ground truth",
            "Small experimental candidate pool; results do not establish large-corpus performance",
            "Exact cosine and production CandidateRanker used in memory; no PostgreSQL/HTTP latency claim",
            "Document code mapping is a fixed experimental interpretation, not official classification"));
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("report.json").toFile(),report);
        System.out.println("Completed real embedding pool experiment: "+queries.size()+" queries, "+documents.size()+" candidate documents");
    }

    static int expectedQueryCount() {
        int count = Integer.parseInt(System.getProperty("pool.expectedCount", "500"));
        if (count < 1) throw new IllegalArgumentException("Invalid expected query count");
        return count;
    }

    static List<Document> documents(Path corpus, SearchTextBuilder builder) throws Exception {
        var result=new ArrayList<Document>();
        for(var node:JSON.readTree(corpus.toFile()).path("documents")) {
            String key=node.path("itemKey").asText();
            List<SubjectMatterCode> subjects;
            List<RequestedActionCode> actions;
            if (node.has("experimentalMetadata")) {
                var metadata=node.path("experimentalMetadata");
                subjects=enumCodes(metadata.path("subjectMatterCodes"), SubjectMatterCode.class);
                actions=enumCodes(metadata.path("requestedActionCodes"), RequestedActionCode.class);
            } else switch(key) {
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
                String code="GRIEVANCE";
                if(node.has("experimentalMetadata")) {
                    var categoryCode=node.path("experimentalMetadata").path("categoryCode");
                    if(!categoryCode.isTextual()) throw new IllegalArgumentException("Missing experimental category code");
                    code=categoryCode.asText();
                }
                if(code.isBlank()) throw new IllegalArgumentException("Missing experimental category code");
                var category=new CategoryReference(code,node.path("title").asText(),node.path("text").asText(),
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
        if(result.isEmpty() || result.stream().map(Document::key).anyMatch(String::isBlank)
                || result.stream().map(Document::key).distinct().count()!=result.size()) throw new IllegalArgumentException("Expected unique nonempty documents");
        return result;
    }

    static <T extends Enum<T>> List<T> enumCodes(JsonNode array, Class<T> type) {
        if(!array.isArray() || array.isEmpty()) throw new IllegalArgumentException("Missing document metadata codes");
        var codes=new ArrayList<T>();
        for(var code:array) codes.add(Enum.valueOf(type,code.asText()));
        if(new HashSet<>(codes).size()!=codes.size()) throw new IllegalArgumentException("Duplicate metadata code");
        return List.copyOf(codes);
    }

    static String verifyBenchmark(JsonNode lock, Map<String,String> actual) {
        var keys=new HashSet<String>();lock.path("inputs").fieldNames().forEachRemaining(keys::add);
        if(!keys.equals(actual.keySet()) || lock.path("benchmarkId").asText().isBlank()) throw new IllegalArgumentException("Incomplete benchmark lock");
        for(var entry:actual.entrySet()) if(!entry.getValue().equals(lock.path("inputs").path(entry.getKey()).path("sha256").asText()))
            throw new IllegalArgumentException("Frozen benchmark changed; use a new version");
        return lock.path("benchmarkId").asText();
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
                predictions.add(CandidateRanker.rank(hits.stream().limit(config.candidateLimit()).toList(),
                    fusion?codeHits.stream().limit(config.candidateLimit()).toList():List.of(),q.metadata,config,3).stream().map(h -> keys.get(h.itemId())).toList());
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
