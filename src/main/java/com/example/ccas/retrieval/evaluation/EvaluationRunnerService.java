package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.application.ingestion.CreateKnowledgeSourceCommand;
import com.example.ccas.retrieval.application.ingestion.IngestCategoryReferenceCommand;
import com.example.ccas.retrieval.application.ingestion.IngestOfficialGuideCommand;
import com.example.ccas.retrieval.application.ingestion.IngestVerifiedCaseCommand;
import com.example.ccas.retrieval.application.ingestion.KnowledgeSourceIngestionService;
import com.example.ccas.retrieval.application.ingestion.RetrievalItemIngestionService;
import com.example.ccas.retrieval.application.query.ExecuteRetrievalQueryCommand;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionResult;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionService;
import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.search.RetrievalSearchHit;
import com.example.ccas.retrieval.config.RetrievalEvaluationProperties;
import com.example.ccas.retrieval.config.RetrievalSearchProperties;
import com.example.ccas.retrieval.domain.SubjectMatter;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EvaluationRunnerService {

    private final KnowledgeSourceIngestionService sourceIngestionService;
    private final RetrievalItemIngestionService itemIngestionService;
    private final RetrievalQueryExecutionService queryExecutionService;
    private final EvaluationMetricsCalculator metricsCalculator;
    private final RetrievalSearchProperties searchProperties;
    private final RetrievalEvaluationProperties evaluationProperties;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public EvaluationRunnerService(
            KnowledgeSourceIngestionService sourceIngestionService,
            RetrievalItemIngestionService itemIngestionService,
            RetrievalQueryExecutionService queryExecutionService,
            EvaluationMetricsCalculator metricsCalculator,
            RetrievalSearchProperties searchProperties,
            RetrievalEvaluationProperties evaluationProperties,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.sourceIngestionService = sourceIngestionService;
        this.itemIngestionService = itemIngestionService;
        this.queryExecutionService = queryExecutionService;
        this.metricsCalculator = metricsCalculator;
        this.searchProperties = searchProperties;
        this.evaluationProperties = evaluationProperties;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public EvaluationReport run(EvaluationDataset dataset) {
        validate(dataset);
        if (!dataset.thresholdProfileVersion().equals(evaluationProperties.thresholdProfileVersion())) {
            throw new EvaluationDatasetException("Dataset thresholdProfileVersion does not match active evaluation profile.");
        }

        Map<Long, String> itemKeyById = ingestKnowledgeItems(dataset);
        List<EvaluationQueryResult> queryResults = dataset.queries().stream()
                .map(query -> executeQuery(query, itemKeyById))
                .toList();

        return metricsCalculator.calculate(
                dataset,
                evaluationProperties.provisional(),
                queryResults,
                searchProperties.topK().verifiedCase(),
                searchProperties.topK().categoryReference(),
                searchProperties.topK().officialGuide()
        );
    }

    public void writeJsonReport(EvaluationReport report, Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(outputDirectory.resolve(report.datasetId() + ".json").toFile(), report);
        } catch (IOException exception) {
            throw new EvaluationDatasetException("Failed to write retrieval evaluation report.", exception);
        }
    }

    private Map<Long, String> ingestKnowledgeItems(EvaluationDataset dataset) {
        Map<Long, String> itemKeyById = new HashMap<>();
        for (EvaluationKnowledgeItem item : dataset.knowledgeItems()) {
            long sourceId = sourceIngestionService.createSource(new CreateKnowledgeSourceCommand(
                    item.sourceType(),
                    item.sourceTitle(),
                    "synthetic-evaluation-source",
                    null,
                    null,
                    null
            ));
            long itemId = ingestItem(item, sourceId);
            itemKeyById.put(itemId, item.itemKey());
        }
        return itemKeyById;
    }

    private long ingestItem(EvaluationKnowledgeItem item, long sourceId) {
        return switch (item.itemType()) {
            case VERIFIED_CASE -> itemIngestionService.ingestVerifiedCase(new IngestVerifiedCaseCommand(
                    sourceId,
                    item.itemKey(),
                    item.title(),
                    "synthetic masked text for " + item.itemKey(),
                    item.verifiedCase(),
                    verifiedCaseCategoryCode(item),
                    null,
                    item.reviewStatus()
            ));
            case CATEGORY_REFERENCE -> itemIngestionService.ingestCategoryReference(new IngestCategoryReferenceCommand(
                    sourceId,
                    item.itemKey(),
                    item.title(),
                    item.categoryReference(),
                    item.reviewStatus()
            ));
            case OFFICIAL_GUIDE -> itemIngestionService.ingestOfficialGuide(new IngestOfficialGuideCommand(
                    sourceId,
                    item.itemKey(),
                    item.title(),
                    item.officialGuideChunk(),
                    item.reviewStatus()
            ));
        };
    }

    private String verifiedCaseCategoryCode(EvaluationKnowledgeItem item) {
        return item.verifiedCase().subjectMatters().stream()
                .findFirst()
                .map(SubjectMatter::code)
                .map(SubjectMatterCode::name)
                .orElseThrow(() -> new EvaluationDatasetException("VERIFIED_CASE requires at least one subject matter."));
    }

    private EvaluationQueryResult executeQuery(EvaluationQueryCase query, Map<Long, String> itemKeyById) {
        RetrievalQueryExecutionResult result = queryExecutionService.execute(new ExecuteRetrievalQueryCommand(
                query.inputSource(),
                true,
                query.maskedText(),
                query.structuredComplaint()
        ));
        RetrievalCandidates candidates = result.candidates();
        return new EvaluationQueryResult(
                query.queryKey(),
                query.expectedEvidenceStatus(),
                result.decision().evidenceStatus(),
                query.expectedEvidenceStatus() == result.decision().evidenceStatus(),
                query.expectedRiskSignalPresent(),
                result.riskSignalPresent(),
                returnedKeys(candidates.verifiedCases(), itemKeyById),
                returnedKeys(candidates.categoryReferences(), itemKeyById),
                returnedKeys(candidates.officialGuides(), itemKeyById)
        );
    }

    private List<String> returnedKeys(List<RetrievalSearchHit> hits, Map<Long, String> itemKeyById) {
        return hits.stream()
                .map(hit -> {
                    String itemKey = itemKeyById.get(hit.itemId());
                    if (itemKey == null) {
                        throw new EvaluationDatasetException("Search result itemId has no evaluation itemKey mapping.");
                    }
                    return itemKey;
                })
                .toList();
    }

    private void validate(EvaluationDataset dataset) {
        Set<ConstraintViolation<EvaluationDataset>> violations = validator.validate(dataset);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new EvaluationDatasetException(message);
        }
    }
}
