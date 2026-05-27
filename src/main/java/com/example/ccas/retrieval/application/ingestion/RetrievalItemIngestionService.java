package com.example.ccas.retrieval.application.ingestion;

import com.example.ccas.retrieval.application.text.RetrievalItemMetadataExtractor;
import com.example.ccas.retrieval.application.text.RetrievalMetadata;
import com.example.ccas.retrieval.application.text.SearchTextBuilder;
import com.example.ccas.retrieval.application.text.StructuredMetadataExtractor;
import com.example.ccas.retrieval.config.RetrievalVersionProperties;
import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import com.example.ccas.retrieval.embedding.EmbeddingPort;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.infrastructure.persistence.KnowledgeSourceRecord;
import com.example.ccas.retrieval.infrastructure.persistence.KnowledgeSourceRepository;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalItemInsertRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RetrievalItemIngestionService {

    private final KnowledgeSourceRepository knowledgeSourceRepository;
    private final RetrievalItemTransactionalWriter writer;
    private final SearchTextBuilder searchTextBuilder;
    private final StructuredMetadataExtractor structuredMetadataExtractor;
    private final RetrievalItemMetadataExtractor retrievalItemMetadataExtractor;
    private final EmbeddingPort embeddingPort;
    private final ObjectMapper objectMapper;
    private final RetrievalVersionProperties versionProperties;
    private final Validator validator;

    public RetrievalItemIngestionService(
            KnowledgeSourceRepository knowledgeSourceRepository,
            RetrievalItemTransactionalWriter writer,
            SearchTextBuilder searchTextBuilder,
            StructuredMetadataExtractor structuredMetadataExtractor,
            RetrievalItemMetadataExtractor retrievalItemMetadataExtractor,
            EmbeddingPort embeddingPort,
            ObjectMapper objectMapper,
            RetrievalVersionProperties versionProperties,
            Validator validator
    ) {
        this.knowledgeSourceRepository = knowledgeSourceRepository;
        this.writer = writer;
        this.searchTextBuilder = searchTextBuilder;
        this.structuredMetadataExtractor = structuredMetadataExtractor;
        this.retrievalItemMetadataExtractor = retrievalItemMetadataExtractor;
        this.embeddingPort = embeddingPort;
        this.objectMapper = objectMapper;
        this.versionProperties = versionProperties;
        this.validator = validator;
    }

    public long ingestVerifiedCase(IngestVerifiedCaseCommand command) {
        validate(command);
        validateVerifiedCaseCategory(command);
        requireSourceType(command.sourceId(), RetrievalItemType.VERIFIED_CASE, KnowledgeSourceType.TEAM_VERIFIED_CASESET);

        String searchText = searchTextBuilder.buildForComplaint(command.structuredComplaint());
        RetrievalMetadata metadata = structuredMetadataExtractor.extract(command.structuredComplaint());
        EmbeddingResult embeddingResult = embeddingPort.embed(searchText);
        String payloadJson = serializePayload(command.structuredComplaint());

        return writer.save(new RetrievalItemInsertRow(
                RetrievalItemType.VERIFIED_CASE,
                command.sourceId(),
                command.externalKey(),
                command.title(),
                command.maskedText(),
                payloadJson,
                searchText,
                metadata.subjectMatterCodes(),
                metadata.requestedActionCodes(),
                metadata.riskSignalCodes(),
                metadata.placeType(),
                metadata.relationshipType(),
                metadata.ongoingStatus(),
                command.verifiedCategoryCode(),
                command.verifiedRouteCode(),
                command.reviewStatus(),
                true,
                embeddingResult,
                versionProperties.structureVersion(),
                versionProperties.complaintSearchTextVersion()
        ));
    }

    public long ingestCategoryReference(IngestCategoryReferenceCommand command) {
        validate(command);
        requireSourceType(command.sourceId(), RetrievalItemType.CATEGORY_REFERENCE, KnowledgeSourceType.CATEGORY_POLICY);

        String searchText = searchTextBuilder.buildForCategoryReference(command.categoryReference());
        RetrievalMetadata metadata = retrievalItemMetadataExtractor.extractFromCategoryReference(command.categoryReference());
        EmbeddingResult embeddingResult = embeddingPort.embed(searchText);
        String payloadJson = serializePayload(command.categoryReference());

        return writer.save(new RetrievalItemInsertRow(
                RetrievalItemType.CATEGORY_REFERENCE,
                command.sourceId(),
                command.externalKey(),
                command.title(),
                null,
                payloadJson,
                searchText,
                metadata.subjectMatterCodes(),
                metadata.requestedActionCodes(),
                metadata.riskSignalCodes(),
                metadata.placeType(),
                metadata.relationshipType(),
                metadata.ongoingStatus(),
                command.categoryReference().categoryCode(),
                null,
                command.reviewStatus(),
                true,
                embeddingResult,
                versionProperties.structureVersion(),
                versionProperties.categoryReferenceSearchTextVersion()
        ));
    }

    public long ingestOfficialGuide(IngestOfficialGuideCommand command) {
        validate(command);
        requireSourceType(command.sourceId(), RetrievalItemType.OFFICIAL_GUIDE, KnowledgeSourceType.OFFICIAL_DOCUMENT);

        String searchText = searchTextBuilder.buildForOfficialGuide(command.officialGuideChunk());
        RetrievalMetadata metadata = retrievalItemMetadataExtractor.extractFromOfficialGuide(command.officialGuideChunk());
        EmbeddingResult embeddingResult = embeddingPort.embed(searchText);
        String payloadJson = serializePayload(command.officialGuideChunk());

        return writer.save(new RetrievalItemInsertRow(
                RetrievalItemType.OFFICIAL_GUIDE,
                command.sourceId(),
                command.externalKey(),
                command.title(),
                null,
                payloadJson,
                searchText,
                metadata.subjectMatterCodes(),
                metadata.requestedActionCodes(),
                metadata.riskSignalCodes(),
                metadata.placeType(),
                metadata.relationshipType(),
                metadata.ongoingStatus(),
                null,
                null,
                command.reviewStatus(),
                true,
                embeddingResult,
                versionProperties.structureVersion(),
                versionProperties.officialGuideSearchTextVersion()
        ));
    }

    private void validateVerifiedCaseCategory(IngestVerifiedCaseCommand command) {
        if (command.reviewStatus() == ReviewStatus.VERIFIED
                && (command.verifiedCategoryCode() == null || command.verifiedCategoryCode().isBlank())) {
            throw new IllegalArgumentException("VERIFIED 상태의 검증 사례는 verifiedCategoryCode가 필요합니다.");
        }
    }

    private void requireSourceType(long sourceId, RetrievalItemType itemType, KnowledgeSourceType expectedType) {
        KnowledgeSourceRecord source = knowledgeSourceRepository.findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException(sourceId));
        if (source.sourceType() != expectedType) {
            throw new InvalidKnowledgeSourceTypeException(itemType, expectedType, source.sourceType());
        }
    }

    private <T> void validate(T command) {
        Set<ConstraintViolation<T>> violations = validator.validate(command);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new RetrievalItemIngestionException("structured_payload JSON 직렬화에 실패했습니다.", exception);
        }
    }
}
