package com.example.ccas.retrieval.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;

class StructuredBatchValidationMainTest {
    @TempDir Path root;
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode row() throws Exception {
        var row = (ObjectNode) mapper.readTree("""
            {"queryKey":"synthetic-1","groupId":"group-1","splitProposal":"dev",
             "request":{"inputSource":"TEXT","maskingCompleted":true,"maskedText":"흉기로 위협한다는 주장",
             "structuredComplaint":{"factualSummary":"흉기 위협 주장","context":{"placeType":"UNKNOWN","relationshipType":"UNKNOWN","timePattern":"UNKNOWN"},
             "observedFacts":[{"factType":"ACTION","value":"위협 주장","evidence":"흉기로 위협한다는 주장","certainty":"UNCERTAIN"}],
             "riskSignals":[{"code":"WEAPON","evidence":"흉기","certainty":"UNCERTAIN"}],
             "subjectMatters":[{"code":"SAFETY_THREAT","evidence":"위협","certainty":"UNCERTAIN"}],
             "requestedActions":[{"code":"UNKNOWN","evidence":"주장","certainty":"UNCERTAIN"}],
             "ongoingStatus":"UNKNOWN","missingInformation":["현재성 확인"]}}}
            """);
        row.put("sourceTextSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(row.path("request").path("maskedText").asText().getBytes(StandardCharsets.UTF_8))));
        return row;
    }

    private Path input(ObjectNode row) throws Exception {
        Path path = root.resolve("input.jsonl");
        Files.writeString(path, mapper.writeValueAsString(row));
        return path;
    }

    @Test void usesProductionBuilderAndPreservesRisk() throws Exception {
        Path input = input(row());
        Path output = root.resolve("out");
        var summary = StructuredBatchValidationMain.run(input, output, 1);
        assertThat(summary.get("validatedCount")).isEqualTo(1);
        var result = mapper.readTree(Files.readString(output.resolve("validated-requests.jsonl")));
        assertThat(result.path("riskSignalPresent").asBoolean()).isTrue();
        assertThat(result.path("searchText").asText()).contains("위험 신호: 흉기 또는 무기 관련 위험(확인 필요)")
                .doesNotContain("현재성 확인");
        assertThatThrownBy(() -> StructuredBatchValidationMain.run(input, output, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsMaskingIncompleteWithoutSearchableOutput() throws Exception {
        var value = row();
        ((ObjectNode)value.path("request")).put("maskingCompleted", false);
        Path input = input(value);
        Path output = root.resolve("out");
        assertThatThrownBy(() -> StructuredBatchValidationMain.run(input, output, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(output.resolve("validated-requests.jsonl")).doesNotExist();
        assertThat(Files.readString(output.resolve("validation.json"))).contains("maskingCompleted").doesNotContain("흉기");
    }

    @Test void rejectsInvalidEnumWithoutLoggingSource() throws Exception {
        var value = row();
        ((ObjectNode)value.path("request").path("structuredComplaint")).put("ongoingStatus", "INVALID");
        Path input = input(value);
        Path output = root.resolve("out");
        assertThatThrownBy(() -> StructuredBatchValidationMain.run(input, output, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(output.resolve("validation.json"))).contains("INVALID_JSON_ENUM_HASH_OR_IDENTITY").doesNotContain("흉기");
    }

    @Test void rejectsChangedSourceHash() throws Exception {
        var value = row();
        value.put("sourceTextSha256", "wrong");
        Path input = input(value);
        Path output = root.resolve("out");
        assertThatThrownBy(() -> StructuredBatchValidationMain.run(input, output, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(output.resolve("validated-requests.jsonl")).doesNotExist();
    }

    @Test void rejectsGroupLeakageAcrossSplits() throws Exception {
        var first = row();
        var second = row();
        second.put("queryKey", "synthetic-2");
        second.put("splitProposal", "test");
        Path input = root.resolve("input.jsonl");
        Files.writeString(input, mapper.writeValueAsString(first) + "\n" + mapper.writeValueAsString(second));
        Path output = root.resolve("out");
        assertThatThrownBy(() -> StructuredBatchValidationMain.run(input, output, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThat(output.resolve("validated-requests.jsonl")).doesNotExist();
    }
}
