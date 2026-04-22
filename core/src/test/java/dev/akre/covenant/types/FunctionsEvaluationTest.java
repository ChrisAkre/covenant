package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class FunctionsEvaluationTest {

    @Test
    public void testIdentity() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("Id", "<T>(T) -> T")
                .build();

        system.assertThat("Id").withArgs("String").evaluatesTo("String");

        system.assertThat("Id").withArgs("Int").evaluatesTo("Int");
    }

    @Test
    public void testNullCoalesceDeep() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("NullCoalesce", "<T1, T2>(T1, T2) -> ((T1 & ~Null) | T2)")
                .build();

        // String? & String -> String
        system.assertThat("NullCoalesce").withArgs("String | Null", "String").evaluatesTo("String");

        // (Int | String | Null) & String -> String | Int
        system.assertThat("NullCoalesce").withArgs("Int | String | Null", "Int").evaluatesTo("Int | String");
    }

    @Test
    public void testArrayElementExtraction() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("First", "<T>(Array<T, Any...>) -> T")
                .build();

        system.assertThat("First").withArgs("Array<Bool...>").evaluatesTo("Bool?");

        system.assertThat("First").withArgs("Array<String, Int>").evaluatesTo("String");

        system.assertThat("First").withArgs("Array<Int, String>").evaluatesTo("Int");
    }

    @Test
    public void testArrayRestExtraction() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("LastElements", "<T>(Array<Any, T...>) -> Array<T...>")
                .build();

        system.assertThat("LastElements").withArgs("Array<String, Int, Int>").evaluatesTo("Array<Int...>");

        system.assertThat("LastElements").withArgs("Int").isBottom();
    }

    @Test
    public void testOverloadedReduce() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("Reduce", "(<T>(Array<T, T...>, (T, T) -> T) -> T) & ((Array<>, Any) -> Null)")
                .build();

        // Non-empty array -> T
        system.assertThat("Reduce")
                .withArgs("Array<Int, Int>", "(Int, Int) -> Int")
                .evaluatesTo("Int");

        // Empty array -> Null
        system.assertThat("Reduce").withArgs("Array<>", "(Any, Any) -> Any").evaluatesTo("Null");
    }

    @Test
    public void testSignatureAsStandaloneType() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        system.assertThat("(Int) -> Int").satisfies("((Int) -> Int)");
        system.assertThat("((Int) -> Int)").notSatisfies("(Int) -> String");

        // Specific signature satisfies general signature
        system.assertThat("(Any) -> Int").satisfies("(Int) -> Any");

        // Signature in intersection
        system.assertThat("((Int) -> Int) & ((String) -> String)").satisfies("(Int) -> Int");
        system.assertThat("((Int) -> Int) & ((String) -> String)").satisfies("(String) -> String");

        // Signature in union
        system.assertThat("(Int) -> Int").satisfies("(Int) -> Int | (String) -> String");
    }

    @Test
    public void testHigherOrderContravariance() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("ApplyCallback", "<T>(T, (T) -> Any) -> T")
                .build();

        // Exact match
        system.assertThat("ApplyCallback").withArgs("String", "(String) -> Any").evaluatesTo("String");

        // Contravariant success: A callback accepting `Any` can safely process a `String`
        system.assertThat("ApplyCallback").withArgs("String", "(Any) -> Any").evaluatesTo("String");

        // Contravariant failure: A callback accepting only `Int` cannot process a `String`.
        // Assuming evaluation failure returns bottom/nil in your DSL.
        system.assertThat("ApplyCallback").withArgs("String", "(Int) -> Any").evaluatesTo("bottom");
    }

    @Test
    public void testAlgebraicUnionSubtraction() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .atom("Error")
                .functionAlias("StripError", "<T>(T | Error) -> T")
                .build();

        // Standard subtraction: (Int | Error) & ~Error => Int
        system.assertThat("StripError").withArgs("Int | Error").evaluatesTo("Int");

        // Complex subtraction: (String | Bool | Error) & ~Error => String | Bool
        system.assertThat("StripError").withArgs("String | Bool | Error").evaluatesTo("String | Bool");

        // Complete subtraction: Error & ~Error => bottom
        system.assertThat("StripError").withArgs("Error").isBottom();
    }

    @Test
    public void testVariadicNullPadding() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("ExtractTwo", "<T1, T2>(Array<T1, T2, Any...>) -> (T1 | T2)")
                .build();

        // Exact positional match
        system.assertThat("ExtractTwo").withArgs("Array<Int, String>").evaluatesTo("Int | String");

        // Partial positional match: T2 must gracefully pad to Null
        system.assertThat("ExtractTwo")
                .withArgs("Array<Int>") // Equivalently Array<Int, bottom...>
                .isBottom();

        // Variadic spread match: T1 is Bool, T2 evaluates the remainder of the spread (Bool | Null)
        system.assertThat("ExtractTwo").withArgs("Array<Bool...>").evaluatesTo("Bool | Null");
    }

    @Test
    public void testBoundedGenerics() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                // Assuming `Number` is a supertype of `Int` and `Float`
                .functionAlias("Clamp", "<T: Number>(T, T, T) -> T")
                .build();

        system.assertThat("Clamp").withArgs("Int", "Int", "Int").evaluatesTo("Int");

        // Evaluates to the union of inferred types, bounded by Number
        system.assertThat("Clamp").withArgs("Int", "Float", "Int").evaluatesTo("Int | Float");

        // Fails generic constraint (String is not a Number)
        system.assertThat("Clamp").withArgs("String", "String", "String").evaluatesTo("bottom");
    }

    @Test
    public void testMultiVariableConvergence() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("Pick", "<T>(T, T) -> T")
                .build();

        // T infers to String | Int
        system.assertThat("Pick").withArgs("String", "Int").evaluatesTo("String | Int");

        // Overlap: T infers to (String | Null) | String => String | Null
        system.assertThat("Pick").withArgs("String | Null", "String").evaluatesTo("String | Null");
    }

    @Test
    public void testUnions() {}
}
