package dev.akre.covenant.jsonpath;

import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.TypeSystemBuilderImpl;
import dev.akre.covenant.types.JsonTypeSystem;

public class JsonPathTypeSystem {
    public static final AbstractTypeSystem INSTANCE = new TypeSystemBuilderImpl(JsonTypeSystem.INSTANCE)
            // Nodelist generic wrapper
            .atom("Nodelist")
            .arrayPattern()

            // Relational and equality operators (mapping directly from RFC 9535 filters)
            .functionAlias("eq", "<T>(T, T) -> Bool")
            .functionAlias("neq", "<T>(T, T) -> Bool")
            .functionAlias("gt", "(Number, Number) -> Bool", "(String, String) -> Bool")
            .functionAlias("gte", "(Number, Number) -> Bool", "(String, String) -> Bool")
            .functionAlias("lt", "(Number, Number) -> Bool", "(String, String) -> Bool")
            .functionAlias("lte", "(Number, Number) -> Bool", "(String, String) -> Bool")

            // Logical
            .functionAlias("and", "(Bool, Bool) -> Bool")
            .functionAlias("or", "(Bool, Bool) -> Bool")
            .functionAlias("not", "(Bool) -> Bool")

            .build();
}
