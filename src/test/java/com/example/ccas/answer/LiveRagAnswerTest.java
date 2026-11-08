package com.example.ccas.answer;

import com.example.ccas.retrieval.application.ingestion.CreateKnowledgeSourceCommand;
import com.example.ccas.retrieval.application.ingestion.IngestOfficialGuideCommand;
import com.example.ccas.retrieval.application.ingestion.KnowledgeSourceIngestionService;
import com.example.ccas.retrieval.application.ingestion.RetrievalItemIngestionService;
import com.example.ccas.retrieval.domain.OfficialGuideChunk;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "retrieval.evaluation.guide-min-similarity=0.45",
        "retrieval.evaluation.threshold-profile-version=synthetic-live-test"
})
class LiveRagAnswerTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort int port;
    @Autowired KnowledgeSourceIngestionService sources;
    @Autowired RetrievalItemIngestionService items;
    @Autowired ObjectMapper json;

    @Test
    void answersWithVerifiableQuotesAfterGuideIsIngested() throws Exception {
        var request = Files.readAllBytes(Path.of("docs/examples/rag-answer-request.json"));
        var empty = post(request);
        assertThat(empty.evidenceStatus()).isEqualTo(EvidenceStatus.INSUFFICIENT_EVIDENCE);
        assertThat(empty.answerStatus()).isEqualTo(AnswerResponse.Status.ABSTAINED);

        String title = "합성 테스트 문서: 도서관 분실물 조회";
        String chunk = "도서관에서 잃어버린 지갑을 조회하려면 분실물 조회 화면에서 물품 종류를 지갑으로 선택하고 분실 장소를 입력합니다.";
        long source = sources.createSource(new CreateKnowledgeSourceCommand(KnowledgeSourceType.OFFICIAL_DOCUMENT,
                title, null, null, null, null));
        var guide = new OfficialGuideChunk(title, "지갑 분실물 조회 방법", chunk,
                List.of(SubjectMatterCode.LOST_ITEM), List.of(RequestedActionCode.SEARCH), List.of());
        long item = items.ingestOfficialGuide(new IngestOfficialGuideCommand(source, "synthetic-wallet", title,
                guide, ReviewStatus.VERIFIED));

        var response = post(request);
        assertThat(response.evidenceStatus()).isEqualTo(EvidenceStatus.GUIDE_ONLY);
        assertThat(response.answerStatus()).isEqualTo(AnswerResponse.Status.GENERATED);
        assertThat(response.answer()).contains("[1]");
        assertThat(response.citations()).isNotEmpty().allSatisfy(citation -> {
            assertThat(citation.itemId()).isEqualTo(item);
            assertThat(citation.quote()).isNotBlank();
            assertThat(chunk).contains(citation.quote());
        });
        System.out.println(response.answer());
        response.citations().forEach(citation -> System.out.println(citation.documentTitle() + " | " + citation.quote()));
    }

    private AnswerResponse post(byte[] request) throws Exception {
        String result = RestClient.create().post().uri("http://127.0.0.1:" + port + "/api/rag/answer")
                .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(String.class);
        return json.readValue(result, AnswerResponse.class);
    }
}
