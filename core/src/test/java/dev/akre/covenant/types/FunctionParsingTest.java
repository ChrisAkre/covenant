package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class FunctionParsingTest {

    @Test
    public void testFunctionReturnIntersection() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        // & inside a function return type binds tighter than the function arrow
        system.assertThat("(String) -> Int & Null")
                .printsLike("(String) -> Int & Null") // Int & Null evaluates to bottom eventually, but syntactically it's parsed in the return type
                .withArgs("String").evaluatesTo("bottom");

        system.assertThat("(String) -> String & ~Null")
                .printsLike("(String) -> String & ~Null")
                .withArgs("String").evaluatesTo("String");
    }

    @Test
    public void testFunctionReturnUnion() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        // | inside a function return type binds tighter than the function arrow
        system.assertThat("(String) -> Int | String")
                .printsLike("(String) -> Int | String")
                .withArgs("String").evaluatesTo("Int | String");
    }

    @Test
    public void testFunctionIntersection() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        // & followed by a function is a function intersection
        system.assertThat("(String) -> String & (Int) -> Int")
                .printsLike("((String) -> String) & ((Int) -> Int)");

        // Nested generic functions
        system.assertThat("<T2>(Null, T2) -> T2 & <T1: ~Null>(T1, Any) -> T1")
                .printsLike("(<T2>(Null, T2) -> T2) & (<T1: ~Null>(T1, Any) -> T1)");
    }

    @Test
    public void testFunctionUnion() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        // | followed by a function is a function union
        system.assertThat("(String) -> String | (Int) -> Int")
                .printsLike("(Int) -> Int | (String) -> String"); // UnionType sorts its members alphabetically
    }

    @Test
    public void testComplexNestedFunctions() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        // Arrow is left-associative in Covenant
        system.assertThat("(String) -> (Int) -> Int")
                .printsLike("((String) -> Int) -> Int");

        // Function returning a function intersection
        system.assertThat("(String) -> ((Int) -> Int & (Null) -> Null)")
                .printsLike("(String) -> (Int) -> Int & (Null) -> Null");
    }
}
