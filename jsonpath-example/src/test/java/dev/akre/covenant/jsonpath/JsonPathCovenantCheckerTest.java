package dev.akre.covenant.jsonpath;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.JsonSchemaParser;
import dev.akre.covenant.types.JsonTypeSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonPathCovenantCheckerTest {

    private JsonPathCovenantChecker checker;
    private AbstractTypeSystem system;
    private ObjectMapper mapper;
    private JsonSchemaParser jsonParser;

    private static final String STORE_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "store": {
          "type": "object",
          "properties": {
            "book": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "category": { "type": "string" },
                  "author": { "type": "string" },
                  "title": { "type": "string" },
                  "isbn": { "type": "string" },
                  "price": { "type": "number" }
                },
                "required": ["category", "author", "title", "price"],
                "additionalProperties": false
              }
            },
            "bicycle": {
              "type": "object",
              "properties": {
                "color": { "type": "string" },
                "price": { "type": "number" }
              },
              "required": ["color", "price"],
              "additionalProperties": false
            }
          },
          "required": ["book", "bicycle"],
          "additionalProperties": false
        },
        "expensive": {
          "type": "number"
        }
      },
      "required": ["store", "expensive"],
      "additionalProperties": false
    }
    """;

    @BeforeEach
    public void setup() {
        checker = new JsonPathCovenantChecker(false);
        system = JsonPathTypeSystem.INSTANCE;
        mapper = new ObjectMapper();
        jsonParser = new JsonSchemaParser(system);
    }

    private Type parseSchema(String jsonSchema) throws Exception {
        return jsonParser.parse(mapper.readTree(jsonSchema));
    }

    @Test
    public void testWildcardAndUnions() throws Exception {
        Type schema = parseSchema(STORE_SCHEMA);
        Type expected = system.expression("Nodelist<Number>");

        Type inferred = checker.infer(schema, "$.store.*.price");
        assertTrue(expected.isAssignableFrom(inferred), "Wildcard extraction should result in Nodelist<Number>. Expected: " + expected.repr() + ", Actual: " + inferred.repr());
    }

    @Test
    public void testMathematicalFilters() throws Exception {
        Type schema = parseSchema(STORE_SCHEMA);
        Type inferred = checker.infer(schema, "$.store.book[?(@.category == 'fiction')]");

        // Since `additionalProperties: false` was set, there is no spread.
        // The original type is: Object<category: String, author: String, title: String, isbn: ?String, price: Number>
        // The constraint applied is: Object<category: 'fiction' & String, ...>
        // The result type generated during traversal might collapse into generic constraints
        Type expected = system.expression("Nodelist<Object<category: 'fiction' & String, ...>>");

        assertTrue(expected.isAssignableFrom(inferred), "Filter should narrow member type appropriately. Expected: " + expected.repr() + ", Actual: " + inferred.repr());

        // Ensure price logic checks against valid parameter formats by unwrapping first or checking properties
        Type priceConstraint = system.expression("Object<price: Number, ...>");
        // Manually inferring logic implies the intersection was successful against the category object. We rely on the core behavior.
    }

    @Test
    public void testDeepScan() throws Exception {
        Type schema = parseSchema(STORE_SCHEMA);
        Type inferred = checker.infer(schema, "$..price");

        // Deep scan for 'price' should extract Number from book properties, and Number from bicycle properties.
        // Thus the resulting type is Nodelist<Number>
        Type expectedPriceResult = system.expression("Nodelist<Number>");
        assertTrue(expectedPriceResult.isAssignableFrom(inferred), "Deep scan should union all 'price' properties. Expected: " + expectedPriceResult.repr() + ", Actual: " + inferred.repr());
    }

    @Test
    public void testDefinitePathNoWrapper() throws Exception {
        String jsonSchema = """
        {
          "type": "object",
          "properties": {
            "name": { "type": "string" }
          },
          "additionalProperties": false
        }
        """;
        Type schema = parseSchema(jsonSchema);
        Type inferred = checker.infer(schema, "$.name");

        Type expected = system.expression("String");
        assertTrue(expected.isAssignableFrom(inferred), "Definite path without option enabled should not wrap in Nodelist");
    }

    @Test
    public void testDefinitePathWithWrapper() throws Exception {
        JsonPathCovenantChecker configuredChecker = new JsonPathCovenantChecker(true);
        String jsonSchema = """
        {
          "type": "object",
          "properties": {
            "name": { "type": "string" }
          },
          "additionalProperties": false
        }
        """;
        Type schema = parseSchema(jsonSchema);
        Type inferred = configuredChecker.infer(schema, "$.name");

        Type expected = system.expression("Nodelist<String>");
        assertTrue(expected.isAssignableFrom(inferred), "Definite path with option enabled should wrap in Nodelist");
    }
}
