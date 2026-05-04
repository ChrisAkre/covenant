package dev.akre.covenant.example;

import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.TypeSystemBuilderImpl;
import dev.akre.covenant.types.JsonTypeSystem;

public class JavaSubscriptTypeSystem {
    public static final AbstractTypeSystem INSTANCE = new TypeSystemBuilderImpl(JsonTypeSystem.INSTANCE)
            // Relational / Equality Operators
            .functionAlias("strictEq", "<T>(T, T) -> Bool")
            .functionAlias("gt", "(Number, Number) -> Bool", "(String, String) -> Bool")
            .functionAlias("gte", "(Number, Number) -> Bool", "(String, String) -> Bool")
            .functionAlias("lt", "(Number, Number) -> Bool", "(String, String) -> Bool")
            .functionAlias("lte", "(Number, Number) -> Bool", "(String, String) -> Bool")

            // Arithmetic Operators
            .functionAlias("plus", "(Number, Number) -> Number", "(String, String) -> String", "(String, Any) -> String", "(Any, String) -> String")
            .functionAlias("minus", "(Number, Number) -> Number")
            .functionAlias("times", "(Number, Number) -> Number")
            .functionAlias("divide", "(Number, Number) -> Number")

            // Logical Operators
            .functionAlias("and", "(Bool, Bool) -> Bool")
            .functionAlias("or", "(Bool, Bool) -> Bool")
            .functionAlias("not", "(Bool) -> Bool")

            .build();
}
