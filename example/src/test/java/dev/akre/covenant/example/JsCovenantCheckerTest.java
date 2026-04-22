package dev.akre.covenant.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

public class JsCovenantCheckerTest {

    @Test
    public void testValidContract() throws Exception {
        String js = """
            (user) => {
                let category = "Minor";
                if (user.age >= 18) {
                    category = "Adult";
                }

                if (user.status === "inactive") {
                    return "Archived " + category;
                }
                return category;
            }
        """;

        String inputSchemaStr = """
        {
            "type": "object",
            "properties": {
                "age": {"type": "integer"},
                "status": {"enum": ["active", "inactive"]}
            },
            "required": ["age", "status"]
        }
        """;

        String outputSchemaStr = """
        {
            "type": "string"
        }
        """;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode inputSchema = mapper.readTree(inputSchemaStr);
        JsonNode outputSchema = mapper.readTree(outputSchemaStr);

        JsCovenantChecker checker = new JsCovenantChecker();
        boolean isValid = checker.verify(js, inputSchema, outputSchema);

        assertTrue(isValid, "The JavaScript function should satisfy the String output schema.");
    }

    @Test
    public void testInvalidContractReturnsNumber() throws Exception {
        String js = """
            (user) => {
                return user.age;
            }
        """;

        String inputSchemaStr = """
        {
            "type": "object",
            "properties": {
                "age": {"type": "integer"},
                "status": {"enum": ["active", "inactive"]}
            },
            "required": ["age", "status"]
        }
        """;

        String outputSchemaStr = """
        {
            "type": "string"
        }
        """;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode inputSchema = mapper.readTree(inputSchemaStr);
        JsonNode outputSchema = mapper.readTree(outputSchemaStr);

        JsCovenantChecker checker = new JsCovenantChecker();
        boolean isValid = checker.verify(js, inputSchema, outputSchema);

        assertFalse(isValid, "The JavaScript function returning a Number should NOT satisfy the String output schema.");
    }
}
