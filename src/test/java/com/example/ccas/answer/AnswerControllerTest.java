package com.example.ccas.answer;

import com.example.ccas.retrieval.api.error.RetrievalApiExceptionHandler;
import com.example.ccas.retrieval.api.mapper.RetrievalApiMapper;
import com.example.ccas.retrieval.application.evaluation.RetrievalEvidenceDecision;
import com.example.ccas.retrieval.application.evaluation.RetrievalHitAssessment;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionResult;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionService;
import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.search.RetrievalSearchHit;
import com.example.ccas.retrieval.application.text.SearchTextBuilder;
import com.example.ccas.retrieval.config.OpenAiProperties;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import com.example.ccas.retrieval.domain.type.HitRejectionReason;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnswerControllerTest {
    private static final String URL = "https://example.test/v1";
    private static final String CHUNK = "조회할 물품의 종류를 입력하세요.";
    private static final RetrievalItemType GUIDE = RetrievalItemType.OFFICIAL_GUIDE;
    private final ObjectMapper json = new ObjectMapper();
    private final RetrievalQueryExecutionService retrieval = mock(RetrievalQueryExecutionService.class);
    private MockRestServiceServer provider;
    private MockMvc mvc;
    private String request;

    @BeforeEach
    void setup() throws Exception {
        request = Files.readString(Path.of("docs/examples/rag-answer-request.json"));
        var builder = RestClient.builder().baseUrl(URL);
        provider = MockRestServiceServer.bindTo(builder).build();
        var chat = new ChatAnswerClient(builder.build(), new OpenAiProperties(URL, "test-key"), json, "test-model");
        var service = new ComplaintAnswerService(retrieval, new SearchTextBuilder(), chat);
        mvc = MockMvcBuilders.standaloneSetup(new AnswerController(service, new RetrievalApiMapper()))
                .setControllerAdvice(new RetrievalApiExceptionHandler()).build();
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = {"GUIDE_ONLY", "REFERENCE_SUPPORTED", "CASE_AND_REFERENCE_SUPPORTED"})
    void generatesCitedAnswerFromAcceptedGuides(EvidenceStatus status) throws Exception {
        givenRetrieval(status);
        var reply = json.writeValueAsString(Map.of("statements", List.of(Map.of("text", "물품 종류를 입력하세요.",
                "itemId", 5, "quote", CHUNK))));
        provider.expect(requestTo(URL + "/chat/completions"))
                .andExpect(http -> {
                    var payload = json.readTree(((MockClientHttpRequest) http).getBodyAsBytes());
                    assertThat(payload.path("model").asText()).isEqualTo("test-model");
                    var context = json.readTree(payload.path("messages").get(1).path("content").asText());
                    assertThat(context.path("complaint").asText()).contains("지갑").doesNotContain("도서관에서 지갑을 잃어버렸어요.");
                    assertThat(context.path("guides")).hasSize(1);
                    assertThat(context.path("guides").get(0).path("chunkText").asText()).isEqualTo(CHUNK);
                })
                .andRespond(withSuccess(envelope(reply), MediaType.APPLICATION_JSON));

        submit(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.answerStatus").value("GENERATED"))
                .andExpect(jsonPath("$.evidenceStatus").value(status.name()))
                .andExpect(jsonPath("$.riskSignalPresent").value(true))
                .andExpect(jsonPath("$.answer").value("물품 종류를 입력하세요. [1]"))
                .andExpect(jsonPath("$.citations[0].itemId").value(5))
                .andExpect(jsonPath("$.citations[0].quote").value(CHUNK))
                .andExpect(jsonPath("$.maskedText").doesNotExist());
        provider.verify();
    }

    @ParameterizedTest
    @EnumSource(value = EvidenceStatus.class, names = {"AMBIGUOUS", "INSUFFICIENT_EVIDENCE"})
    void abstainsWithoutCallingChat(EvidenceStatus status) throws Exception {
        givenRetrieval(status);
        submit(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.answerStatus").value("ABSTAINED"))
                .andExpect(jsonPath("$.evidenceStatus").value(status.name()))
                .andExpect(jsonPath("$.riskSignalPresent").value(true))
                .andExpect(jsonPath("$.citations").isEmpty());
    }

    @Test
    void allowsModelToAbstain() throws Exception {
        givenRetrieval(EvidenceStatus.GUIDE_ONLY);
        provider.expect(requestTo(URL + "/chat/completions"))
                .andRespond(withSuccess(envelope("{\"statements\":[]}"), MediaType.APPLICATION_JSON));
        submit(request).andExpect(status().isOk()).andExpect(jsonPath("$.answerStatus").value("ABSTAINED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad-json", "{}", "{\"statements\":[{\"text\":\"bad\",\"itemId\":5,\"quote\":\"invented\"}]}",
            "{\"statements\":[{\"text\":\"bad\",\"itemId\":6,\"quote\":\"rejected\"}]}"})
    void rejectsUnverifiableProviderReply(String content) throws Exception {
        givenRetrieval(EvidenceStatus.GUIDE_ONLY);
        provider.expect(requestTo(URL + "/chat/completions"))
                .andRespond(withSuccess(envelope(content), MediaType.APPLICATION_JSON));
        submit(request).andExpect(status().isBadGateway()).andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void hidesProviderFailureBody() throws Exception {
        givenRetrieval(EvidenceStatus.GUIDE_ONLY);
        provider.expect(requestTo(URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("private-provider-detail"));
        var response = submit(request).andExpect(status().isBadGateway()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private-provider-detail");
    }

    @Test
    void rejectsUnmaskedInputBeforeRetrieval() throws Exception {
        var body = json.readValue(request, ObjectNode.class);
        body.put("maskingCompleted", false);
        submit(body.toString()).andExpect(status().isBadRequest());
        verifyNoInteractions(retrieval);
    }

    private ResultActions submit(String body) throws Exception {
        return mvc.perform(post("/api/rag/answer").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String envelope(String content) throws Exception {
        return json.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop",
                "message", Map.of("content", content)))));
    }

    private void givenRetrieval(EvidenceStatus status) {
        var assessments = List.of(new RetrievalHitAssessment(GUIDE, 5, 1, true, null),
                new RetrievalHitAssessment(GUIDE, 6, 2, false, HitRejectionReason.BELOW_GUIDE_MIN_SIMILARITY));
        var decision = new RetrievalEvidenceDecision(status, true, null, null, 0.9, null, null,
                List.of(), assessments, "test", true);
        var candidates = new RetrievalCandidates(List.of(), List.of(), List.of(guide(5, CHUNK, 1), guide(6, "rejected", 2)));
        when(retrieval.execute(any())).thenReturn(new RetrievalQueryExecutionResult(UUID.randomUUID(), true, candidates, decision));
    }

    private RetrievalSearchHit guide(long id, String text, int rank) {
        var payload = json.createObjectNode().put("documentTitle", "테스트 안내").put("sectionTitle", "조회").put("chunkText", text);
        return new RetrievalSearchHit(id, GUIDE, "테스트", payload, List.of(), List.of(), List.of(), null, null, 0.9, rank);
    }
}
