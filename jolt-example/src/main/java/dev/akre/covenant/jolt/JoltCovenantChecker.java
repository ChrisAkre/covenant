package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.JsonTypeSystem;
import dev.akre.covenant.types.OwnedTypeDef;
import dev.akre.covenant.types.GenericTypeDef;
import dev.akre.covenant.types.UnionType;
import dev.akre.covenant.types.TypeDef;
import dev.akre.covenant.types.TypeDefParam;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

public class JoltCovenantChecker {
    private final AbstractTypeSystem typeSystem;
    private final ObjectMapper mapper;

    public JoltCovenantChecker() {
        this.typeSystem = JoltTypeSystem.INSTANCE;
        this.mapper = new ObjectMapper();
    }

    public Type infer(Type inputSchema, String joltSpecJson) throws Exception {
        JsonNode specs = mapper.readTree(joltSpecJson);
        Type currentSchema = inputSchema;

        if (specs.isArray()) {
            for (JsonNode specNode : specs) {
                String operation = specNode.path("operation").asText();
                JsonNode specData = specNode.path("spec");

                if ("shift".equals(operation)) {
                    currentSchema = applyShift(currentSchema, specData);
                } else if ("default".equals(operation)) {
                    currentSchema = applyDefault(currentSchema, specData);
                } else if ("remove".equals(operation)) {
                    currentSchema = applyRemove(currentSchema, specData);
                }
            }
        }

        return currentSchema;
    }

    private Type applyShift(Type schema, JsonNode specNode) {
        Type outputSchema = typeSystem.bottom();
        List<String> currentPath = new ArrayList<>();
        Stack<String> matchedKeys = new Stack<>();

        outputSchema = walkShiftSpec(schema, specNode, currentPath, matchedKeys, outputSchema);
        return outputSchema;
    }

    private Type walkShiftSpec(Type currentInputContext, JsonNode specNode, List<String> currentPath, Stack<String> matchedKeys, Type currentOutputSchema) {
        if (specNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = specNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();

                if (key.equals("*")) {
                    Type extracted = extractAllProperties(currentInputContext);
                    matchedKeys.push("*");
                    currentOutputSchema = currentOutputSchema.union(walkShiftSpec(extracted, value, currentPath, matchedKeys, currentOutputSchema));
                    matchedKeys.pop();
                } else if (key.equals("$")) {
                    String destPath = value.asText();
                    String resolvedDestPath = resolveReferences(destPath, matchedKeys);
                    currentOutputSchema = mergeAtPath(currentOutputSchema, resolvedDestPath, typeSystem.expression("String"));
                } else {
                    Type childType = extractProperty(currentInputContext, key);
                    matchedKeys.push(key);
                    currentPath.add(key);
                    currentOutputSchema = currentOutputSchema.union(walkShiftSpec(childType, value, currentPath, matchedKeys, currentOutputSchema));
                    currentPath.remove(currentPath.size() - 1);
                    matchedKeys.pop();
                }
            }
        } else if (specNode.isValueNode()) {
            String destPath = specNode.asText();
            String resolvedDestPath = resolveReferences(destPath, matchedKeys);
            currentOutputSchema = mergeAtPath(currentOutputSchema, resolvedDestPath, currentInputContext);
        }

        return currentOutputSchema;
    }

    private String resolveReferences(String destPath, Stack<String> matchedKeys) {
        String resolved = destPath;
        for (int i = 1; i <= matchedKeys.size(); i++) {
            String ref = "&" + i;
            if (resolved.contains(ref)) {
                int index = matchedKeys.size() - i;
                if (index >= 0) {
                    resolved = resolved.replace(ref, matchedKeys.get(index));
                }
            }
        }
        return resolved;
    }

    private Type mergeAtPath(Type currentSchema, String path, Type leafType) {
        String[] parts = path.split("\\.");
        Type currentConstraint = leafType;

        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].equals("*")) {
                currentConstraint = typeSystem.expression("Object<..., " + currentConstraint.repr() + ">");
            } else {
                currentConstraint = typeSystem.expression("Object<" + parts[i] + ": " + currentConstraint.repr() + ", ...>");
            }
        }

        if (currentSchema.equals(typeSystem.bottom())) {
            return currentConstraint;
        }

        return currentSchema.intersect(currentConstraint);
    }

    private Type applyDefault(Type schema, JsonNode specNode) {
        Type defaultSchema = buildConstraintFromValue(specNode);
        return schema.intersect(defaultSchema);
    }

    private Type applyRemove(Type schema, JsonNode specNode) {
        return schema;
    }

    private Type buildConstraintFromValue(JsonNode node) {
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder("Object<");
            boolean first = true;
            boolean hasSpread = false;
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!first) sb.append(", ");
                String key = field.getKey();
                if (key.equals("*")) {
                    hasSpread = true;
                    // Fix syntax for spread
                    sb.append("..., ").append(buildConstraintFromValue(field.getValue()).repr());
                } else {
                    sb.append(key).append(": ").append(buildConstraintFromValue(field.getValue()).repr());
                }
                first = false;
            }
            if (!hasSpread) {
                if (!first) sb.append(", ...>");
                else sb.append("...>");
            } else {
                sb.append(">");
            }

            String expr = sb.toString();
            // clean up just in case
            if (expr.contains(", >")) expr = expr.replace(", >", ">");
            if (expr.contains("..., ,")) expr = expr.replace("..., ,", "..., ");

            return typeSystem.expression(expr);
        } else if (node.isNumber()) {
            return typeSystem.expression("Number");
        } else if (node.isTextual()) {
            return typeSystem.expression("String");
        } else if (node.isBoolean()) {
            return typeSystem.expression("Bool");
        }
        return typeSystem.top();
    }

    private Type extractProperty(Type objType, String propertyName) {
        TypeDef rawObj = ((OwnedTypeDef) objType).def();

        if (rawObj instanceof GenericTypeDef gen) {
            for (TypeDefParam param : gen.parameters()) {
                if (param instanceof TypeDefParam.Named named && named.name().equals(propertyName)) {
                    return typeSystem.wrap(param.type());
                }
            }
            if (gen.spreadParam() != null) {
                return typeSystem.wrap(gen.spreadParam());
            }
        } else if (rawObj instanceof UnionType union) {
            List<Type> extracted = new ArrayList<>();
            for (TypeDef member : union.members()) {
                Type extractedFromMember = extractProperty(typeSystem.wrap(member), propertyName);
                if (!extractedFromMember.equals(typeSystem.bottom())) {
                    extracted.add(extractedFromMember);
                }
            }
            if (!extracted.isEmpty()) {
                Type res = extracted.getFirst();
                for (int i = 1; i < extracted.size(); i++) {
                    res = res.union(extracted.get(i));
                }
                return res;
            }
        }
        return typeSystem.bottom();
    }

    private Type extractAllProperties(Type objType) {
        TypeDef rawObj = ((OwnedTypeDef) objType).def();
        if (rawObj instanceof GenericTypeDef gen) {
            if (gen.template().name().equals("Object")) {
                Type unionType = typeSystem.bottom();
                for (TypeDefParam param : gen.parameters()) {
                    unionType = unionType.union(typeSystem.wrap(param.type()));
                }
                if (gen.spreadParam() != null) {
                    unionType = unionType.union(typeSystem.wrap(gen.spreadParam()));
                }
                return unionType;
            }
        } else if (rawObj instanceof UnionType union) {
            Type unionType = typeSystem.bottom();
            for (TypeDef member : union.members()) {
                unionType = unionType.union(extractAllProperties(typeSystem.wrap(member)));
            }
            return unionType;
        }
        return typeSystem.bottom();
    }
}
