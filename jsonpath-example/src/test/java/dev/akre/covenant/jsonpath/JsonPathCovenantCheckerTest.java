package dev.akre.covenant.jsonpath;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonPathCovenantCheckerTest {

    private JsonPathCovenantChecker checker;
    private AbstractTypeSystem system;

    @BeforeEach
    public void setup() {
        checker = new JsonPathCovenantChecker(false);
        system = JsonPathTypeSystem.INSTANCE;
    }

    @Test
    public void testWildcardAndUnions() {
        Type schema = system.expression("Object<store: Object<book: Object<price: Number>, bicycle: Object<price: Number>>>");
        Type expected = system.expression("Nodelist<Number>");

        Type inferred = checker.infer(schema, "$.store.*.price");
        assertTrue(expected.isAssignableFrom(inferred), "Wildcard extraction should result in Nodelist<Number>");
    }

    @Test
    public void testMathematicalFilters() {
        Type schema = system.expression("Array<Object<category: String, price: Number>>");
        Type inferred = checker.infer(schema, "$[?(@.category == 'fiction')]");

        Type expected = system.expression("Nodelist<Object<category: 'fiction' & String, ...>>");

        System.out.println("Inferred: " + inferred.repr());
        System.out.println("Expected: " + expected.repr());

        assertTrue(expected.isAssignableFrom(inferred), "Filter should narrow member type appropriately");

        // Since `inferred` comes out as Nodelist<Object<category: 'fiction' & String, ...>>, we can test that the intersection happened
        // The implementation returned `Object<category: 'fiction' & String, ...>` when applying the `intersect` method.
        // If properties were structurally preserved via intersection they would appear. The API handles intersection of objects
        // by intersecting common parameters and unioning spread, or keeping spread.
        // Here, inferred is: Nodelist<Object<category: 'fiction' & String, ...>>
    }

    @Test
    public void testDeepScan() {
        Type schema = system.expression("Object<book: Object<id: String>, author: Object<id: Number>>");
        Type expected = system.expression("Nodelist<String | Number>");

        Type inferred = checker.infer(schema, "$..id");
        assertTrue(expected.isAssignableFrom(inferred), "Deep scan should union all 'id' properties");
    }

    @Test
    public void testDefinitePathNoWrapper() {
        Type schema = system.expression("Object<name: String>");
        Type inferred = checker.infer(schema, "$.name");

        Type expected = system.expression("String");
        assertTrue(expected.isAssignableFrom(inferred), "Definite path without option enabled should not wrap in Nodelist");
    }

    @Test
    public void testDefinitePathWithWrapper() {
        JsonPathCovenantChecker configuredChecker = new JsonPathCovenantChecker(true);
        Type schema = system.expression("Object<name: String>");
        Type inferred = configuredChecker.infer(schema, "$.name");

        Type expected = system.expression("Nodelist<String>");
        assertTrue(expected.isAssignableFrom(inferred), "Definite path with option enabled should wrap in Nodelist");
    }
}
