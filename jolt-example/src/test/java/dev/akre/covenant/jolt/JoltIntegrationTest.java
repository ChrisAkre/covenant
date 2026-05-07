package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.JsonSchemaParser;
import dev.akre.covenant.types.JsonTypeSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JoltIntegrationTest {

    private JsonSchemaParser jsonParser;
    private JsonMapper mapper;

    @BeforeEach
    public void setup() {
        jsonParser = new JsonSchemaParser(JsonTypeSystem.INSTANCE);
        mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build();
    }

    static Stream<Path> jsonFilesProvider() throws IOException {
        Path jsonDir = Paths.get("src/test/json");
        return Files.walk(jsonDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"));
    }

    @ParameterizedTest
    @MethodSource("jsonFilesProvider")
    public void testJoltTypeChecking(Path jsonFilePath) throws Exception {
        JsonNode rootNode = mapper.readTree(jsonFilePath.toFile());

        JsonNode inputSchemaNode = rootNode.path("inputSchema");
        JsonNode expectedSchemaNode = rootNode.path("expectedSchema");
        JsonNode specNode = rootNode.path("spec");

        assertFalse(inputSchemaNode.isMissingNode(), "Missing inputSchema in " + jsonFilePath);
        assertFalse(expectedSchemaNode.isMissingNode(), "Missing expectedSchema in " + jsonFilePath);
        assertFalse(specNode.isMissingNode(), "Missing spec in " + jsonFilePath);

        Type inputSchema = jsonParser.parse(inputSchemaNode);
        Type expectedSchema = jsonParser.parse(expectedSchemaNode);

        assertNotNull(inputSchema, "Parsed input schema is null in " + jsonFilePath);
        assertNotNull(expectedSchema, "Parsed expected schema is null in " + jsonFilePath);

        // As a dummy check for Jolt expressions, just ensure they are non-bottom for now.
        // Once Jolt Covenant checking is implemented we would assert type inference here.
        assertFalse(inputSchema.isBottom(), "Input schema evaluates to bottom");
    }
}
