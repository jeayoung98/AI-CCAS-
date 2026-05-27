package com.example.ccas.retrieval.experiment;

import com.example.ccas.retrieval.experiment.dto.ExperimentComplaintRecord;
import com.example.ccas.retrieval.experiment.dto.ExpectedSimilarityPairs;
import com.example.ccas.retrieval.experiment.report.ExcludedExperimentRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentInputLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ExperimentInputLoader loader = new ExperimentInputLoader(objectMapper);
    private final ExperimentCorpusSelector corpusSelector = new ExperimentCorpusSelector();
    private final ExpectedPairsValidator expectedPairsValidator = new ExpectedPairsValidator();

    @TempDir
    Path tempDir;

    @Test
    void loadsOutputJsonWhileIgnoringUnknownFields() throws Exception {
        Path input = tempDir.resolve("output.json");
        Files.writeString(input, """
                [
                  {
                    "id": 1,
                    "title": "버스 정거장 개선",
                    "ok": true,
                    "original_content": "raw text must be ignored",
                    "dept": "transport",
                    "riskSignals": [{"code": "WEAPON"}],
                    "subjectMatters": [{"code": "LOST_ITEM"}],
                    "requestedActions": [{"code": "REPORT"}],
                    "structured": {
                      "factualSummary": "버스 정거장 위치 조정을 요청하는 민원",
                      "context": {"placeType": "ROAD", "timePattern": "ONGOING", "label": "ignored"},
                      "observedFacts": [{"value": "버스 정거장 대기 불편"}]
                    }
                  }
                ]
                """);

        List<ExperimentComplaintRecord> records = loader.loadComplaintRecords(input);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().id()).isEqualTo(1);
        assertThat(records.getFirst().structured().observedFacts().getFirst().value())
                .isEqualTo("버스 정거장 대기 불편");
    }

    @Test
    void selectsCorpusAndReportsOnlyExcludedIdsAndReasons() {
        List<ExperimentComplaintRecord> records = List.of(
                validRecord(1),
                new ExperimentComplaintRecord(2, "실패", false, validRecord(2).structured()),
                new ExperimentComplaintRecord(3, "구조 없음", true, null),
                new ExperimentComplaintRecord(4, "요약 없음", true,
                        new com.example.ccas.retrieval.experiment.dto.ExperimentStructuredComplaint(
                                " ", validRecord(4).structured().context(), validRecord(4).structured().observedFacts())),
                new ExperimentComplaintRecord(5, "사실 없음", true,
                        new com.example.ccas.retrieval.experiment.dto.ExperimentStructuredComplaint(
                                "요약", validRecord(5).structured().context(), List.of())),
                new ExperimentComplaintRecord(6, "사실 값 없음", true,
                        new com.example.ccas.retrieval.experiment.dto.ExperimentStructuredComplaint(
                                "요약", validRecord(6).structured().context(),
                                List.of(new com.example.ccas.retrieval.experiment.dto.ExperimentObservedFact(" ")))),
                new ExperimentComplaintRecord(7, "맥락 없음", true,
                        new com.example.ccas.retrieval.experiment.dto.ExperimentStructuredComplaint(
                                "요약", null, validRecord(7).structured().observedFacts())),
                new ExperimentComplaintRecord(8, " ", true, validRecord(8).structured())
        );

        ExperimentCorpus corpus = corpusSelector.select(records);

        assertThat(corpus.records()).extracting(ExperimentComplaintRecord::id).containsExactly(1L);
        assertThat(corpus.excludedRecords()).hasSize(7);
        assertThat(reasons(corpus.excludedRecords(), 2)).contains("SOURCE_MARKED_FAILED");
        assertThat(reasons(corpus.excludedRecords(), 3)).contains("STRUCTURED_NULL");
        assertThat(reasons(corpus.excludedRecords(), 4)).contains("FACTUAL_SUMMARY_BLANK");
        assertThat(reasons(corpus.excludedRecords(), 5)).contains("OBSERVED_FACTS_EMPTY");
        assertThat(reasons(corpus.excludedRecords(), 6)).contains("OBSERVED_FACT_VALUE_BLANK");
        assertThat(reasons(corpus.excludedRecords(), 7)).contains("CONTEXT_NULL");
        assertThat(reasons(corpus.excludedRecords(), 8)).contains("TITLE_BLANK");
    }

    @Test
    void validatesExpectedPairsAgainstSelectedCorpus() throws Exception {
        Path expected = tempDir.resolve("expected-pairs.json");
        Files.writeString(expected, """
                {
                  "queries": [
                    {"queryId": 1, "label": "A to B", "expectedRelevantIds": [2]}
                  ]
                }
                """);
        ExpectedSimilarityPairs pairs = loader.loadExpectedPairs(expected);

        expectedPairsValidator.validate(pairs, Set.of(1L, 2L));

        assertThatThrownBy(() -> expectedPairsValidator.validate(pairs, Set.of(2L)))
                .isInstanceOf(ExperimentException.class)
                .hasMessageContaining("queryId");
        assertThatThrownBy(() -> expectedPairsValidator.validate(
                new ExpectedSimilarityPairs(List.of(new com.example.ccas.retrieval.experiment.dto.ExpectedSimilarityQuery(
                        1L, "self", List.of(1L)
                ))),
                Set.of(1L)))
                .isInstanceOf(ExperimentException.class)
                .hasMessageContaining("query id");
        assertThatThrownBy(() -> expectedPairsValidator.validate(
                new ExpectedSimilarityPairs(List.of(new com.example.ccas.retrieval.experiment.dto.ExpectedSimilarityQuery(
                        1L, "missing", List.of(99L)
                ))),
                Set.of(1L)))
                .isInstanceOf(ExperimentException.class)
                .hasMessageContaining("relevant id");
    }

    private List<String> reasons(List<ExcludedExperimentRecord> records, long id) {
        return records.stream()
                .filter(record -> record.id() == id)
                .findFirst()
                .orElseThrow()
                .reasons();
    }

    private ExperimentComplaintRecord validRecord(long id) {
        return new ExperimentComplaintRecord(
                id,
                "민원 " + id,
                true,
                new com.example.ccas.retrieval.experiment.dto.ExperimentStructuredComplaint(
                        "민원 요약 " + id,
                        new com.example.ccas.retrieval.experiment.dto.ExperimentComplaintContext("ROAD", "ONGOING"),
                        List.of(new com.example.ccas.retrieval.experiment.dto.ExperimentObservedFact("핵심 사실 " + id))
                )
        );
    }
}
