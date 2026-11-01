package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.api.dto.RetrievalRequest;
import com.example.ccas.retrieval.api.mapper.RetrievalApiMapper;
import com.example.ccas.retrieval.application.ingestion.*;
import com.example.ccas.retrieval.application.search.*;
import com.example.ccas.retrieval.application.text.*;
import com.example.ccas.retrieval.config.*;
import com.example.ccas.retrieval.domain.type.*;
import com.example.ccas.retrieval.embedding.*;
import com.example.ccas.retrieval.infrastructure.persistence.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in replay, deliberately excluded from ordinary tests. All writes target a disposable container. */
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicPoolDatabaseExperiment {
    static final ObjectMapper JSON=PublicPoolExperimentMain.JSON;
    static final EmbeddingProperties EMBEDDING=new EmbeddingProperties("text-embedding-3-large",1536,"embed-v1-large-1536");
    @Container static final PostgreSQLContainer DB=new PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("ccas_disposable_pool").withUsername("ccas").withPassword("test-only");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);
        r.add("spring.datasource.password",DB::getPassword);r.add("spring.flyway.enabled",()->true);
        r.add("retrieval.search.strategy",()->"BASELINE");
        if(System.getProperty("pool.guideThreshold")!=null) {
            r.add("retrieval.evaluation.guide-min-similarity",()->Double.parseDouble(System.getProperty("pool.guideThreshold")));
            r.add("retrieval.evaluation.threshold-profile-version",()->System.getProperty("pool.thresholdProfileVersion","experimental-ai-pool-v3"));
        }
    }
    @Autowired RetrievalItemRepository items;
    @Autowired KnowledgeSourceIngestionService sources;
    @Autowired PgVectorRetrievalSearchRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired RetrievalEvaluationProperties thresholds;
    @LocalServerPort int port;

    static Path path(String property) {return Path.of(PublicPoolExperimentMain.required(property));}
    static float[] vector(String text) throws Exception {
        String key=PublicPoolExperimentMain.hash((EMBEDDING.toString()+text).getBytes(StandardCharsets.UTF_8));
        float[] v=JSON.readValue(path("pool.cache").resolve(key+".json").toFile(),float[].class);
        PublicPoolExperimentMain.validateVector(v,EMBEDDING.dimensions());return v;
    }
    static EmbeddingResult embedding(float[] v) {return new EmbeddingResult(v,EMBEDDING.model(),EMBEDDING.dimensions(),EMBEDDING.version());}
    @TestConfiguration static class CacheConfiguration {
        @Bean @Primary EmbeddingPort cachedEmbedding() {
            return text -> {try {return embedding(vector(text));} catch(Exception e) {throw new IllegalStateException("Missing or invalid cached vector; no network fallback");}};
        }
    }

    @Test void replayRealVectorsThroughDatabaseAndHttp() throws Exception {
        var docs=PublicPoolExperimentMain.documents(path("pool.corpus"),new SearchTextBuilder());
        var rows=PublicPoolExperimentMain.readLines(path("pool.queries"));
        int expectedCount=PublicPoolExperimentMain.expectedQueryCount();
        assertThat(rows).hasSize(expectedCount);
        var keys=new HashMap<Long,String>();var prototypes=new ArrayList<RetrievalSearchHit>();
        var vectors=new HashMap<Long,float[]>();
        long source=sources.createSource(new CreateKnowledgeSourceCommand(KnowledgeSourceType.OFFICIAL_DOCUMENT,
            "DISPOSABLE EXPERIMENT: source DRAFT unchanged, not production approval",null,null,null,null));
        for(var d:docs) {
            var h=d.prototype();float[] v=vector(d.text());
            // VERIFIED exists only in this throwaway DB to exercise the production predicate.
            // It is a test fixture marker, not a claim of human or usage approval.
            String code=d.type()==RetrievalItemType.CATEGORY_REFERENCE?h.structuredPayload().path("categoryCode").asText():null;
            long id=items.save(new RetrievalItemInsertRow(d.type(),source,d.key(),h.title(),null,h.structuredPayload().toString(),d.text(),
                h.subjectMatterCodes(),h.requestedActionCodes(),h.riskSignalCodes(),null,null,null,code,null,
                ReviewStatus.VERIFIED,true,embedding(v),"complaint-structure-v1",
                d.type()==RetrievalItemType.CATEGORY_REFERENCE?"category-reference-search-text-v1":"official-guide-search-text-v1"));
            keys.put(id,d.key());vectors.put(id,v);
            prototypes.add(new RetrievalSearchHit(id,d.type(),h.title(),h.structuredPayload(),h.subjectMatterCodes(),h.requestedActionCodes(),h.riskSignalCodes(),code,null,0,0));
        }
        double parityWeight=Double.parseDouble(System.getProperty("pool.rerankWeight","0.3"));
        var configs=List.of(
            new RetrievalSearchProperties(new RetrievalSearchProperties.TopK(5,3,3),RetrievalSearchProperties.Strategy.BASELINE,20,0,60),
            new RetrievalSearchProperties(new RetrievalSearchProperties.TopK(5,3,3),RetrievalSearchProperties.Strategy.METADATA,20,parityWeight,60),
            new RetrievalSearchProperties(new RetrievalSearchProperties.TopK(5,3,3),RetrievalSearchProperties.Strategy.CODE_RRF,20,0,60),
            new RetrievalSearchProperties(new RetrievalSearchProperties.TopK(5,3,3),RetrievalSearchProperties.Strategy.COSINE_CODE_RRF,20,parityWeight,60));
        var timings=new LinkedHashMap<String,List<Double>>();configs.forEach(c->timings.put(c.strategy().name(),new ArrayList<>()));
        var httpTimings=new ArrayList<Double>();var details=new ArrayList<Object>();var statuses=new TreeMap<String,Integer>();
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var mapper=new RetrievalApiMapper();int riskCount=0;
        try(var validation=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            for(int qi=0;qi<rows.size();qi++) {
                var row=rows.get(qi);var request=JSON.treeToValue(row.path("request"),RetrievalRequest.class);
                assertThat(validation.getValidator().validate(request)).isEmpty();assertThat(request.maskingCompleted()).isTrue();
                var complaint=mapper.toCommand(request).structuredComplaint();
                String text=new SearchTextBuilder().buildForComplaint(complaint);
                assertThat(text).isEqualTo(row.path("searchText").asText());
                assertThat(PublicPoolExperimentMain.hash(text.getBytes(StandardCharsets.UTF_8))).isEqualTo(row.path("searchTextSha256").asText());
                float[] qv=vector(text);var meta=new StructuredMetadataExtractor().extract(complaint);
                var queryDetails=new LinkedHashMap<String,Object>();
                var expectedBaseline=new HashMap<RetrievalItemType,List<RetrievalSearchHit>>();
                // First 10 inputs warm the DB; measurements exclude those inputs.
                for(int offset=0;offset<configs.size();offset++) {
                    var config=configs.get((qi+offset)%configs.size());
                    long start=System.nanoTime();
                    var actual=new RetrievalSearchService(repository,config).retrieveCandidates(embedding(qv),meta);
                    if(qi>=10)timings.get(config.strategy().name()).add((System.nanoTime()-start)/1e6);
                    assertThat(actual.verifiedCases()).isEmpty();
                    for(var type:List.of(RetrievalItemType.CATEGORY_REFERENCE,RetrievalItemType.OFFICIAL_GUIDE)) {
                        var all=new ArrayList<RetrievalSearchHit>();
                        for(var p:prototypes)if(p.itemType()==type)all.add(new RetrievalSearchHit(p.itemId(),type,p.title(),p.structuredPayload(),
                            p.subjectMatterCodes(),p.requestedActionCodes(),p.riskSignalCodes(),p.verifiedCategoryCode(),null,
                            PublicPoolExperimentMain.cosine(qv,vectors.get(p.itemId())),0));
                        all.sort(Comparator.comparingDouble(RetrievalSearchHit::cosineSimilarity).reversed().thenComparingLong(RetrievalSearchHit::itemId));
                        var codes=all.stream().filter(h->PublicPoolExperimentMain.codeScore(meta,h)>0)
                            .sorted(Comparator.<RetrievalSearchHit>comparingInt(h->PublicPoolExperimentMain.codeScore(meta,h)).reversed()
                                .thenComparing(Comparator.comparingDouble(RetrievalSearchHit::cosineSimilarity).reversed()).thenComparingLong(RetrievalSearchHit::itemId)).toList();
                        boolean fusion=config.strategy()==RetrievalSearchProperties.Strategy.CODE_RRF||config.strategy()==RetrievalSearchProperties.Strategy.COSINE_CODE_RRF;
                        var expected=config.strategy()==RetrievalSearchProperties.Strategy.BASELINE?all.stream().limit(3).toList():CandidateRanker.rank(all,fusion?codes:List.of(),meta,config,3);
                        var found=type==RetrievalItemType.CATEGORY_REFERENCE?actual.categoryReferences():actual.officialGuides();
                        assertThat(found.stream().map(RetrievalSearchHit::itemId).toList()).isEqualTo(expected.stream().map(RetrievalSearchHit::itemId).toList());
                        for(int i=0;i<found.size();i++)assertThat(found.get(i).cosineSimilarity()).isCloseTo(expected.get(i).cosineSimilarity(),within(0.00001));
                        queryDetails.put(config.strategy()+":"+type,found.stream().map(h->Map.of("itemKey",keys.get(h.itemId()),"cosine",h.cosineSimilarity())).toList());
                        if(config.strategy()==RetrievalSearchProperties.Strategy.BASELINE)expectedBaseline.put(type,expected);
                    }
                }
                var call=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/vector/retrieve"))
                    .timeout(Duration.ofSeconds(30)).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(row.path("request").toString())).build();
                long start=System.nanoTime();var response=http.send(call,HttpResponse.BodyHandlers.ofString());
                if(qi>=10)httpTimings.add((System.nanoTime()-start)/1e6);
                assertThat(response.statusCode()).isEqualTo(200);
                var body=JSON.readTree(response.body());boolean risk=!complaint.riskSignals().isEmpty();if(risk)riskCount++;
                assertThat(body.path("riskSignalPresent").asBoolean()).isEqualTo(risk);
                assertThat(body.findValues("embedding")).isEmpty();
                assertThat(body.path("channels").path("verifiedCases").size()).isZero();
                for(var entry:Map.of("categoryReferences",RetrievalItemType.CATEGORY_REFERENCE,"officialGuides",RetrievalItemType.OFFICIAL_GUIDE).entrySet()) {
                    var ids=new ArrayList<Long>();body.path("channels").path(entry.getKey()).forEach(h->ids.add(h.path("itemId").asLong()));
                    assertThat(ids).isEqualTo(expectedBaseline.get(entry.getValue()).stream().map(RetrievalSearchHit::itemId).toList());
                }
                statuses.merge(body.path("decision").path("evidenceStatus").asText(),1,Integer::sum);
                details.add(Map.of("queryKey",row.path("queryKey").asText(),"split",row.path("splitProposal").asText(),
                    "sourceTextSha256",PublicPoolExperimentMain.hash(request.maskedText().getBytes(StandardCharsets.UTF_8)),"rankings",queryDetails));
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from retrieval_query",Integer.class)).isEqualTo(expectedCount);
        assertThat(jdbc.queryForObject("select count(*) from retrieval_decision",Integer.class)).isEqualTo(expectedCount);
        var latency=new LinkedHashMap<String,Object>();timings.forEach((k,v)->latency.put(k,latency(v)));
        var report=new LinkedHashMap<String,Object>();report.put("queryCount",expectedCount);report.put("documentCount",docs.size());
        report.put("queryInputSha256",PublicPoolExperimentMain.hash(Files.readAllBytes(path("pool.queries"))));
        report.put("corpusSha256",PublicPoolExperimentMain.hash(Files.readAllBytes(path("pool.corpus"))));
        report.put("databaseMemoryRankAgreement",1.0);report.put("httpSuccessCount",expectedCount);report.put("inputRiskFlagCount",riskCount);
        report.put("rankParityRerankWeight",parityWeight);
        report.put("riskFlagPreservationRate",1.0);report.put("baselineEvidenceStatusCounts",statuses);
        report.put("thresholds",thresholds);
        report.put("databaseSearchLatencyMs",latency);report.put("cachedEmbeddingHttpLatencyMs",latency(httpTimings));
        report.put("scope","DISPOSABLE_TEST_DB_ONLY; source DRAFT unchanged; test VERIFIED markers are not production approval");
        report.put("limitations",List.of("20-document pool is not a scale benchmark","HTTP includes disk-cached embeddings, not remote GMS latency",
            "Risk preservation does not validate upstream extraction","Evidence statuses are predictions, not independent ground truth",
            "Rerank weight is supplied as a fixed parity configuration, not selected by this DB test"));
        Files.createDirectories(path("pool.output"));
        JSON.writerWithDefaultPrettyPrinter().writeValue(path("pool.output").resolve("database-report.json").toFile(),report);
        JSON.writerWithDefaultPrettyPrinter().writeValue(path("pool.output").resolve("rankings.json").toFile(),details);
    }
    static Map<String,Object> latency(List<Double> samples) {
        var sorted=samples.stream().sorted().toList();
        return Map.of("samples",samples.size(),"p50",sorted.get((int)Math.ceil(sorted.size()*.5)-1),"p95",sorted.get((int)Math.ceil(sorted.size()*.95)-1));
    }
}
