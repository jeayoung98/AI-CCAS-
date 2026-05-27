package com.example.ccas.retrieval.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationDatasetLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final EvaluationDatasetLoader loader = new EvaluationDatasetLoader(objectMapper, validator);

    @TempDir
    Path tempDir;

    @Test
    void loadsValidSyntheticDataset() {
        EvaluationDataset dataset = loader.loadSynthetic(Path.of(
                "src/test/resources/retrieval/evaluation/synthetic/synthetic-retrieval-evaluation-v1.json"
        ));

        assertThat(dataset.datasetId()).isEqualTo("synthetic-retrieval-evaluation-v1");
        assertThat(dataset.synthetic()).isTrue();
        assertThat(dataset.queries()).hasSize(8);
        assertThat(dataset.knowledgeItems()).isNotEmpty();
    }

    @Test
    void rejectsKnowledgeItemWithMismatchedPayload() throws Exception {
        Path datasetPath = write("""
                {
                  "datasetId": "invalid-payload",
                  "description": "invalid synthetic dataset",
                  "synthetic": true,
                  "structureVersion": "complaint-structure-v1",
                  "embeddingVersion": "embed-v1-large-1536",
                  "thresholdProfileVersion": "provisional-v1",
                  "knowledgeItems": [
                    {
                      "itemKey": "bad-item",
                      "itemType": "CATEGORY_REFERENCE",
                      "sourceType": "CATEGORY_POLICY",
                      "sourceTitle": "synthetic",
                      "title": "bad item",
                      "reviewStatus": "VERIFIED"
                    }
                  ],
                  "queries": [
                    {
                      "queryKey": "q1",
                      "description": "query",
                      "inputSource": "TEXT",
                      "maskedText": "masked",
                      "structuredComplaint": {
                        "factualSummary": "summary",
                        "context": { "placeType": "UNKNOWN", "relationshipType": "UNKNOWN", "timePattern": "UNKNOWN" },
                        "observedFacts": [{ "factType": "ACTION", "value": "value", "evidence": "evidence", "certainty": "EXPLICIT" }],
                        "riskSignals": [],
                        "subjectMatters": [{ "code": "GENERAL_CONSULTATION", "evidence": "evidence", "certainty": "EXPLICIT" }],
                        "requestedActions": [{ "code": "CONSULT", "evidence": "evidence", "certainty": "EXPLICIT" }],
                        "ongoingStatus": "UNKNOWN",
                        "missingInformation": []
                      },
                      "expectedRelevantVerifiedCaseKeys": [],
                      "expectedRelevantCategoryReferenceKeys": [],
                      "expectedRelevantOfficialGuideKeys": [],
                      "expectedEvidenceStatus": "INSUFFICIENT_EVIDENCE",
                      "expectedRiskSignalPresent": false,
                      "noReliableSimilarCaseExpected": true,
                      "ambiguityExpected": false
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> loader.loadSynthetic(datasetPath))
                .isInstanceOf(EvaluationDatasetException.class)
                .hasMessageContaining("itemType must match");
    }

    @Test
    void rejectsQueryWithMissingRequiredValue() throws Exception {
        Path datasetPath = write("""
                {
                  "datasetId": "invalid-query",
                  "description": "invalid synthetic dataset",
                  "synthetic": true,
                  "structureVersion": "complaint-structure-v1",
                  "embeddingVersion": "embed-v1-large-1536",
                  "thresholdProfileVersion": "provisional-v1",
                  "knowledgeItems": [],
                  "queries": [
                    {
                      "queryKey": " ",
                      "description": "query",
                      "inputSource": "TEXT",
                      "maskedText": "masked",
                      "structuredComplaint": null,
                      "expectedRelevantVerifiedCaseKeys": [],
                      "expectedRelevantCategoryReferenceKeys": [],
                      "expectedRelevantOfficialGuideKeys": [],
                      "expectedEvidenceStatus": "INSUFFICIENT_EVIDENCE",
                      "expectedRiskSignalPresent": false,
                      "noReliableSimilarCaseExpected": false,
                      "ambiguityExpected": false
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> loader.loadSynthetic(datasetPath))
                .isInstanceOf(EvaluationDatasetException.class)
                .hasMessageContaining("queries[0]");
    }

    @Test
    void rejectsNonSyntheticDatasetFromSyntheticLoader() throws Exception {
        Path datasetPath = write("""
                {
                  "datasetId": "human-reviewed-placeholder",
                  "description": "human reviewed placeholder",
                  "synthetic": false,
                  "structureVersion": "complaint-structure-v1",
                  "embeddingVersion": "embed-v1-large-1536",
                  "thresholdProfileVersion": "provisional-v1",
                  "knowledgeItems": [],
                  "queries": [
                    {
                      "queryKey": "q1",
                      "description": "query",
                      "inputSource": "TEXT",
                      "maskedText": "masked",
                      "structuredComplaint": {
                        "factualSummary": "summary",
                        "context": { "placeType": "UNKNOWN", "relationshipType": "UNKNOWN", "timePattern": "UNKNOWN" },
                        "observedFacts": [{ "factType": "ACTION", "value": "value", "evidence": "evidence", "certainty": "EXPLICIT" }],
                        "riskSignals": [],
                        "subjectMatters": [{ "code": "GENERAL_CONSULTATION", "evidence": "evidence", "certainty": "EXPLICIT" }],
                        "requestedActions": [{ "code": "CONSULT", "evidence": "evidence", "certainty": "EXPLICIT" }],
                        "ongoingStatus": "UNKNOWN",
                        "missingInformation": []
                      },
                      "expectedRelevantVerifiedCaseKeys": [],
                      "expectedRelevantCategoryReferenceKeys": [],
                      "expectedRelevantOfficialGuideKeys": [],
                      "expectedEvidenceStatus": "INSUFFICIENT_EVIDENCE",
                      "expectedRiskSignalPresent": false,
                      "noReliableSimilarCaseExpected": false,
                      "ambiguityExpected": false
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> loader.loadSynthetic(datasetPath))
                .isInstanceOf(EvaluationDatasetException.class)
                .hasMessageContaining("synthetic=true");
    }

    private Path write(String content) throws Exception {
        Path path = tempDir.resolve("dataset.json");
        Files.writeString(path, content);
        return path;
    }
}
