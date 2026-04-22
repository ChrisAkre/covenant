package dev.akre.covenant.types;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ArrayTypesTest {

    @Test
    public void testArrayBasics() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        system.assertThat("Array").satisfies("Array");
        system.assertThat("Array<Int, Int>").satisfies("Array");
        system.assertThat("Array").notSatisfies("Array<Int>");
    }

    @Test
    public void testIntFloat() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);
        system.assertThat("Int").satisfies("Float");
    }

    @Test
    public void testArrayCovariance() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        system.assertThat("Array<Int>").satisfies("Array<Float>");
        system.assertThat("Array<Float>").notSatisfies("Array<Int>");
    }

    @Test
    public void testArrayTermNavigation() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        system.assertThat("Array<Int, String>").term(0).satisfies("Int");
        system.assertThat("Array<Int, String>:0").satisfies("Int");

        system.assertThat("Array<Int, String>").term(1).satisfies("String");
        system.assertThat("Array<Int, String>").term(2).isBottom();
        system.assertThat("Array<Int, String>:2").isBottom();

        // Variadic elements are optional (T?) because they might not exist
        system.assertThat("Array<Int...>").term(0).satisfies("Int?");
        system.assertThat("Array<Int...>").term(0).notSatisfies("Int");

        // Mixed prefix and variadic
        system.assertThat("Array<String, Int...>").term(0).satisfies("String");
        system.assertThat("Array<String, Int...>").term(1).satisfies("Int?");

        // Algebraic
        system.assertThat("Array<Int> | Array<String>").term(0).isEquivalentTo("Int | String");
        system.assertThat("Array<Int> & Array<Float>").term(0).isEquivalentTo("Int"); // Int satisfies Float
    }

    @Test
    public void testVariadic() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        system.assertThat("Array<Bool...>").satisfies("Array<Bool?, Bool...>");
        system.assertThat("Array<Any...>").satisfies("Array<Any, Any...>");
        system.assertThat("Array<Bool...>").satisfies("Array<Any, Any...>");
    }

    @Test
    public void testArrayConcatenation() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        // Fixed + Fixed
        system.assertThat("Array<Int>").concat("Array<String>").isEquivalentTo("Array<Int, String>");
        system.assertThat("Array<Int, Bool>")
                .concat("Array<String, Float>")
                .isEquivalentTo("Array<Int, Bool, String, Float>");

        // Empty + Fixed
        system.assertThat("Array<>").concat("Array<Int>").isEquivalentTo("Array<Int>");
        system.assertThat("Array<Int>").concat("Array<>").isEquivalentTo("Array<Int>");

        // Left Variadic + Fixed
        system.assertThat("Array<Int...>").concat("Array<String>").isEquivalentTo("Array<(Int | String)...>");
        system.assertThat("Array<Int, String...>")
                .concat("Array<Bool>")
                .isEquivalentTo("Array<Int, (String | Bool)...>");

        // Fixed + Right Variadic
        system.assertThat("Array<Int>").concat("Array<String...>").isEquivalentTo("Array<Int, String...>");

        // Variadic + Variadic
        system.assertThat("Array<Int...>").concat("Array<String...>").isEquivalentTo("Array<(Int | String)...>");

        // Complex Absorption
        system.assertThat("Array<Int, String...>")
                .concat("Array<Bool, Float>")
                .isEquivalentTo("Array<Int, (String | Bool | Float)...>");
    }
}
