package com.example.ccas.retrieval.application.search;

import com.example.ccas.retrieval.application.evaluation.RetrievalEvidenceEvaluator;
import com.example.ccas.retrieval.application.text.RetrievalMetadata;
import com.example.ccas.retrieval.config.RetrievalEvaluationProperties;
import com.example.ccas.retrieval.config.RetrievalSearchProperties;
import com.example.ccas.retrieval.config.RetrievalSearchProperties.Strategy;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 500 generated ranking stress cases, NOT 500 reviewed complaints or real embeddings. */
class RetrievalStressExperimentTest {
    private static final long DEV_SEED = 2026102301L;
    private static final long TEST_SEED = 2026102302L;
    private static final List<String> FAMILIES = List.of("purpose-near", "wrong-metadata", "unknown-metadata",
            "lexical-distractors", "ambiguity", "no-match", "risk", "multiple-relevant", "outside-vector", "ties");
    private static final RetrievalEvidenceEvaluator EVALUATOR = new RetrievalEvidenceEvaluator(
            new RetrievalEvaluationProperties("provisional-v1", true, .80, .75, .70, .05, .67));
    private static final List<Config> GRID = List.of(new Config(Strategy.BASELINE, 0), new Config(Strategy.CODE_RRF, 0),
            new Config(Strategy.METADATA, .05), new Config(Strategy.METADATA, .1), new Config(Strategy.METADATA, .3),
            new Config(Strategy.COSINE_CODE_RRF, .05), new Config(Strategy.COSINE_CODE_RRF, .1),
            new Config(Strategy.COSINE_CODE_RRF, .3));

    @TestFactory
    Stream<DynamicTest> fiveHundredRankingAndEvidenceContracts() {
        return Stream.concat(generate("dev", DEV_SEED, 10).stream(), generate("test", TEST_SEED, 40).stream())
                .map(s -> DynamicTest.dynamicTest(s.key(), () -> {
                    for (var config : GRID) {
                        var hits = rank(s, config);
                        assertThat(hits).hasSize(3);
                        assertThat(hits.stream().map(RetrievalSearchHit::itemId).distinct().count()).isEqualTo(3);
                        assertThat(hits).extracting(RetrievalSearchHit::rank).containsExactly(1, 2, 3);
                        assertThat(hits).allSatisfy(h -> {
                            var original = s.documents().stream().filter(d -> d.itemId() == h.itemId()).findFirst().orElseThrow();
                            assertThat(h.cosineSimilarity()).isEqualTo(original.cosineSimilarity());
                            assertThat(h.riskSignalCodes()).isEqualTo(original.riskSignalCodes());
                            assertThat(h.itemType()).isEqualTo(RetrievalItemType.CATEGORY_REFERENCE);
                        });
                        assertThat(rank(s, config)).isEqualTo(hits);
                        var decision = EVALUATOR.evaluate(new RetrievalCandidates(List.of(), hits, List.of()), s.risk());
                        assertThat(decision.riskSignalPresent()).isEqualTo(s.risk());
                        if (s.family().equals("no-match"))
                            assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.INSUFFICIENT_EVIDENCE);
                    }
                }));
    }

    @Test
    void selectsOnDevelopmentOnlyThenReportsFrozenTestResults() throws Exception {
        var dev = generate("dev", DEV_SEED, 10);
        var test = generate("test", TEST_SEED, 40);
        // Selection is finalized using dev only. Test scores cannot influence the selected weight.
        var devSummaries = GRID.stream().map(c -> summarize(dev, c)).toList();
        var selected = devSummaries.stream()
                .filter(s -> s.config().strategy() == Strategy.COSINE_CODE_RRF)
                .max(Comparator.comparingDouble((Summary s) -> s.mrr() + s.ambiguityRecall())
                        .thenComparingDouble(s -> -s.config().weight())).orElseThrow().config();
        var frozen = List.of(new Config(Strategy.BASELINE, 0), new Config(Strategy.METADATA, .1),
                new Config(Strategy.CODE_RRF, 0), selected);
        var report = new Report("synthetic-ranking-stress-v1", true,
                "Hand-constructed scores and code rankings; no PostgreSQL, embeddings, Korean semantic evaluation or human review. "
                        + "Ten shared scenario families with independent dev/test numeric draws; this is not a semantic generalization split.",
                DEV_SEED, TEST_SEED, dev.size(), test.size(), 5, 3,
                "Maximize dev MRR + ambiguity recall; tie-break toward lower weight. Metadata 0.1 is prior fixed comparator.",
                selected, devSummaries, frozen.stream().map(c -> summarize(test, c)).toList(),
                FAMILIES.stream().map(f -> new FamilyResult(f, frozen.stream()
                        .map(c -> summarize(test.stream().filter(s -> s.family().equals(f)).toList(), c)).toList())).toList(),
                test.stream().map(s -> new QueryResult(s.key(), s.family(), s.relevant(), s.expectedEvidence(),
                        frozen.stream().map(c -> outcome(s, c)).toList())).toList());
        Path directory = Path.of("build/reports/retrieval");
        Files.createDirectories(directory);
        var mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("stress-500.json").toFile(), report);
        // Persist all generated inputs, so the experiment is auditable beyond random seeds.
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("stress-500-inputs.json").toFile(),
                Stream.concat(dev.stream(), test.stream()).toList());
        assertThat(dev).hasSize(100);
        assertThat(test).hasSize(400);
        assertThat(Stream.concat(dev.stream(), test.stream()).map(Scenario::key).distinct().count()).isEqualTo(500);
    }

    private static List<Scenario> generate(String split, long seed, int perFamily) {
        var random = new Random(seed);
        var result = new ArrayList<Scenario>();
        for (String family : FAMILIES) {
            for (int sample = 0; sample < perFamily; sample++) {
                double gap = .004 + random.nextDouble() * .036;
                double base = .88 + random.nextDouble() * .04;
                var query = metadata("LOST_ITEM", "SEARCH", family.equals("risk"));
                var docs = new ArrayList<RetrievalSearchHit>();
                Set<Long> relevant = Set.of(1L);
                EvidenceStatus expected = null;
                switch (family) {
                    case "purpose-near", "wrong-metadata" -> {
                        docs.add(hit(1, base, "LOST_ITEM", "SEARCH", false, "A"));
                        docs.add(hit(2, base + gap, "LOST_ITEM", "REPORT", false, "A"));
                        if (family.equals("wrong-metadata")) {
                            query = metadata("LOST_ITEM", "REPORT", false);
                            // A correct semantic lead faces an incorrect extracted action.
                            docs.set(1, hit(2, base - gap, "LOST_ITEM", "REPORT", false, "A"));
                        }
                    }
                    case "unknown-metadata" -> {
                        query = metadata("UNKNOWN", "UNKNOWN", false);
                        docs.add(hit(1, base, "UNKNOWN", "UNKNOWN", false, "A"));
                        docs.add(hit(2, base - gap, "LOST_ITEM", "SEARCH", false, "A"));
                    }
                    case "lexical-distractors" -> {
                        docs.add(hit(1, base, "UNKNOWN", "UNKNOWN", false, "A"));
                        for (int j = 2; j <= 7; j++)
                            docs.add(hit(j, .65 - j * .04 + random.nextDouble() * .01, "LOST_ITEM", "SEARCH", false, "B"));
                    }
                    case "ambiguity" -> {
                        docs.add(hit(1, .97, "UNKNOWN", "UNKNOWN", false, "A"));
                        docs.add(hit(2, .97 - gap, "UNKNOWN", "UNKNOWN", false, "B"));
                        for (int j = 3; j <= 7; j++)
                            docs.add(hit(j, .85 - j * .03, "LOST_ITEM", "SEARCH", false, "A"));
                        relevant = Set.of(1L, 2L);
                        expected = EvidenceStatus.AMBIGUOUS;
                    }
                    case "no-match" -> {
                        relevant = Set.of();
                        expected = EvidenceStatus.INSUFFICIENT_EVIDENCE;
                        for (int j = 1; j <= 7; j++)
                            docs.add(hit(j, random.nextDouble() * .5, "LOST_ITEM", "SEARCH", false, "A"));
                    }
                    case "risk" -> {
                        docs.add(hit(1, base, "LOST_ITEM", "SEARCH", true, "A"));
                        docs.add(hit(2, base + gap, "LOST_ITEM", "SEARCH", false, "A"));
                    }
                    case "multiple-relevant" -> {
                        docs.add(hit(1, base, "LOST_ITEM", "SEARCH", false, "A"));
                        docs.add(hit(2, base - gap, "LOST_ITEM", "SEARCH", false, "A"));
                        docs.add(hit(3, base + gap, "LOST_ITEM", "REPORT", false, "A"));
                        relevant = Set.of(1L, 2L);
                    }
                    case "outside-vector" -> {
                        docs.add(hit(1, .70 + gap, "LOST_ITEM", "SEARCH", false, "A"));
                        for (int j = 2; j <= 7; j++)
                            docs.add(hit(j, .95 - j * .02, "UNKNOWN", "UNKNOWN", false, "B"));
                    }
                    case "ties" -> {
                        docs.add(hit(1, base, "LOST_ITEM", "SEARCH", false, "A"));
                        docs.add(hit(2, base, "LOST_ITEM", "SEARCH", false, "A"));
                        relevant = Set.of(2L);
                    }
                    default -> throw new IllegalStateException(family);
                }
                for (int j = docs.size() + 1; j <= 12; j++)
                    docs.add(hit(j, .03 + random.nextDouble() * .1, "OTHER", "CONSULT", false, "Z"));
                result.add(new Scenario(split + "-" + family + "-" + sample, family, query, List.copyOf(docs),
                        relevant, expected, family.equals("risk")));
            }
        }
        return List.copyOf(result);
    }

    private static List<RetrievalSearchHit> rank(Scenario s, Config config) {
        var cosineOrder = Comparator.comparingDouble(RetrievalSearchHit::cosineSimilarity).reversed()
                .thenComparingLong(RetrievalSearchHit::itemId);
        var vectors = s.documents().stream().sorted(cosineOrder).limit(5).toList();
        boolean fusion = config.strategy() == Strategy.CODE_RRF || config.strategy() == Strategy.COSINE_CODE_RRF;
        var codes = fusion ? s.documents().stream().filter(h -> codeScore(s.query(), h) > 0)
                .sorted(Comparator.<RetrievalSearchHit>comparingInt(h -> codeScore(s.query(), h)).reversed()
                        .thenComparing(cosineOrder)).limit(5).toList() : List.<RetrievalSearchHit>of();
        return CandidateRanker.rank(vectors, codes, s.query(), new RetrievalSearchProperties(
                new RetrievalSearchProperties.TopK(3, 3, 3), config.strategy(), 5, config.weight(), 60), 3);
    }
    private static int codeScore(RetrievalMetadata q, RetrievalSearchHit h) {
        return overlap(q.subjectMatterCodes(), h.subjectMatterCodes()) + overlap(q.requestedActionCodes(), h.requestedActionCodes())
                + overlap(q.riskSignalCodes(), h.riskSignalCodes());
    }
    private static int overlap(List<String> a, List<String> b) {
        return a.stream().anyMatch(c -> !c.equals("UNKNOWN") && b.contains(c)) ? 1 : 0;
    }
    private static Outcome outcome(Scenario s, Config c) {
        var hits = rank(s, c);
        var ids = hits.stream().map(RetrievalSearchHit::itemId).toList();
        long count = ids.stream().filter(s.relevant()::contains).count();
        int first = IntStream.range(0, ids.size()).filter(i -> s.relevant().contains(ids.get(i))).findFirst().orElse(-1);
        var status = EVALUATOR.evaluate(new RetrievalCandidates(List.of(), hits, List.of()), s.risk()).evidenceStatus();
        return new Outcome(c, ids, s.relevant().isEmpty() ? null : count / (double) s.relevant().size(),
                count / 3.0, first < 0 ? 0 : 1.0 / (first + 1), first == 0 ? 1 : 0, status);
    }
    private static Summary summarize(List<Scenario> scenarios, Config c) {
        double recall = 0, precision = 0, mrr = 0, hit = 0;
        int labeled = 0, ambiguity = 0, detected = 0, noMatch = 0, rejected = 0, oracle = 0, matched = 0;
        for (var s : scenarios) {
            var o = outcome(s, c);
            precision += o.precisionAt3();
            if (o.recallAt3() != null) { labeled++; recall += o.recallAt3(); mrr += o.mrr(); hit += o.hitAt1(); }
            if (s.expectedEvidence() != null) { oracle++; if (s.expectedEvidence() == o.evidence()) matched++; }
            if (s.family().equals("ambiguity")) { ambiguity++; if (o.evidence() == EvidenceStatus.AMBIGUOUS) detected++; }
            if (s.family().equals("no-match")) { noMatch++; if (o.evidence() == EvidenceStatus.INSUFFICIENT_EVIDENCE) rejected++; }
        }
        return new Summary(c, scenarios.size(), labeled, labeled == 0 ? null : hit/labeled,
                labeled == 0 ? null : recall/labeled, precision/scenarios.size(), labeled == 0 ? null : mrr/labeled,
                ambiguity, ambiguity == 0 ? null : detected/(double)ambiguity, noMatch, noMatch == 0 ? null : rejected/(double)noMatch,
                oracle, oracle == 0 ? null : matched/(double)oracle);
    }
    private static RetrievalMetadata metadata(String subject, String action, boolean risk) {
        return new RetrievalMetadata(List.of(subject), List.of(action), risk ? List.of("WEAPON") : List.of(), null, null, null);
    }
    private static RetrievalSearchHit hit(long id, double cosine, String subject, String action, boolean risk, String category) {
        return new RetrievalSearchHit(id, RetrievalItemType.CATEGORY_REFERENCE, "synthetic-" + id,
                JsonNodeFactory.instance.objectNode(), List.of(subject), List.of(action),
                risk ? List.of("WEAPON") : List.of(), category, null, cosine, (int)id);
    }
    record Config(Strategy strategy, double weight) {}
    record Scenario(String key, String family, RetrievalMetadata query, List<RetrievalSearchHit> documents,
                    Set<Long> relevant, EvidenceStatus expectedEvidence, boolean risk) {}
    record Outcome(Config config, List<Long> returnedIds, Double recallAt3, double precisionAt3,
                   double mrr, double hitAt1, EvidenceStatus evidence) {}
    record Summary(Config config, int count, int relevantQueryCount, Double hitAt1, Double recallAt3,
                   double precisionAt3, Double mrr, int ambiguityCount, Double ambiguityRecall, int noMatchCount, Double noMatchRecall,
                   int evidenceOracleCount, Double evidenceAccuracyOnOracleSubset) {}
    record FamilyResult(String family, List<Summary> strategies) {}
    record QueryResult(String key, String family, Set<Long> relevant, EvidenceStatus expectedEvidence, List<Outcome> outcomes) {}
    record Report(String version, boolean synthetic, String limitations, long devSeed, long testSeed,
                  int devCount, int testCount, int candidateLimit, int topK, String selectionRule, Config selected,
                  List<Summary> development, List<Summary> heldOut, List<FamilyResult> families, List<QueryResult> queries) {}
}
