package com.example.ccas.retrieval.importing;

import com.example.ccas.retrieval.config.RetrievalImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConditionalOnProperty(prefix = "retrieval.import", name = "enabled", havingValue = "true")
public class RetrievalKnowledgeImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RetrievalKnowledgeImportRunner.class);

    private final RetrievalKnowledgeImportService importService;
    private final RetrievalImportProperties properties;

    public RetrievalKnowledgeImportRunner(
            RetrievalKnowledgeImportService importService,
            RetrievalImportProperties properties
    ) {
        this.importService = importService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.filePath() == null || properties.filePath().isBlank()) {
            throw new RetrievalKnowledgeImportException("retrieval.import.file-path is required when import is enabled.");
        }
        RetrievalKnowledgeImportReport report = importService.importDataset(
                Path.of(properties.filePath()),
                new ImportExecutionOptions(properties.allowSynthetic(), properties.dryRun())
        );
        log.info(
                "Retrieval knowledge import completed: datasetId={}, synthetic={}, dryRun={}, sources={}, items={}, searchableVerifiedItems={}",
                report.datasetId(),
                report.synthetic(),
                report.dryRun(),
                report.sourceCount(),
                report.totalItemCount(),
                report.searchableVerifiedItemCount()
        );
    }
}
