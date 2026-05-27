package com.example.ccas.retrieval.embedding.openai;

import com.example.ccas.retrieval.config.EmbeddingProperties;
import com.example.ccas.retrieval.config.OpenAiProperties;
import com.example.ccas.retrieval.embedding.EmbeddingConfigurationException;
import com.example.ccas.retrieval.embedding.EmbeddingRequestException;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiEmbeddingAdapterTest {

    private static final EmbeddingProperties EMBEDDING_PROPERTIES = new EmbeddingProperties(
            "text-embedding-3-large",
            1536,
            "embed-v1-large-1536"
    );

    @Test
    void returnsEmbeddingResultFromSuccessfulOpenAiResponse() {
        TestAdapter testAdapter = testAdapter("test-api-key");
        String searchText = "발생 상황: 테스트\n민원 대상: 유실물";

        testAdapter.server().expect(requestTo("https://mock.openai.test/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
                .andExpect(content().json("""
                        {
                          "model": "text-embedding-3-large",
                          "input": "발생 상황: 테스트\\n민원 대상: 유실물",
                          "dimensions": 1536,
                          "encoding_format": "float"
                        }
                        """))
                .andRespond(withSuccess(embeddingResponse(1536), MediaType.APPLICATION_JSON));

        EmbeddingResult result = testAdapter.adapter().embed(searchText);

        assertThat(result.vector()).hasSize(1536);
        assertThat(result.model()).isEqualTo("text-embedding-3-large");
        assertThat(result.dimensions()).isEqualTo(1536);
        assertThat(result.version()).isEqualTo("embed-v1-large-1536");
        testAdapter.server().verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsBlankInputWithoutCallingProvider(String searchText) {
        TestAdapter testAdapter = testAdapter("test-api-key");

        assertThatThrownBy(() -> testAdapter.adapter().embed(searchText))
                .isInstanceOf(EmbeddingRequestException.class)
                .hasMessage("임베딩 대상 search_text는 비어 있을 수 없습니다.");

        testAdapter.server().verify();
    }

    @Test
    void rejectsMissingApiKeyWithoutCallingProvider() {
        TestAdapter testAdapter = testAdapter(" ");

        assertThatThrownBy(() -> testAdapter.adapter().embed("발생 상황: 테스트"))
                .isInstanceOf(EmbeddingConfigurationException.class)
                .hasMessage("OpenAI API key가 설정되어 있지 않습니다.");

        testAdapter.server().verify();
    }

    @Test
    void rejectsNullResponseBody() {
        TestAdapter testAdapter = testAdapter("test-api-key");
        testAdapter.server().expect(requestTo("https://mock.openai.test/v1/embeddings"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> testAdapter.adapter().embed("발생 상황: 테스트"))
                .isInstanceOf(EmbeddingRequestException.class)
                .hasMessage("OpenAI embedding provider 응답 본문이 비어 있습니다.");

        testAdapter.server().verify();
    }

    @Test
    void rejectsEmptyDataResponse() {
        TestAdapter testAdapter = testAdapter("test-api-key");
        testAdapter.server().expect(requestTo("https://mock.openai.test/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> testAdapter.adapter().embed("발생 상황: 테스트"))
                .isInstanceOf(EmbeddingRequestException.class)
                .hasMessage("OpenAI embedding provider 응답 data가 비어 있습니다.");

        testAdapter.server().verify();
    }

    @Test
    void rejectsUnexpectedVectorDimensions() {
        TestAdapter testAdapter = testAdapter("test-api-key");
        testAdapter.server().expect(requestTo("https://mock.openai.test/v1/embeddings"))
                .andRespond(withSuccess(embeddingResponse(1535), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> testAdapter.adapter().embed("발생 상황: 테스트"))
                .isInstanceOf(EmbeddingRequestException.class)
                .hasMessage("OpenAI embedding provider 응답 vector 차원이 설정과 다릅니다.");

        testAdapter.server().verify();
    }

    @Test
    void convertsBadRequestProviderError() {
        TestAdapter testAdapter = testAdapter("test-api-key");
        testAdapter.server().expect(requestTo("https://mock.openai.test/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> testAdapter.adapter().embed("발생 상황: 테스트"))
                .isInstanceOf(EmbeddingRequestException.class)
                .hasMessage("OpenAI embedding provider 요청이 실패했습니다.");

        testAdapter.server().verify();
    }

    @Test
    void convertsServerProviderError() {
        TestAdapter testAdapter = testAdapter("test-api-key");
        testAdapter.server().expect(requestTo("https://mock.openai.test/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> testAdapter.adapter().embed("발생 상황: 테스트"))
                .isInstanceOf(EmbeddingRequestException.class)
                .hasMessage("OpenAI embedding provider 요청이 실패했습니다.");

        testAdapter.server().verify();
    }

    private TestAdapter testAdapter(String apiKey) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://mock.openai.test/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiEmbeddingAdapter adapter = new OpenAiEmbeddingAdapter(
                builder.build(),
                EMBEDDING_PROPERTIES,
                new OpenAiProperties("https://mock.openai.test/v1", apiKey)
        );

        return new TestAdapter(adapter, server);
    }

    private static String embeddingResponse(int dimensions) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"data\":[{\"embedding\":[");
        for (int i = 0; i < dimensions; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(i == 0 ? "0.001" : "0.0");
        }
        builder.append("]}]}");
        return builder.toString();
    }

    private record TestAdapter(
            OpenAiEmbeddingAdapter adapter,
            MockRestServiceServer server
    ) {
    }
}
