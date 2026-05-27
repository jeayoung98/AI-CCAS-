package com.example.ccas.retrieval.application.query;

import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.text.RetrievalMetadata;
import com.example.ccas.retrieval.application.text.SearchTextBuilder;
import com.example.ccas.retrieval.application.text.StructuredMetadataExtractor;
import com.example.ccas.retrieval.config.RetrievalVersionProperties;
import com.example.ccas.retrieval.embedding.EmbeddingPort;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalQueryInsertRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RetrievalQueryExecutionService {

    private final Validator validator;
    private final SearchTextBuilder searchTextBuilder;
    private final StructuredMetadataExtractor metadataExtractor;
    private final EmbeddingPort embeddingPort;
    private final ObjectMapper objectMapper;
    private final RetrievalVersionProperties versionProperties;
    private final RetrievalQueryTransactionalExecutor transactionalExecutor;

    public RetrievalQueryExecutionService(
            Validator validator,
            SearchTextBuilder searchTextBuilder,
            StructuredMetadataExtractor metadataExtractor,
            EmbeddingPort embeddingPort,
            ObjectMapper objectMapper,
            RetrievalVersionProperties versionProperties,
            RetrievalQueryTransactionalExecutor transactionalExecutor
    ) {
        this.validator = validator;
        this.searchTextBuilder = searchTextBuilder;
        this.metadataExtractor = metadataExtractor;
        this.embeddingPort = embeddingPort;
        this.objectMapper = objectMapper;
        this.versionProperties = versionProperties;
        this.transactionalExecutor = transactionalExecutor;
    }

    public RetrievalQueryExecutionResult execute(ExecuteRetrievalQueryCommand command) {
        validate(command);

        UUID queryId = UUID.randomUUID();
        String searchText = searchTextBuilder.buildForComplaint(command.structuredComplaint());
        RetrievalMetadata metadata = metadataExtractor.extract(command.structuredComplaint());
        EmbeddingResult embeddingResult = embeddingPort.embed(searchText);
        String structuredPayloadJson = serializePayload(command);

        RetrievalQueryInsertRow row = new RetrievalQueryInsertRow(
                queryId,
                command.inputSource(),
                command.maskingCompleted(),
                command.maskedText(),
                structuredPayloadJson,
                searchText,
                metadata,
                embeddingResult,
                versionProperties.structureVersion(),
                versionProperties.complaintSearchTextVersion()
        );

        boolean riskSignalPresent = !command.structuredComplaint().riskSignals().isEmpty();
        RetrievalQueryTransactionResult transactionResult = transactionalExecutor.persistSearchAndEvaluate(
                row,
                embeddingResult,
                riskSignalPresent
        );

        return new RetrievalQueryExecutionResult(
                queryId,
                riskSignalPresent,
                transactionResult.candidates(),
                transactionResult.decision()
        );
    }

    private void validate(ExecuteRetrievalQueryCommand command) {
        if (command == null) {
            throw new InvalidRetrievalQueryException("retrieval query command는 null일 수 없습니다.");
        }

        Set<ConstraintViolation<ExecuteRetrievalQueryCommand>> violations = validator.validate(command);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidRetrievalQueryException(message);
        }
    }

    private String serializePayload(ExecuteRetrievalQueryCommand command) {
        try {
            return objectMapper.writeValueAsString(command.structuredComplaint());
        } catch (JsonProcessingException exception) {
            throw new RetrievalQuerySerializationException("retrieval_query structured_payload JSON 직렬화에 실패했습니다.", exception);
        }
    }
}
