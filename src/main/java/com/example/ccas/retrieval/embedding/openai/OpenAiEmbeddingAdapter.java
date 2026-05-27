package com.example.ccas.retrieval.embedding.openai;

import com.example.ccas.retrieval.config.EmbeddingProperties;
import com.example.ccas.retrieval.config.OpenAiProperties;
import com.example.ccas.retrieval.embedding.EmbeddingConfigurationException;
import com.example.ccas.retrieval.embedding.EmbeddingPort;
import com.example.ccas.retrieval.embedding.EmbeddingRequestException;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAiEmbeddingAdapter implements EmbeddingPort {

    private static final String ENCODING_FORMAT = "float";

    private final RestClient restClient;
    private final EmbeddingProperties embeddingProperties;
    private final OpenAiProperties openAiProperties;

    public OpenAiEmbeddingAdapter(
            @Qualifier("openAiRestClient") RestClient restClient,
            EmbeddingProperties embeddingProperties,
            OpenAiProperties openAiProperties
    ) {
        this.restClient = restClient;
        this.embeddingProperties = embeddingProperties;
        this.openAiProperties = openAiProperties;
    }

    @Override
    public EmbeddingResult embed(String searchText) {
        validateInput(searchText);
        validateConfiguration();

        OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest(
                embeddingProperties.model(),
                searchText,
                embeddingProperties.dimensions(),
                ENCODING_FORMAT
        );

        OpenAiEmbeddingResponse response = postEmbeddingRequest(request);
        float[] vector = extractVector(response);

        return new EmbeddingResult(
                vector,
                embeddingProperties.model(),
                embeddingProperties.dimensions(),
                embeddingProperties.version()
        );
    }

    private void validateInput(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            throw new EmbeddingRequestException("임베딩 대상 search_text는 비어 있을 수 없습니다.");
        }
    }

    private void validateConfiguration() {
        if (openAiProperties.apiKey() == null || openAiProperties.apiKey().isBlank()) {
            throw new EmbeddingConfigurationException("OpenAI API key가 설정되어 있지 않습니다.");
        }
        if (embeddingProperties.model() == null || embeddingProperties.model().isBlank()) {
            throw new EmbeddingConfigurationException("embedding model은 비어 있을 수 없습니다.");
        }
        if (embeddingProperties.dimensions() != 1536) {
            throw new EmbeddingConfigurationException("embedding dimensions는 1536이어야 합니다.");
        }
        if (embeddingProperties.version() == null || embeddingProperties.version().isBlank()) {
            throw new EmbeddingConfigurationException("embedding version은 비어 있을 수 없습니다.");
        }
    }

    private OpenAiEmbeddingResponse postEmbeddingRequest(OpenAiEmbeddingRequest request) {
        try {
            return restClient.post()
                    .uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class);
        } catch (RestClientResponseException exception) {
            throw new EmbeddingRequestException("OpenAI embedding provider 요청이 실패했습니다.", exception);
        } catch (RestClientException exception) {
            throw new EmbeddingRequestException("OpenAI embedding provider 호출 중 오류가 발생했습니다.", exception);
        }
    }

    private float[] extractVector(OpenAiEmbeddingResponse response) {
        if (response == null) {
            throw new EmbeddingRequestException("OpenAI embedding provider 응답 본문이 비어 있습니다.");
        }
        if (response.data() == null || response.data().isEmpty()) {
            throw new EmbeddingRequestException("OpenAI embedding provider 응답 data가 비어 있습니다.");
        }

        float[] embedding = response.data().getFirst().embedding();
        if (embedding == null) {
            throw new EmbeddingRequestException("OpenAI embedding provider 응답 embedding이 비어 있습니다.");
        }
        if (embedding.length != embeddingProperties.dimensions()) {
            throw new EmbeddingRequestException("OpenAI embedding provider 응답 vector 차원이 설정과 다릅니다.");
        }

        return embedding;
    }
}
