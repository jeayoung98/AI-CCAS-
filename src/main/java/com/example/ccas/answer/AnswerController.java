package com.example.ccas.answer;

import com.example.ccas.retrieval.api.dto.RetrievalRequest;
import com.example.ccas.retrieval.api.mapper.RetrievalApiMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnswerController {
    private final ComplaintAnswerService answers;
    private final RetrievalApiMapper mapper;

    public AnswerController(ComplaintAnswerService answers, RetrievalApiMapper mapper) {
        this.answers = answers;
        this.mapper = mapper;
    }

    @PostMapping("/api/rag/answer")
    public AnswerResponse answer(@Valid @RequestBody RetrievalRequest request) {
        return answers.answer(mapper.toCommand(request));
    }
}
