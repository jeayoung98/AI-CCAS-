package com.example.ccas.retrieval.evaluation;

import com.example.ccas.CcasApplication;
import com.example.ccas.retrieval.config.RetrievalSearchProperties;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.nio.file.*;
import java.util.*;

/** Reads an existing isolated corpus and a masked evaluation dataset. Never calls OpenAI. */
public class RetrievalReplayMain {
    public static void main(String[] args) throws Exception {
        Path datasetPath = Path.of(required("replay.dataset"));
        Path cachePath = Path.of(required("replay.embeddings"));
        Path output = Path.of(System.getProperty("replay.output", "build/retrieval-replay.json"));
        try (var context = new SpringApplicationBuilder(CcasApplication.class).web(WebApplicationType.NONE).run(args)) {
            var mapper = context.getBean(ObjectMapper.class);
            var dataset = context.getBean(EvaluationDatasetLoader.class).load(datasetPath);
            Map<String, EmbeddingResult> embeddings = mapper.readValue(cachePath.toFile(), new TypeReference<>() {});
            var topK = context.getBean(RetrievalSearchProperties.class).topK();
            int candidates = Integer.parseInt(System.getProperty("replay.candidates", "20"));
            int rrf = Integer.parseInt(System.getProperty("replay.rrf", "60"));
            var configurations = new ArrayList<RetrievalSearchProperties>();
            configurations.add(new RetrievalSearchProperties(topK));
            configurations.add(new RetrievalSearchProperties(topK, RetrievalSearchProperties.Strategy.EXACT, candidates, 0, rrf));
            for (String value : System.getProperty("replay.weights", "0,0.1,0.3").split(",")) {
                double weight = Double.parseDouble(value);
                if (!Double.isFinite(weight) || weight < 0 || weight > 1 || candidates < 1 || candidates > 1000 || rrf < 1)
                    throw new IllegalArgumentException("Invalid replay settings");
                configurations.add(new RetrievalSearchProperties(topK, RetrievalSearchProperties.Strategy.METADATA, candidates, weight, rrf));
                configurations.add(new RetrievalSearchProperties(topK, RetrievalSearchProperties.Strategy.COSINE_CODE_RRF, candidates, weight, rrf));
            }
            configurations.add(new RetrievalSearchProperties(topK, RetrievalSearchProperties.Strategy.CODE_RRF, candidates, 0, rrf));
            var report = context.getBean(RetrievalReplayExperiment.class).run(dataset, embeddings, configurations,
                    Integer.parseInt(System.getProperty("replay.warmups", "3")),
                    Integer.parseInt(System.getProperty("replay.repetitions", "10")));
            Files.createDirectories(output.toAbsolutePath().getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
            System.out.println("Retrieval replay report: " + output.toAbsolutePath());
        }
    }
    private static String required(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required -D" + property);
        return value;
    }
}
