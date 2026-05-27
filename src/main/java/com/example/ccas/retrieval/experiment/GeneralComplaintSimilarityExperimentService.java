package com.example.ccas.retrieval.experiment;

import com.example.ccas.retrieval.config.EmbeddingProperties;
import com.example.ccas.retrieval.embedding.EmbeddingPort;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.experiment.dto.ExperimentComplaintRecord;
import com.example.ccas.retrieval.experiment.dto.ExpectedSimilarityPairs;
import com.example.ccas.retrieval.experiment.persistence.ExperimentGeneralComplaintRepository;
import com.example.ccas.retrieval.experiment.report.GeneralComplaintSimilarityReport;
import com.example.ccas.retrieval.experiment.report.QuerySimilarityResult;
import com.example.ccas.retrieval.experiment.report.SimilarityMetrics;
import com.example.ccas.retrieval.experiment.report.SimilarityMetricsCalculator;
import com.example.ccas.retrieval.experiment.report.SimilaritySearchHit;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GeneralComplaintSimilarityExperimentService {

    private final ExperimentInputLoader inputLoader;
    private final ExperimentCorpusSelector corpusSelector;
    private final ExpectedPairsValidator expectedPairsValidator;
    private final GeneralComplaintExperimentSearchTextBuilder searchTextBuilder;
    private final EmbeddingPort embeddingPort;
    private final EmbeddingProperties embeddingProperties;
    private final ExperimentGeneralComplaintRepository repository;
    private final SimilarityMetricsCalculator metricsCalculator;

    public GeneralComplaintSimilarityExperimentService(
            ExperimentInputLoader inputLoader,
            ExperimentCorpusSelector corpusSelector,
            ExpectedPairsValidator expectedPairsValidator,
            GeneralComplaintExperimentSearchTextBuilder searchTextBuilder,
            EmbeddingPort embeddingPort,
            EmbeddingProperties embeddingProperties,
            ExperimentGeneralComplaintRepository repository,
            SimilarityMetricsCalculator metricsCalculator
    ) {
        this.inputLoader = inputLoader;
        this.corpusSelector = corpusSelector;
        this.expectedPairsValidator = expectedPairsValidator;
        this.searchTextBuilder = searchTextBuilder;
        this.embeddingPort = embeddingPort;
        this.embeddingProperties = embeddingProperties;
        this.repository = repository;
        this.metricsCalculator = metricsCalculator;
    }

    public GeneralComplaintSimilarityReport run(ExperimentProperties properties) {
        List<ExperimentComplaintRecord> inputRecords = inputLoader.loadComplaintRecords(properties.input());
        ExperimentCorpus corpus = corpusSelector.select(inputRecords);
        Map<Long, ExperimentComplaintRecord> recordsById = corpus.records().stream()
                .collect(Collectors.toMap(
                        ExperimentComplaintRecord::id,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Set<Long> corpusIds = recordsById.keySet();

        ExpectedSimilarityPairs expectedPairs = inputLoader.loadExpectedPairs(properties.expected());
        expectedPairsValidator.validate(expectedPairs, corpusIds);

        repository.recreateTable();
        Map<Long, EmbeddingResult> embeddingsById = embedCorpusOnce(corpus.records(), properties.variant());
        saveCorpus(corpus.records(), embeddingsById);

        List<QuerySimilarityResult> queryResults = expectedPairs.queries().stream()
                .map(query -> {
                    EmbeddingResult queryEmbedding = embeddingsById.get(query.queryId());
                    List<SimilaritySearchHit> top5 = repository.searchTop5(query.queryId(), queryEmbedding);
                    return metricsCalculator.calculateQuery(query, top5);
                })
                .toList();
        SimilarityMetrics metrics = metricsCalculator.calculateMetrics(queryResults);

        return new GeneralComplaintSimilarityReport(
                "general-complaint-similarity-" + Instant.now(),
                embeddingProperties.model(),
                embeddingProperties.version(),
                properties.variant().name(),
                corpus.records().size(),
                corpus.excludedRecords().size(),
                corpus.excludedRecords(),
                queryResults,
                metrics
        );
    }

    private Map<Long, EmbeddingResult> embedCorpusOnce(
            List<ExperimentComplaintRecord> records,
            ExperimentSearchTextVariant variant
    ) {
        Map<Long, EmbeddingResult> embeddings = new LinkedHashMap<>();
        for (ExperimentComplaintRecord record : records) {
            String searchText = searchTextBuilder.build(record, variant);
            embeddings.put(record.id(), embeddingPort.embed(searchText));
        }
        return embeddings;
    }

    private void saveCorpus(List<ExperimentComplaintRecord> records, Map<Long, EmbeddingResult> embeddingsById) {
        for (ExperimentComplaintRecord record : records) {
            repository.save(record.id(), record.title(), embeddingsById.get(record.id()));
        }
    }
}
