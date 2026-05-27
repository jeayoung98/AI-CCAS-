package com.example.ccas.retrieval.api.error;

import com.example.ccas.retrieval.application.query.InvalidRetrievalQueryException;
import com.example.ccas.retrieval.application.query.RetrievalQuerySerializationException;
import com.example.ccas.retrieval.application.search.RetrievalSearchException;
import com.example.ccas.retrieval.embedding.EmbeddingConfigurationException;
import com.example.ccas.retrieval.embedding.EmbeddingRequestException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = com.example.ccas.retrieval.api.RetrievalController.class)
public class RetrievalApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid retrieval request.", "Request validation failed.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid retrieval request.", "Request body is malformed or contains unsupported values.");
    }

    @ExceptionHandler(InvalidRetrievalQueryException.class)
    ProblemDetail handleInvalidRetrievalQuery(InvalidRetrievalQueryException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid retrieval request.", "Request validation failed.");
    }

    @ExceptionHandler(EmbeddingRequestException.class)
    ProblemDetail handleEmbeddingRequestFailure(EmbeddingRequestException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "Retrieval processing failed.", "Embedding provider request failed.");
    }

    @ExceptionHandler({
            EmbeddingConfigurationException.class,
            RetrievalQuerySerializationException.class,
            RetrievalSearchException.class,
            DataAccessException.class,
            IllegalStateException.class
    })
    ProblemDetail handleServerFailure(RuntimeException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Retrieval processing failed.", "The retrieval request could not be completed.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }
}
