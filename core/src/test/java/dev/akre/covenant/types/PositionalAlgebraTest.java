package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class PositionalAlgebraTest {
    public static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testPositionalIntersection() {
        // 1. Same length, compatible types
        SYSTEM.assertThat("Array<Number, String> & Array<Int, Any>")
                .isEquivalentTo("Array<Int, String>");

        // 2. Same length, incompatible types at index
        SYSTEM.assertThat("Array<Int, String> & Array<String, Int>")
                .isBottom();

        // 3. Different lengths (fixed)
        // A 1-element array cannot be a 2-element array
        SYSTEM.assertThat("Array<Int> & Array<Int, String>")
                .isBottom();
    }

    @Test
    public void testPositionalVariadicIntersection() {
        // 1. Fixed length intersecting with broad variadic
        SYSTEM.assertThat("Array<Int, Int> & Array<Number...>")
                .isEquivalentTo("Array<Int, Int>");

        // 2. Fixed length intersecting with narrower variadic
        SYSTEM.assertThat("Array<Number, Number> & Array<Int...>")
                .isEquivalentTo("Array<Int, Int>");

        // 3. Incompatible types in variadic
        SYSTEM.assertThat("Array<Int, String> & Array<Int...>")
                .isBottom();

        // 4. Overlapping variadics
        SYSTEM.assertThat("Array<Int, Number...> & Array<Number, Int...>")
                .isEquivalentTo("Array<Int, Int...>");
    }

    @Test
    public void testPositionalUnion() {
        // 1. Same structure, common types
        SYSTEM.assertThat("Array<Int, String> | Array<Int, Bool>")
                .isEquivalentTo("Array<Int, (String | Bool)>");

        // 2. Different lengths - should probably remain a union
        SYSTEM.assertThat("Array<Int> | Array<Int, String>")
                .isEquivalentTo("Array<Int> | Array<Int, String>");
    }

    @Test
    public void testTupleLikeBehavior() {
        // Intersecting tuples with shared interfaces/parents
        // Assuming 'Number' is a parent of 'Int' and 'Float'
        SYSTEM.assertThat("Array<Int, Float> & Array<Number, Number>")
                .isEquivalentTo("Array<Int, Float>");
    }
}
