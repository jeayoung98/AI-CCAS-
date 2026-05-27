package com.example.ccas.retrieval.api;

import com.example.ccas.retrieval.api.dto.RetrievalRequest;
import com.example.ccas.retrieval.api.dto.RetrievalResponse;
import com.example.ccas.retrieval.api.mapper.RetrievalApiMapper;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionResult;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetrievalController {

    private final RetrievalQueryExecutionService executionService;
    private final RetrievalApiMapper mapper;

    public RetrievalController(RetrievalQueryExecutionService executionService, RetrievalApiMapper mapper) {
        this.executionService = executionService;
        this.mapper = mapper;
    }

    @PostMapping("/api/vector/retrieve")
    public ResponseEntity<RetrievalResponse> retrieve(@Valid @RequestBody RetrievalRequest request) {
        RetrievalQueryExecutionResult result = executionService.execute(mapper.toCommand(request));
        return ResponseEntity.ok(mapper.toResponse(result));
    }
}
