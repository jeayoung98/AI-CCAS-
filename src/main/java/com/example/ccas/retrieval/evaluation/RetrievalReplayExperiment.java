package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.application.evaluation.RetrievalEvidenceEvaluator;
import com.example.ccas.retrieval.application.search.*;
import com.example.ccas.retrieval.application.text.StructuredMetadataExtractor;
import com.example.ccas.retrieval.config.*;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.infrastructure.persistence.PgVectorRetrievalSearchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Read-only paired replay: no ingestion, external API calls, query logging or threshold tuning. */
@Service
public class RetrievalReplayExperiment {
    private final PgVectorRetrievalSearchRepository repository;
    private final JdbcTemplate jdbc;
    private final RetrievalEvidenceEvaluator evaluator;
    private final RetrievalEvaluationProperties thresholds;

    public RetrievalReplayExperiment(PgVectorRetrievalSearchRepository repository, JdbcTemplate jdbc,
            RetrievalEvidenceEvaluator evaluator, RetrievalEvaluationProperties thresholds) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.evaluator = evaluator;
        this.thresholds = thresholds;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Report run(EvaluationDataset dataset, Map<String, EmbeddingResult> queryEmbeddings,
            List<RetrievalSearchProperties> configurations, int warmups, int repetitions) {
        if (warmups < 0 || repetitions < 1 || configurations.isEmpty() || dataset.queries().isEmpty()) {
            throw new IllegalArgumentException("Nonempty experiment and positive repetitions required");
        }
        if (!dataset.thresholdProfileVersion().equals(thresholds.thresholdProfileVersion())) {
            throw new IllegalArgumentException("Threshold profile mismatch");
        }
        Map<Long, String> keys = new LinkedHashMap<>();
        Set<String> corpusModels = new HashSet<>();
        Map<String, String> expectedTypes = new HashMap<>();
        dataset.knowledgeItems().forEach(item -> {
            if (expectedTypes.put(item.itemKey(), item.itemType().name()) != null)
                throw new IllegalArgumentException("Duplicate corpus item key");
        });
        jdbc.query("""
                SELECT id, external_key, item_type, embedding_model FROM retrieval_item
                WHERE review_status='VERIFIED' AND is_active AND embedding_version=? ORDER BY id
                """, rs -> {
            String key = rs.getString("external_key");
            if (!Objects.equals(expectedTypes.get(key), rs.getString("item_type")))
                throw new IllegalArgumentException("Corpus mismatch: use an isolated database");
            if (keys.containsValue(key)) throw new IllegalArgumentException("Duplicate corpus key in database");
            keys.put(rs.getLong("id"), key);
            corpusModels.add(rs.getString("embedding_model"));
        }, dataset.embeddingVersion());
        if (!new HashSet<>(keys.values()).equals(expectedTypes.keySet())) {
            throw new IllegalArgumentException("Missing or extra corpus items");
        }
        Set<String> seen = new HashSet<>();
        var queryDigest = digest();
        for (var query : dataset.queries()) {
            if (!seen.add(query.queryKey())) throw new IllegalArgumentException("Duplicate query key");
            var embedding = queryEmbeddings.get(query.queryKey());
            if (embedding == null || !dataset.embeddingVersion().equals(embedding.version()))
                throw new IllegalArgumentException("Missing query embedding or version mismatch");
            if (corpusModels.size() != 1 || !corpusModels.contains(embedding.model()))
                throw new IllegalArgumentException("Corpus/query embedding model mismatch");
            queryDigest.update((query.toString() + Arrays.toString(embedding.vector()))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var labels = Map.of("VERIFIED_CASE", query.expectedRelevantVerifiedCaseKeys(),
                    "CATEGORY_REFERENCE", query.expectedRelevantCategoryReferenceKeys(),
                    "OFFICIAL_GUIDE", query.expectedRelevantOfficialGuideKeys());
            for (var label : labels.entrySet()) {
                if (label.getValue().stream().anyMatch(key -> !label.getKey().equals(expectedTypes.get(key))))
                    throw new IllegalArgumentException("Relevance label references an unknown item or wrong channel");
            }
        }
        var corpusDigest = digest();
        jdbc.query("""
                SELECT jsonb_build_array(external_key,item_type,structured_payload,search_text,
                    subject_matter_codes,requested_action_codes,risk_signal_codes,place_type,relationship_type,
                    ongoing_status,verified_category_code,verified_route_code,embedding::text,
                    embedding_model,embedding_version,structure_version,search_text_version)::text AS snapshot
                FROM retrieval_item WHERE review_status='VERIFIED' AND is_active AND embedding_version=?
                ORDER BY external_key, item_type
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> corpusDigest.update(
                rs.getString("snapshot").getBytes(java.nio.charset.StandardCharsets.UTF_8)), dataset.embeddingVersion());
        var extractor = new StructuredMetadataExtractor();
        var samples = new ArrayList<List<Double>>();
        var results = new ArrayList<List<EvaluationQueryResult>>();
        var details = new ArrayList<List<QueryDetail>>();
        var services = configurations.stream().map(c -> new RetrievalSearchService(repository, c)).toList();
        configurations.forEach(c -> { samples.add(new ArrayList<>()); results.add(new ArrayList<>()); details.add(new ArrayList<>()); });
        // Rotate strategy order to reduce systematic warm-cache/first-strategy bias.
        for (int round = -warmups; round < repetitions; round++) {
            for (int qi = 0; qi < dataset.queries().size(); qi++) {
                var query = dataset.queries().get(qi);
                var metadata = extractor.extract(query.structuredComplaint());
                for (int offset = 0; offset < services.size(); offset++) {
                    int strategy = Math.floorMod(round + qi + offset, services.size());
                    long start = System.nanoTime();
                    var candidates = services.get(strategy).retrieveCandidates(queryEmbeddings.get(query.queryKey()), metadata);
                    double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
                    if (round < 0) continue;
                    samples.get(strategy).add(elapsedMs);
                    if (round != 0) continue;
                    boolean risk = !query.structuredComplaint().riskSignals().isEmpty();
                    var decision = evaluator.evaluate(candidates, risk);
                    results.get(strategy).add(new EvaluationQueryResult(query.queryKey(), query.expectedEvidenceStatus(),
                            decision.evidenceStatus(), decision.evidenceStatus() == query.expectedEvidenceStatus(),
                            query.expectedRiskSignalPresent(), risk, itemKeys(candidates.verifiedCases(), keys),
                            itemKeys(candidates.categoryReferences(), keys), itemKeys(candidates.officialGuides(), keys)));
                    details.get(strategy).add(new QueryDetail(query.queryKey(),
                            scores(candidates.verifiedCases(), keys), scores(candidates.categoryReferences(), keys),
                            scores(candidates.officialGuides(), keys), decision.categoryMargin()));
                }
            }
        }
        var variants = new ArrayList<Variant>();
        for (int i = 0; i < configurations.size(); i++) {
            var config = configurations.get(i);
            var metrics = new EvaluationMetricsCalculator().calculate(dataset, thresholds.provisional(), results.get(i),
                    config.topK().verifiedCase(), config.topK().categoryReference(), config.topK().officialGuide());
            variants.add(new Variant(config, metrics, percentile(samples.get(i), .50), percentile(samples.get(i), .95),
                    samples.get(i).size(), binary(dataset, results.get(i), false), binary(dataset, results.get(i), true), details.get(i)));
        }
        return new Report("replay-v1-metrics-v2", dataset.datasetId(), dataset.synthetic(), keys.size(),
                dataset.queries().size(), dataset.embeddingVersion(), queryEmbeddings.values().stream()
                        .map(EmbeddingResult::model).distinct().sorted().toList(),
                "Frozen supplied query embeddings; no OpenAI calls. Latency is JDBC retrieval + mapping + ranking, not API E2E.",
                HexFormat.of().formatHex(corpusDigest.digest()), HexFormat.of().formatHex(queryDigest.digest()),
                jdbc.queryForObject("SELECT version()", String.class), System.getProperty("java.version"),
                warmups, repetitions, variants);
    }

    private List<String> itemKeys(List<RetrievalSearchHit> hits, Map<Long, String> keys) {
        return hits.stream().map(h -> Objects.requireNonNull(keys.get(h.itemId()))).toList();
    }
    private List<Hit> scores(List<RetrievalSearchHit> hits, Map<Long, String> keys) {
        return hits.stream().map(h -> new Hit(keys.get(h.itemId()), h.rank(), h.cosineSimilarity())).toList();
    }
    private BinaryMetrics binary(EvaluationDataset dataset, List<EvaluationQueryResult> results, boolean ambiguity) {
        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (int i = 0; i < results.size(); i++) {
            var q = dataset.queries().get(i);
            boolean expected = ambiguity ? q.ambiguityExpected() : q.expectedRelevantVerifiedCaseKeys().isEmpty()
                    && q.expectedRelevantCategoryReferenceKeys().isEmpty() && q.expectedRelevantOfficialGuideKeys().isEmpty();
            boolean actual = results.get(i).actualEvidenceStatus() == (ambiguity
                    ? EvidenceStatus.AMBIGUOUS : EvidenceStatus.INSUFFICIENT_EVIDENCE);
            if (expected && actual) tp++;
            else if (actual) fp++;
            else if (expected) fn++;
            else tn++;
        }
        return new BinaryMetrics(tp, fp, fn, tn, tp + fp == 0 ? null : tp / (double) (tp + fp),
                tp + fn == 0 ? null : tp / (double) (tp + fn));
    }
    static double percentile(List<Double> values, double fraction) {
        var sorted = values.stream().sorted().toList();
        return sorted.get(Math.max(0, (int) Math.ceil(fraction * sorted.size()) - 1));
    }
    private java.security.MessageDigest digest() {
        try { return java.security.MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public record BinaryMetrics(int truePositive, int falsePositive, int falseNegative, int trueNegative,
                                Double precision, Double recall) {}
    public record Hit(String itemKey, int rank, double cosineSimilarity) {}
    public record QueryDetail(String queryKey, List<Hit> cases, List<Hit> categories, List<Hit> guides, Double cosineCategoryMargin) {}
    public record Variant(RetrievalSearchProperties configuration, EvaluationReport metrics,
                          double retrievalP50Ms, double retrievalP95Ms, int latencySampleCount,
                          BinaryMetrics allChannelsNoMatch, BinaryMetrics ambiguity, List<QueryDetail> queryDetails) {}
    public record Report(String configurationVersion, String datasetId, boolean synthetic, int documentCount,
                         int queryCount, String embeddingVersion, List<String> embeddingModels, String latencyScope,
                         String corpusSha256, String queriesSha256, String postgresVersion, String javaVersion,
                         int warmups, int repetitions, List<Variant> variants) {}
}
