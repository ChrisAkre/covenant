package dev.akre.covenant.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JsCovenantCheckerTest {

    public static final String CATEGORY_FUNCTION = """
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

    public static final String USER_SCHEMA = """
        {
            "type": "object",
            "properties": {
                "age": {"type": "integer"},
                "status": {"enum": ["active", "inactive"]}
            },
            "required": ["age", "status"]
        }
        """;

    public static final String STRING_SCHEMA = """
        {
            "type": "string"
        }
        """;

    public static final String NESTED_USER_SCHEMA = """
        {
            "type": "object",
            "properties": {
                "profile": {
                    "type": "object",
                    "properties": {
                        "status": {"enum": ["active", "inactive"]},
                        "accessLevel": {"type": "integer"}
                    },
                    "required": ["status", "accessLevel"]
                }
            },
            "required": ["profile"]
        }
        """;

    public static final String NESTED_VALID_FUNCTION = """
            (user) => {
                if (user.profile.status === "inactive") {
                    return "Archived Account";
                }
                return "Active Account";
            }
        """;

    public static final String NESTED_INVALID_FUNCTION = """
            (user) => {
                if (user.profile.status === "inactive") {
                    return "Archived Account";
                }
                // Bug: Returning an integer when the contract expects a String
                return user.profile.accessLevel;
            }
        """;

    @Test
    public void testValidContract() {
        JsCovenantChecker checker = new JsCovenantChecker();
        assertTrue(checker.verify(CATEGORY_FUNCTION, USER_SCHEMA, STRING_SCHEMA),
                "The JavaScript function should satisfy the String output schema.");
    }

    @Test
    public void testInvalidContractReturnsNumber() {
        String getAge = """
            (user) => {
                return user.age;
            }
        """;

        JsCovenantChecker checker = new JsCovenantChecker();
        assertFalse(checker.verify(getAge, USER_SCHEMA, STRING_SCHEMA),
                "The JavaScript function returning a Number should NOT satisfy the String output schema.");
    }

    @Test
    public void testValidNestedPathNarrowing() {
        JsCovenantChecker checker = new JsCovenantChecker();
        assertTrue(checker.verify(NESTED_VALID_FUNCTION, NESTED_USER_SCHEMA, STRING_SCHEMA),
                "The JavaScript function should successfully evaluate the nested path and satisfy the String output schema.");
    }

    @Test
    public void testInvalidNestedPathReturnsNumber() {
        JsCovenantChecker checker = new JsCovenantChecker();
        assertFalse(checker.verify(NESTED_INVALID_FUNCTION, NESTED_USER_SCHEMA, STRING_SCHEMA),
                "The JavaScript function returning the nested accessLevel (Number) should NOT satisfy the String output schema.");
    }
}
