package dev.akre.covenant.types;

import static org.junit.jupiter.api.Assertions.*;

import dev.akre.covenant.api.Type;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class JsonSchemaParsingTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);
    private final JsonSchemaParser parser = new JsonSchemaParser(system);

    @Test
    public void testBasicPrimitives() throws Exception {
        JsonNode stringSchema = mapper.readTree("{\"type\": \"string\"}");
        Type stringDef = parser.parse(stringSchema);
        assertEquals(system.find("String").orElseThrow(), stringDef);

        JsonNode intSchema = mapper.readTree("{\"type\": \"integer\"}");
        Type intDef = parser.parse(intSchema);
        assertEquals(system.find("Int").orElseThrow(), intDef);
    }

    @Test
    public void testObjectParsing() throws Exception {
        String json = """
            {
              "type": "object",
              "properties": {
                "id": { "type": "string" },
                "age": { "type": "integer" }
              },
              "required": ["id"]
            }
            """;
        JsonNode schema = mapper.readTree(json);
        Type type = parser.parse(schema);

        system.assertThat(type).satisfies("Object<id: String, age: Int?, ...>");
        system.assertThat("Object<id: String, age: Int>").satisfies(type.repr());
        system.assertThat("Object<id: String>").satisfies(type.repr());
    }

    @Test
    public void testArrayParsing() throws Exception {
        JsonNode schema = mapper.readTree("{\"type\": \"array\", \"items\": {\"type\": \"integer\"}}");
        Type type = parser.parse(schema);

        system.assertThat(type).satisfies("Array<Int...>");
        system.assertThat("Array<Int, Int>").satisfies(type.repr());
    }

    @Test
    public void testAdditionalPropertiesParsing() throws Exception {
        String json = """
            {
              "type": "object",
              "additionalProperties": { "type": "integer" }
            }
            """;
        JsonNode schema = mapper.readTree(json);
        Type type = parser.parse(schema);

        // It should satisfy an object with any int property, but not string
        system.assertThat(type).satisfiedBy("Object<foo: Int>");
        system.assertThat(type).notSatisfiedBy("Object<foo: String>");
    }

    @Test
    public void testAlgebraicParsing() throws Exception {
        JsonNode schema = mapper.readTree("{\"anyOf\": [{\"type\": \"string\"}, {\"type\": \"integer\"}]}");
        Type type = parser.parse(schema);

        system.assertThat(type).isEquivalentTo("String | Int");
    }

    @Test
    public void testEnforceDerivedSystem() {
        assertThrows(IllegalArgumentException.class, () -> {
            AbstractTypeSystem nonDerived =
                    new TypeSystemBuilderImpl().atom("String").build();
            new JsonSchemaParser(nonDerived);
        });
    }

    @Test
    public void testTypedAdditionalProperties() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);
        JsonSchemaParser parser = new JsonSchemaParser(system);
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();

        String json = """
            {
              "type": "object",
              "properties": {
                "id": { "type": "string" }
              },
              "additionalProperties": { "type": "integer" }
            }
            """;
        Type type = parser.parse(mapper.readTree(json));

        system.assertThat(type).term("id").satisfies("String");
        system.assertThat(type).term("unknown").satisfies("Int");
        system.assertThat(type).satisfiedBy("Object<id: String, extra: Int>");
        system.assertThat(type).notSatisfiedBy("Object<id: String, extra: String>");
    }

    @Test
    public void testLengthConstraints() throws Exception {
        // String length
        String stringJson = """
            {
              "type": "string",
              "minLength": 5,
              "maxLength": 10
            }
            """;
        Type stringType = parser.parse(mapper.readTree(stringJson));
        system.assertThat(stringType).isEquivalentTo("String & minlength 5 & maxlength 10");

        // Array items length
        String arrayJson = """
            {
              "type": "array",
              "items": { "type": "integer" },
              "minItems": 2,
              "maxItems": 4
            }
            """;
        Type arrayType = parser.parse(mapper.readTree(arrayJson));
        system.assertThat(arrayType).isEquivalentTo("Array<Int...> & minitems 2 & maxitems 4");
    }

    @Test
    public void testNestedLengthConstraints() throws Exception {
        String json = """
            {
              "type": "object",
              "properties": {
                "tags": {
                  "type": "array",
                  "items": {
                    "type": "string",
                    "minLength": 3
                  },
                  "minItems": 1
                }
              }
            }
            """;
        Type type = parser.parse(mapper.readTree(json));
        
        // Verify satisfy - tags is optional because it's not in 'required'
        system.assertThat(type).isEquivalentTo("Object<tags?: Array<minlength 3...> & minlength 1, ...>");
        
        // Verify satisfy
        system.assertThat("Object<tags: Array<'foo'>>").satisfies(type.repr());
        system.assertThat("Object<tags: Array<'a'>>").notSatisfies(type.repr());
        system.assertThat("Object<tags: Array<()>>").notSatisfies(type.repr());
    }

    @Test
    public void testPatternParsing() throws Exception {
        String json = "{\"type\": \"string\", \"pattern\": \"^a.*\"}";
        Type type = parser.parse(mapper.readTree(json));
        system.assertThat(type).isEquivalentTo("String & matches \"^a.*\"");
    }
}
