package com.example.ccas.retrieval.experiment;

import com.example.ccas.CcasApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;

public class GeneralComplaintSimilarityExperimentMain {

    public static void main(String[] args) {
        ExperimentProperties properties = ExperimentProperties.fromSystemProperties();
        ExperimentProperties.requireOpenAiApiKey(System.getenv("OPENAI_API_KEY"));

        ConfigurableApplicationContext context = new SpringApplicationBuilder(CcasApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("local")
                .properties("spring.main.web-application-type=none")
                .run(args);
        try {
            Path reportPath = context.getBean(GeneralComplaintSimilarityExperimentRunner.class).run(properties);
            System.out.println("Semantic similarity experiment report written to " + reportPath);
        } finally {
            context.close();
        }
    }
}
