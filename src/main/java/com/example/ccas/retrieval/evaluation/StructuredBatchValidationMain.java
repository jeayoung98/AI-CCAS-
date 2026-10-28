package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.api.dto.RetrievalRequest;
import com.example.ccas.retrieval.api.mapper.RetrievalApiMapper;
import com.example.ccas.retrieval.application.text.SearchTextBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Offline DTO/domain validation and production search-text generation; no Spring/DB/API startup. */
public final class StructuredBatchValidationMain {
    private StructuredBatchValidationMain() {}

    public static void main(String[] args) throws Exception {
        try {
            var report = run(Path.of(required("structure.input")), Path.of(required("structure.output")),
                    Integer.parseInt(System.getProperty("structure.expectedCount", "500")));
            System.out.println("Validated structured queries: " + report.get("validatedCount"));
        } catch (Exception error) {
            // Jackson/constraint exception messages can contain complaint text. Do not print them.
            System.err.println("Structured batch validation failed; inspect the aggregate validation report if produced.");
            System.exit(1);
        }
    }

    public static Map<String, Object> run(Path input, Path output, int expectedCount) throws Exception {
        if (expectedCount < 1 || Files.exists(output)) throw new IllegalArgumentException("Invalid count or existing output");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var apiMapper = new RetrievalApiMapper();
        var builder = new SearchTextBuilder();
        var versions = new Properties();
        try (var resource = StructuredBatchValidationMain.class.getResourceAsStream("/application.properties")) {
            if (resource == null) throw new IllegalStateException("Missing application configuration");
            versions.load(resource);
        }
        String structureVersion = Objects.requireNonNull(versions.getProperty("retrieval.structure.version"));
        String searchTextVersion = Objects.requireNonNull(versions.getProperty("retrieval.search-text.complaint-version"));
        byte[] inputBytes = Files.readAllBytes(input);
        var lines = new String(inputBytes, StandardCharsets.UTF_8).lines().filter(s -> !s.isBlank()).toList();
        var keys = new HashSet<String>();
        var splitGroups = new HashMap<String, String>();
        var errors = new ArrayList<Map<String, Object>>();
        var valid = new ArrayList<ObjectNode>();
        int riskCount = 0;
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (int i = 0; i < lines.size(); i++) {
                try {
                    var node = mapper.readTree(lines.get(i));
                    String key = node.path("queryKey").asText();
                    String group = node.path("groupId").asText();
                    String split = node.path("splitProposal").asText();
                    if (key.isBlank() || !keys.add(key) || group.isBlank() || !Set.of("dev", "test").contains(split))
                        throw new IllegalArgumentException();
                    String existing = splitGroups.putIfAbsent(group, split);
                    if (existing != null && !existing.equals(split)) throw new IllegalArgumentException();
                    var request = mapper.treeToValue(node.path("request"), RetrievalRequest.class);
                    var violations = validator.validate(request);
                    if (!violations.isEmpty()) {
                        errors.add(Map.of("line", i + 1, "code", "DTO_CONSTRAINT",
                                "fields", violations.stream().map(v -> v.getPropertyPath().toString()).sorted().toList()));
                        continue;
                    }
                    if (!sha256(request.maskedText().getBytes(StandardCharsets.UTF_8)).equals(node.path("sourceTextSha256").asText()))
                        throw new IllegalArgumentException();
                    var command = apiMapper.toCommand(request);
                    if (!validator.validate(command).isEmpty()) throw new IllegalArgumentException();
                    String searchText = builder.buildForComplaint(command.structuredComplaint());
                    boolean riskPresent = !command.structuredComplaint().riskSignals().isEmpty();
                    if (riskPresent) riskCount++;
                    ObjectNode result = ((ObjectNode) node).deepCopy();
                    result.put("searchText", searchText);
                    result.put("searchTextSha256", sha256(searchText.getBytes(StandardCharsets.UTF_8)));
                    result.put("structureVersion", structureVersion);
                    result.put("searchTextVersion", searchTextVersion);
                    result.put("riskSignalPresent", riskPresent);
                    result.put("javaValidated", true);
                    valid.add(result);
                } catch (Exception error) {
                    errors.add(Map.of("line", i + 1, "code", "INVALID_JSON_ENUM_HASH_OR_IDENTITY"));
                }
            }
        }
        if (lines.size() != expectedCount) errors.add(Map.of("code", "UNEXPECTED_RECORD_COUNT"));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("inputSha256", sha256(inputBytes));
        report.put("recordCount", lines.size());
        report.put("validatedCount", valid.size());
        report.put("riskSignalPresentCount", riskCount);
        report.put("structureVersion", structureVersion);
        report.put("searchTextVersion", searchTextVersion);
        report.put("errors", errors);
        report.put("success", errors.isEmpty());
        report.put("embeddingExecuted", false);
        report.put("semanticAccuracyValidated", false);
        Files.createDirectories(output);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("validation.json").toFile(), report);
        if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid batch; no searchable output produced");
        var encoded = new ArrayList<String>();
        for (var row : valid) encoded.add(mapper.writeValueAsString(row));
        Files.writeString(output.resolve("validated-requests.jsonl"), String.join("\n", encoded) + "\n", StandardCharsets.UTF_8);
        return report;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing property");
        return value;
    }
}
