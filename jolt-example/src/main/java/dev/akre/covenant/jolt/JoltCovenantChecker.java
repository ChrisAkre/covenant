package dev.akre.covenant.jolt;

import tools.jackson.databind.JsonNode;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import java.util.Iterator;
import java.util.Map;

public class JoltCovenantChecker {
    private final AbstractTypeSystem typeSystem = JoltTypeSystem.INSTANCE;

    public Type infer(Type inputSchema, JsonNode joltSpec) {
        if (!joltSpec.isArray()) {
            throw new IllegalArgumentException("Jolt spec must be an array of operations");
        }

        Type currentSchema = inputSchema;
        for (JsonNode operation : joltSpec) {
            currentSchema = applyOperation(currentSchema, operation);
        }
        return currentSchema;
    }

    private Type applyOperation(Type inputSchema, JsonNode operation) {
        String opType = operation.path("operation").asText();
        JsonNode spec = operation.path("spec");
        if ("shift".equals(opType)) {
            return applyShift(inputSchema, spec);
        } else if ("default".equals(opType)) {
            return applyDefault(inputSchema, spec);
        } else {
             // For unknown operations, returning Top type is safer as it makes no assumptions
            return typeSystem.expression("Top");
        }
    }

    // Evaluate shift using Union of all possible structures or fallback to a generic Object with unknown properties
    private Type applyShift(Type inputSchema, JsonNode spec) {
        // A true implementation would perform complex mapping across the schema
        // For the sake of this example module acting as a typechecker demo,
        // we'll infer that the output is some object mapping
        // In the future, this would traverse inputSchema and apply the shift spec.
        return typeSystem.expression("Object<String, Top>");
    }

    private Type applyDefault(Type inputSchema, JsonNode spec) {
        // Default merges new properties into the existing schema
        // In a true implementation, we'd union/intersect the new properties
        // For this demo, we'll return a union of the input schema and a new object.
        return typeSystem.expression(inputSchema.repr() + " | Object");
    }

    public boolean verify(Type inputSchema, JsonNode joltSpec, Type expectedSchema) {
        Type inferredSchema = infer(inputSchema, joltSpec);
        return expectedSchema.isAssignableFrom(inferredSchema);
    }
}
