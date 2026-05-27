package com.example.ccas.retrieval.api;

import com.example.ccas.retrieval.api.error.RetrievalApiExceptionHandler;
import com.example.ccas.retrieval.api.mapper.RetrievalApiMapper;
import com.example.ccas.retrieval.application.evaluation.RetrievalEvidenceDecision;
import com.example.ccas.retrieval.application.evaluation.RetrievalHitAssessment;
import com.example.ccas.retrieval.application.query.ExecuteRetrievalQueryCommand;
import com.example.ccas.retrieval.application.query.InvalidRetrievalQueryException;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionResult;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionService;
import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.search.RetrievalSearchHit;
import com.example.ccas.retrieval.domain.type.DecisionReasonCode;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import com.example.ccas.retrieval.domain.type.HitRejectionReason;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.embedding.EmbeddingRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RetrievalControllerTest {

    private static final UUID QUERY_ID = UUID.fromString("55e2f70d-9b59-4fd6-8949-bc955286c6ea");

    private MockMvc mockMvc;

    private RetrievalQueryExecutionService executionService;

    @BeforeEach
    void setUp() {
        executionService = mock(RetrievalQueryExecutionService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new RetrievalController(
                        executionService,
                        new RetrievalApiMapper()
                ))
                .setControllerAdvice(new RetrievalApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsReferenceSupportedResultForLostItemRequest() throws Exception {
        when(executionService.execute(any())).thenReturn(referenceSupportedResult(false));

        String body = lostItemRequestJson(true, "공공장소 인근에서 지갑을 잃어버렸는데 찾는 방법을 알고 싶습니다.");

        String response = mockMvc.perform(post("/api/vector/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.queryId").value(QUERY_ID.toString()))
                .andExpect(jsonPath("$.riskSignalPresent").value(false))
                .andExpect(jsonPath("$.decision.evidenceStatus").value("REFERENCE_SUPPORTED"))
                .andExpect(jsonPath("$.decision.provisionalThreshold").value(true))
                .andExpect(jsonPath("$.decision.thresholdProfileVersion").value("provisional-v1"))
                .andExpect(jsonPath("$.channels.categoryReferences[0].itemType").value("CATEGORY_REFERENCE"))
                .andExpect(jsonPath("$.channels.categoryReferences[0].passedChannelCutoff").value(true))
                .andExpect(jsonPath("$.channels.officialGuides[0].itemType").value("OFFICIAL_GUIDE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertForbiddenResponseFieldsAreAbsent(response);
        ArgumentCaptor<ExecuteRetrievalQueryCommand> captor = ArgumentCaptor.forClass(ExecuteRetrievalQueryCommand.class);
        verify(executionService).execute(captor.capture());
        assertThat(captor.getValue().structuredComplaint().subjectMatters()).hasSize(1);
    }

    @Test
    void returnsRiskSignalReasonWithoutFinalEmergencyRoute() throws Exception {
        when(executionService.execute(any())).thenReturn(referenceSupportedResult(true));

        String response = mockMvc.perform(post("/api/vector/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(riskSignalRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskSignalPresent").value(true))
                .andExpect(jsonPath("$.decision.reasonCodes[?(@ == 'RISK_SIGNAL_PRESENT')]").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("finalRoute", "recommendedRoute", "emergencyRoute", "112");
    }

    @Test
    void rejectsMaskingIncompleteBeforeServiceCall() throws Exception {
        String response = mockMvc.perform(post("/api/vector/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lostItemRequestJson(false, "민감한 masked text가 응답에 그대로 나오면 안 됩니다.")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid retrieval request."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        verifyNoInteractions(executionService);
        assertThat(response).doesNotContain("민감한 masked text");
    }

    @Test
    void mapsServiceValidationFailureToBadRequest() throws Exception {
        when(executionService.execute(any())).thenThrow(new InvalidRetrievalQueryException("maskedText must not be blank"));

        String response = mockMvc.perform(post("/api/vector/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lostItemRequestJson(true, "masked text")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid retrieval request."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("masked text");
    }

    @Test
    void rejectsJsonValidationFailures() throws Exception {
        assertBadRequest(lostItemRequestJson(true, " "));
        assertBadRequest("""
                {
                  "inputSource": "TEXT",
                  "maskingCompleted": true,
                  "maskedText": "masked",
                  "structuredComplaint": null
                }
                """);
        assertBadRequest(lostItemRequestJsonWith("\"factualSummary\": \" \""));
        assertBadRequest(lostItemRequestJsonWith("\"observedFacts\": []"));
        assertBadRequest(lostItemRequestJsonWith("\"subjectMatters\": []"));
        assertBadRequest(lostItemRequestJsonWith("\"requestedActions\": []"));

        verifyNoInteractions(executionService);
    }

    @Test
    void rejectsUnsupportedEnumValues() throws Exception {
        String response = mockMvc.perform(post("/api/vector/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputSource": "AUDIO_RAW",
                                  "maskingCompleted": true,
                                  "maskedText": "masked",
                                  "structuredComplaint": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid retrieval request."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        verifyNoInteractions(executionService);
        assertThat(response).doesNotContain("Exception", "stackTrace");
    }

    @Test
    void mapsServiceProcessingFailureToServerErrorWithoutSensitiveData() throws Exception {
        when(executionService.execute(any())).thenThrow(new IllegalStateException("database failure containing masked text"));

        String response = mockMvc.perform(post("/api/vector/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lostItemRequestJson(true, "masked text must not appear")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Retrieval processing failed."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("masked text", "database failure", "api-key", "embedding");
    }

    @Test
    void mapsEmbeddingProviderFailureToBadGateway() throws Exception {
        when(executionService.execute(any())).thenThrow(new EmbeddingRequestException("provider failed"));

        mockMvc.perform(post("/api/vector/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lostItemRequestJson(true, "masked text")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Retrieval processing failed."));
    }

    @Test
    void mapsHitAssessmentsIntoResponseHits() throws Exception {
        RetrievalSearchHit passed = hit(1, RetrievalItemType.CATEGORY_REFERENCE, "category", 0.89, 1);
        RetrievalSearchHit rejected = hit(2, RetrievalItemType.VERIFIED_CASE, "case", 0.63, 1);
        RetrievalCandidates candidates = new RetrievalCandidates(List.of(rejected), List.of(passed), List.of());
        RetrievalEvidenceDecision decision = new RetrievalEvidenceDecision(
                EvidenceStatus.REFERENCE_SUPPORTED,
                false,
                0.63,
                0.89,
                null,
                null,
                null,
                List.of(DecisionReasonCode.NO_RELIABLE_VERIFIED_CASE),
                List.of(
                        new RetrievalHitAssessment(RetrievalItemType.CATEGORY_REFERENCE, 1, 1, true, null),
                        new RetrievalHitAssessment(RetrievalItemType.VERIFIED_CASE, 2, 1, false, HitRejectionReason.BELOW_CASE_MIN_SIMILARITY)
                ),
                "provisional-v1",
                true
        );
        when(executionService.execute(any())).thenReturn(new RetrievalQueryExecutionResult(QUERY_ID, false, candidates, decision));

        mockMvc.perform(post("/api/vector/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lostItemRequestJson(true, "masked text")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.categoryReferences[0].passedChannelCutoff").value(true))
                .andExpect(jsonPath("$.channels.categoryReferences[0].rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.channels.verifiedCases[0].passedChannelCutoff").value(false))
                .andExpect(jsonPath("$.channels.verifiedCases[0].rejectionReason").value("BELOW_CASE_MIN_SIMILARITY"));
    }

    private void assertBadRequest(String body) throws Exception {
        mockMvc.perform(post("/api/vector/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid retrieval request."));
    }

    private RetrievalQueryExecutionResult referenceSupportedResult(boolean riskSignalPresent) {
        RetrievalSearchHit category = hit(1, RetrievalItemType.CATEGORY_REFERENCE, "category", 0.89, 1);
        RetrievalSearchHit guide = hit(2, RetrievalItemType.OFFICIAL_GUIDE, "guide", 0.83, 1);
        RetrievalCandidates candidates = new RetrievalCandidates(List.of(), List.of(category), List.of(guide));
        RetrievalEvidenceDecision decision = new RetrievalEvidenceDecision(
                EvidenceStatus.REFERENCE_SUPPORTED,
                riskSignalPresent,
                null,
                0.89,
                0.83,
                null,
                null,
                riskSignalPresent
                        ? List.of(
                        DecisionReasonCode.NO_RELIABLE_VERIFIED_CASE,
                        DecisionReasonCode.RELIABLE_CATEGORY_REFERENCE_FOUND,
                        DecisionReasonCode.RELIABLE_OFFICIAL_GUIDE_FOUND,
                        DecisionReasonCode.RISK_SIGNAL_PRESENT
                )
                        : List.of(
                        DecisionReasonCode.NO_RELIABLE_VERIFIED_CASE,
                        DecisionReasonCode.RELIABLE_CATEGORY_REFERENCE_FOUND,
                        DecisionReasonCode.RELIABLE_OFFICIAL_GUIDE_FOUND
                ),
                List.of(
                        new RetrievalHitAssessment(RetrievalItemType.CATEGORY_REFERENCE, 1, 1, true, null),
                        new RetrievalHitAssessment(RetrievalItemType.OFFICIAL_GUIDE, 2, 1, true, null)
                ),
                "provisional-v1",
                true
        );
        return new RetrievalQueryExecutionResult(QUERY_ID, riskSignalPresent, candidates, decision);
    }

    private RetrievalSearchHit hit(long itemId, RetrievalItemType itemType, String title, double score, int rank) {
        JsonNode payload = JsonNodeFactory.instance.objectNode().put("title", title);
        return new RetrievalSearchHit(
                itemId,
                itemType,
                title,
                payload,
                List.of("LOST_ITEM"),
                List.of("SEARCH"),
                List.of(),
                itemType == RetrievalItemType.OFFICIAL_GUIDE ? null : "LOST_ITEM",
                null,
                score,
                rank
        );
    }

    private String lostItemRequestJson(boolean maskingCompleted, String maskedText) {
        return """
                {
                  "inputSource": "TEXT",
                  "maskingCompleted": %s,
                  "maskedText": "%s",
                  "structuredComplaint": {
                    "factualSummary": "공공장소 인근에서 지갑을 분실하여 찾는 방법을 문의하는 상황",
                    "context": {
                      "placeType": "PUBLIC_FACILITY",
                      "relationshipType": "UNKNOWN",
                      "timePattern": "ONE_TIME"
                    },
                    "observedFacts": [
                      {
                        "factType": "ACTION",
                        "value": "지갑 분실",
                        "evidence": "지갑을 잃어버렸는데",
                        "certainty": "EXPLICIT"
                      }
                    ],
                    "riskSignals": [],
                    "subjectMatters": [
                      {
                        "code": "LOST_ITEM",
                        "evidence": "지갑을 잃어버렸는데",
                        "certainty": "EXPLICIT"
                      }
                    ],
                    "requestedActions": [
                      {
                        "code": "SEARCH",
                        "evidence": "찾는 방법을 알고 싶습니다",
                        "certainty": "EXPLICIT"
                      }
                    ],
                    "ongoingStatus": "PAST_EVENT",
                    "missingInformation": []
                  }
                }
                """.formatted(maskingCompleted, maskedText);
    }

    private String riskSignalRequestJson() {
        return """
                {
                  "inputSource": "STT",
                  "maskingCompleted": true,
                  "maskedText": "이웃 주민이 반복적으로 찾아와 죽이겠다고 말했습니다.",
                  "structuredComplaint": {
                    "factualSummary": "이웃 주민이 반복적으로 방문하고 살해 협박성 발언을 한 상황",
                    "context": {
                      "placeType": "RESIDENCE",
                      "relationshipType": "NEIGHBOR",
                      "timePattern": "REPEATED"
                    },
                    "observedFacts": [
                      {
                        "factType": "ACTION",
                        "value": "반복 방문",
                        "evidence": "반복적으로 찾아와",
                        "certainty": "EXPLICIT"
                      }
                    ],
                    "riskSignals": [
                      {
                        "code": "DEATH_THREAT",
                        "evidence": "죽이겠다고 말했습니다",
                        "certainty": "EXPLICIT"
                      }
                    ],
                    "subjectMatters": [
                      {
                        "code": "SAFETY_THREAT",
                        "evidence": "죽이겠다고 말했습니다",
                        "certainty": "EXPLICIT"
                      }
                    ],
                    "requestedActions": [
                      {
                        "code": "REPORT",
                        "evidence": "신고 문의",
                        "certainty": "EXPLICIT"
                      }
                    ],
                    "ongoingStatus": "REPEATED_AND_MAY_CONTINUE",
                    "missingInformation": ["현재 상대방이 현장에 있는지 여부"]
                  }
                }
                """;
    }

    private String lostItemRequestJsonWith(String replacement) {
        String base = lostItemRequestJson(true, "masked");
        if (replacement.startsWith("\"factualSummary\"")) {
            return base.replace("\"factualSummary\": \"공공장소 인근에서 지갑을 분실하여 찾는 방법을 문의하는 상황\"", replacement);
        }
        if (replacement.startsWith("\"observedFacts\"")) {
            return base.replaceFirst("\"observedFacts\"\\s*:\\s*\\[[\\s\\S]*?]\\s*,\\s*\"riskSignals\"", replacement + ",\n                    \"riskSignals\"");
        }
        if (replacement.startsWith("\"subjectMatters\"")) {
            return base.replaceFirst("\"subjectMatters\"\\s*:\\s*\\[[\\s\\S]*?]\\s*,\\s*\"requestedActions\"", replacement + ",\n                    \"requestedActions\"");
        }
        if (replacement.startsWith("\"requestedActions\"")) {
            return base.replaceFirst("\"requestedActions\"\\s*:\\s*\\[[\\s\\S]*?]\\s*,\\s*\"ongoingStatus\"", replacement + ",\n                    \"ongoingStatus\"");
        }
        throw new IllegalArgumentException("Unsupported replacement: " + replacement);
    }

    private void assertForbiddenResponseFieldsAreAbsent(String response) {
        assertThat(response).doesNotContain(
                "embedding",
                "vector",
                "searchText",
                "search_text",
                "maskedText",
                "masked_text",
                "finalCategory",
                "finalRoute",
                "recommendedRoute",
                "crimeDetermination"
        );
    }

}
