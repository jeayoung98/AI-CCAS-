package com.example.ccas.answer;

import com.example.ccas.retrieval.application.query.ExecuteRetrievalQueryCommand;
import com.example.ccas.retrieval.application.evaluation.RetrievalHitAssessment;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionService;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionResult;
import com.example.ccas.retrieval.application.text.SearchTextBuilder;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ComplaintAnswerService {
    private final RetrievalQueryExecutionService retrieval;
    private final SearchTextBuilder searchText;
    private final ChatAnswerClient chat;

    public ComplaintAnswerService(RetrievalQueryExecutionService retrieval, SearchTextBuilder searchText, ChatAnswerClient chat) {
        this.retrieval = retrieval;
        this.searchText = searchText;
        this.chat = chat;
    }

    public AnswerResponse answer(ExecuteRetrievalQueryCommand command) {
        var result = retrieval.execute(command);
        var status = result.decision().evidenceStatus();
        if (status == EvidenceStatus.AMBIGUOUS) {
            return abstain(result, "검색된 자료가 서로 다른 상황을 가리켜 안내하기 어렵습니다. 어떤 상황에서 무엇을 확인하고 싶으신지 조금 더 알려주세요.");
        }
        if (status == EvidenceStatus.INSUFFICIENT_EVIDENCE) {
            return insufficient(result);
        }

        var guides = acceptedGuides(result);
        if (guides.isEmpty()) {
            return insufficient(result);
        }
        var statements = chat.generate(searchText.buildForComplaint(command.structuredComplaint()), guides.values());
        if (statements.isEmpty()) {
            return insufficient(result);
        }

        List<String> lines = new ArrayList<>();
        List<AnswerResponse.Citation> citations = new ArrayList<>();
        for (var statement : statements) {
            if (statement == null || statement.text() == null || statement.text().isBlank()
                    || statement.quote() == null || statement.quote().isBlank()) {
                throw new AnswerGenerationException();
            }
            Guide guide = guides.get(statement.itemId());
            if (guide == null || !guide.chunkText().contains(statement.quote())) {
                throw new AnswerGenerationException();
            }
            citations.add(new AnswerResponse.Citation(guide.itemId(), guide.documentTitle(), guide.sectionTitle(), statement.quote()));
            lines.add(statement.text().strip() + " [" + citations.size() + "]");
        }
        return new AnswerResponse(result.queryId(), status, result.riskSignalPresent(),
                AnswerResponse.Status.GENERATED, String.join("\n", lines), List.copyOf(citations));
    }

    private Map<Long, Guide> acceptedGuides(RetrievalQueryExecutionResult result) {
        Set<Long> accepted = result.decision().hitAssessments().stream()
                .filter(hit -> hit.channel() == RetrievalItemType.OFFICIAL_GUIDE && hit.passedChannelCutoff())
                .map(RetrievalHitAssessment::itemId)
                .collect(Collectors.toSet());
        Map<Long, Guide> guides = new LinkedHashMap<>();
        for (var hit : result.candidates().officialGuides()) {
            if (!accepted.contains(hit.itemId())) {
                continue;
            }
            var payload = hit.structuredPayload();
            if (payload == null || !payload.path("chunkText").isTextual()) {
                continue;
            }
            String chunk = payload.path("chunkText").textValue();
            if (!chunk.isBlank()) {
                guides.put(hit.itemId(), new Guide(hit.itemId(), payload.path("documentTitle").asText(hit.title()),
                        payload.path("sectionTitle").asText(""), chunk));
            }
        }
        return guides;
    }

    private AnswerResponse insufficient(RetrievalQueryExecutionResult result) {
        return abstain(result, "현재 확인한 공식 자료만으로는 이 질문에 답하기 어렵습니다. 안내받고 싶은 절차나 궁금한 점을 조금 더 알려주세요.");
    }

    private AnswerResponse abstain(RetrievalQueryExecutionResult result, String message) {
        return new AnswerResponse(result.queryId(), result.decision().evidenceStatus(), result.riskSignalPresent(),
                AnswerResponse.Status.ABSTAINED, message, List.of());
    }

    record Guide(long itemId, String documentTitle, String sectionTitle, String chunkText) {}
}
