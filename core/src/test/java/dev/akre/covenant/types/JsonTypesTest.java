package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class JsonTypesTest {

    public static final TestTypeSystem JSON_WITH_FUNCTIONS = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
            .functionAlias("null_coalesce", "(T1, T2) -> (T1&~Null)|T2")
            // Math
            .functionAlias("add", "<T: Float | Int>(T, T) -> T")
            .functionAlias("sub", "<T: Float | Int>(T, T) -> T")
            .functionAlias("mul", "<T: Float | Int>(T, T) -> T")
            .functionAlias("div", "(Float, Float) -> Float")
            .functionAlias("abs", "<T: Float | Int>(T) -> T")

            // String
            .functionAlias(
                    "concat",
                    "((String, String) -> String)",
                    "(<T>(Array<T...>, Array<T...>) -> Array<T...>)",
                    "((Object<...>, Object<...>) -> Object<...>)")
            .functionAlias("upper", "(String) -> String")
            .functionAlias("lower", "(String) -> String")
            .functionAlias("substring", "(String, Int, Int) -> String")

            // Logic
            .functionAlias("and", "(Bool, Bool) -> Bool")
            .functionAlias("or", "(Bool, Bool) -> Bool")
            .functionAlias("not", "(Bool) -> Bool")

            // Navigation
            .functionAlias(
                    "get",
                    "(<O: Object, K: Symbol>(O, K) -> O:K)",
                    "(<O: Object, S: String>(O, S) -> O:S)",
                    "(Null, Any) -> Null")
            .functionAlias("at", "<A: Array, I: Int>(A, I) -> A:I")
            .build();

    @Test
    public void testMathFunctions() {
        TestTypeSystem system = JSON_WITH_FUNCTIONS;

        // add: <T: Float | Int>(T, T) -> T
        system.assertThat("add").withArgs("Int", "Int").evaluatesTo("Int");
        system.assertThat("add").withArgs("Float", "Float").evaluatesTo("Float");
        system.assertThat("add").withArgs("Float", "Int").evaluatesTo("Float");
        system.assertThat("add").withArgs("Int", "Float").evaluatesTo("Float");

        // div: (Float, Float) -> Float
        system.assertThat("div").withArgs("Float", "Float").evaluatesTo("Float");
        system.assertThat("div").withArgs("Int", "Int").evaluatesTo("Float");

        // abs: <T: Float | Int>(T) -> T
        system.assertThat("abs").withArgs("Int").evaluatesTo("Int");
        system.assertThat("abs").withArgs("Float").evaluatesTo("Float");
    }

    @Test
    public void testStringFunctions() {
        TestTypeSystem system = JSON_WITH_FUNCTIONS;

        // concat: (String, String) -> String & <T>(Array<T...>, Array<T...>) -> Array<T...> & (Object<...>,
        // Object<...>) -> Object<...>
        system.assertThat("concat").withArgs("String", "String").evaluatesTo("String");
        system.assertThat("concat").withArgs("Array<Int...>", "Array<Int...>").evaluatesTo("Array<Int...>");
        system.assertThat("concat").withArgs("Object<...>", "Object<...>").evaluatesTo("Object<...>");

        system.assertThat("upper").withArgs("String").evaluatesTo("String");
        system.assertThat("lower").withArgs("String").evaluatesTo("String");
        system.assertThat("substring").withArgs("String", "Int", "Int").evaluatesTo("String");
    }

    @Test
    public void testLogicFunctions() {
        TestTypeSystem system = JSON_WITH_FUNCTIONS;

        system.assertThat("null_coalesce").withArgs("Bool", "Bool").evaluatesTo("Bool");

        system.assertThat("and").withArgs("Bool", "Bool").evaluatesTo("Bool");
        system.assertThat("or").withArgs("Bool", "Bool").evaluatesTo("Bool");
        system.assertThat("not").withArgs("Bool").evaluatesTo("Bool");
    }
}
