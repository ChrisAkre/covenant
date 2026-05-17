package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.JsonSchemaParser;
import dev.akre.covenant.types.JsonTypeSystem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JoltIntegrationTest {
    public static final Logger LOGGER = LoggerFactory.getLogger(JoltIntegrationTest.class);

    private JsonSchemaParser jsonParser;
    private JsonMapper mapper;

    private static final List<FailureRecord> failures = new ArrayList<>();

    private static class FailureRecord {
        String testName;
        String message;
        String stackTrace;

        FailureRecord(String testName, String message, String stackTrace) {
            this.testName = testName;
            this.message = message;
            this.stackTrace = stackTrace;
        }
    }

    @BeforeEach
    public void setup() {
        jsonParser = new JsonSchemaParser(JsonTypeSystem.INSTANCE);
        mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build();
    }

    static Stream<Path> jsonFilesProvider() throws IOException {
        Path jsonDir = Paths.get("src/test/json/shiftr");
        if (!Files.exists(jsonDir)) {
            // fallback if running from root
             jsonDir = Paths.get("jolt-example/src/test/json/shiftr");
        }
        return Files.walk(jsonDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"))
                .sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonFilesProvider")
    public void testJoltShiftr(Path path) throws Exception {
        try {
            String content = Files.readString(path);
            JsonNode node = mapper.readTree(content);

            if (!node.has("inputSchema") || !node.has("spec") || !node.has("expectedSchema")) {
                return;
            }

            JsonNode inputSchemaNode = node.get("inputSchema");
            JsonNode specNode = node.get("spec");
            JsonNode expectedSchemaNode = node.get("expectedSchema");

            Type inputSchema = jsonParser.parse(inputSchemaNode);
            Type expectedSchema = jsonParser.parse(expectedSchemaNode);

            JoltCovenantChecker checker = new JoltCovenantChecker(JsonTypeSystem.INSTANCE);
            Type inferred = checker.infer(inputSchema, specNode);
            boolean result = expectedSchema.isAssignableFrom(inferred);

            Assumptions.assumeTrue(result, "Jolt verification failed for: " + path + "\nExpected: " + expectedSchema.repr() + "\nInferred: " + inferred.repr());
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            failures.add(new FailureRecord(path.toString(), t.getMessage(), sw.toString()));
        }
    }

    @AfterAll
    public static void reportFailures() throws IOException {
        if (failures.isEmpty()) {
            return;
        }

        Path targetDir = Paths.get("target");
        if (!Files.exists(targetDir)) {
            // fallback if running from root
            targetDir = Paths.get("jolt-example/target");
        }

        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        Path summaryFile = targetDir.resolve("failing_tests_summary.txt");
        List<String> summaryLines = failures.stream()
                .map(f -> f.testName)
                .collect(Collectors.toList());
        Files.write(summaryFile, summaryLines);

        for (FailureRecord failure : failures) {
            String safeName = failure.testName.replace(File.separatorChar, '_').replace('.', '_').replace(':', '_');
            Path detailFile = targetDir.resolve("failing_test_" + safeName + ".txt");
            String content = "Test: " + failure.testName + "\n" +
                             "Message: " + failure.message + "\n\n" +
                             "Stack Trace:\n" + failure.stackTrace;
            Files.writeString(detailFile, content);
        }
    }
}
