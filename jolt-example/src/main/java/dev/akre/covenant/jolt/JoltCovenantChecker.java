package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.json.JsonReadFeature;

import java.util.*;

public class JoltCovenantChecker {
    private final AbstractTypeSystem typeSystem;
    private final ObjectMapper mapper;

    public JoltCovenantChecker() {
        this.typeSystem = JoltTypeSystem.INSTANCE;
        this.mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build();
    }

    public Type infer(Type inputSchema, String joltSpecJson) throws Exception {
        JsonNode specArray = mapper.readTree(joltSpecJson);
        if (!specArray.isArray()) {
            throw new IllegalArgumentException("Jolt spec must be a JSON array");
        }

        Type currentType = inputSchema;

        for (JsonNode operationNode : specArray) {
            String operation = operationNode.path("operation").asText();
            JsonNode spec = operationNode.path("spec");

            currentType = applyOperation(operation, spec, currentType);
        }

        return currentType;
    }

    private Type applyOperation(String operation, JsonNode spec, Type currentType) {
        if ("shift".equals(operation)) {
            return applyShift(spec, currentType);
        } else if ("default".equals(operation)) {
            return applyDefault(spec, currentType);
        } else {
            return currentType;
        }
    }

    private Type applyShift(JsonNode spec, Type currentType) {
        // High level strategy for shift:
        // Build an intermediate tree where each path is populated by matching the input type.
        Map<String, Object> outputTree = new HashMap<>();
        traverseShiftSpec(spec, currentType, outputTree, new ArrayList<>());
        return buildTypeFromTree(outputTree);
    }

    private void traverseShiftSpec(JsonNode specNode, Type currentType, Map<String, Object> outputTree, List<String> currentPathTokens) {
        if (currentType.equals(typeSystem.bottom())) return;

        if (specNode.isTextual()) {
            // Leaf level of shift spec, assigns output path
            String outputPathRaw = specNode.asText();
            assignToOutputTree(outputTree, outputPathRaw, currentType, currentPathTokens);
        } else if (specNode.isArray()) {
            // Array of output paths
            for (JsonNode pathNode : specNode) {
                if (pathNode.isTextual()) {
                    assignToOutputTree(outputTree, pathNode.asText(), currentType, currentPathTokens);
                }
            }
        } else if (specNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = specNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode childSpec = entry.getValue();

                if (key.equals("*")) {
                    Type childType = extractAllProperties(currentType);
                    traverseShiftSpec(childSpec, childType, outputTree, currentPathTokens); // ignoring specific path token recording for wildcards for now in simplified impl
                } else if (key.contains("&")) {
                    // Simplified reference handling: just use the parent type (assuming typical map-to-list pattern)
                     Type childType = extractAllProperties(currentType);
                     traverseShiftSpec(childSpec, childType, outputTree, currentPathTokens);
                } else if (key.equals("$")) {
                    // Outputting the key itself
                    Type stringType = typeSystem.expression("String");
                    traverseShiftSpec(childSpec, stringType, outputTree, currentPathTokens);
                }
                else {
                    Type childType = extractProperty(currentType, key);
                    List<String> newTokens = new ArrayList<>(currentPathTokens);
                    newTokens.add(key);
                    traverseShiftSpec(childSpec, childType, outputTree, newTokens);
                }
            }
        }
    }

    private void assignToOutputTree(Map<String, Object> outputTree, String outputPathRaw, Type valueType, List<String> currentPathTokens) {
        // Resolve path references like "rating-&" or "&1"
        String resolvedPath = resolvePath(outputPathRaw, currentPathTokens);
        if (resolvedPath == null || resolvedPath.isEmpty()) return;

        String[] parts = resolvedPath.split("\\.");
        Map<String, Object> currentMap = outputTree;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            currentMap.putIfAbsent(part, new HashMap<String, Object>());
            Object next = currentMap.get(part);
            if (next instanceof Map) {
                currentMap = (Map<String, Object>) next;
            } else {
                // Collision, ignore for now in simplified impl
                return;
            }
        }

        String lastPart = parts[parts.length - 1];
        if (currentMap.containsKey(lastPart)) {
            Object existing = currentMap.get(lastPart);
            if (existing instanceof Type existingType) {
                currentMap.put(lastPart, existingType.union(valueType));
            }
        } else {
            currentMap.put(lastPart, valueType);
        }
    }

    private String resolvePath(String rawPath, List<String> tokens) {
        String resolved = rawPath;
        if (resolved.contains("&")) {
            // Simplified substitution: just replace & with the last matched token
            String tokenToUse = tokens.isEmpty() ? "Unknown" : tokens.get(tokens.size() - 1);
            resolved = resolved.replace("&", tokenToUse);
        }
        return resolved;
    }

    private Type applyDefault(JsonNode spec, Type currentType) {
        if (currentType.equals(typeSystem.bottom())) return currentType;
        if (!spec.isObject()) return currentType;

        return mergeDefaultTree(spec, currentType);
    }

    private Type mergeDefaultTree(JsonNode spec, Type currentType) {
        TypeDef rawObj = ((OwnedTypeDef) currentType).def();

        if (rawObj instanceof GenericTypeDef gen && gen.template().name().equals("Object")) {
            List<TypeDefParam> newParams = new ArrayList<>(gen.parameters());
            Iterator<Map.Entry<String, JsonNode>> fields = spec.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode val = entry.getValue();

                boolean found = false;
                for (int i = 0; i < newParams.size(); i++) {
                    TypeDefParam p = newParams.get(i);
                    if (p instanceof TypeDefParam.Named named && named.name().equals(key)) {
                        found = true;
                        if (val.isObject()) {
                            Type mergedChild = mergeDefaultTree(val, typeSystem.wrap(named.type()));
                            newParams.set(i, new TypeDefParam.Named(((OwnedTypeDef) mergedChild).def(), key, false));
                        }
                        break;
                    }
                }

                if (!found) {
                    Type defaultType = jsonNodeToType(val);
                    newParams.add(new TypeDefParam.Named(((OwnedTypeDef) defaultType).def(), key, false));
                }
            }
            return typeSystem.wrap(new GenericTypeDef(gen.template(), gen.pattern(), newParams));
        } else if (rawObj instanceof UnionType union) {
            Type result = typeSystem.bottom();
            for (TypeDef member : union.members()) {
                result = result.union(mergeDefaultTree(spec, typeSystem.wrap(member)));
            }
            return result;
        }

        // Fallback: If currentType is not an Object or Union of Objects, just wrap the default spec into an Object Type and return their intersection or just default type.
        // For simplicity, we just return the object type generated from spec if currentType is top.
        if (currentType.equals(typeSystem.top())) {
           return jsonNodeToType(spec);
        }

        return currentType;
    }


    private Type buildTypeFromTree(Map<String, Object> tree) {
        if (tree.isEmpty()) return typeSystem.expression("Object"); // Empty object

        StringBuilder sb = new StringBuilder("Object<");
        boolean first = true;
        for (Map.Entry<String, Object> entry : tree.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append(": ");
            if (entry.getValue() instanceof Type t) {
                sb.append(t.repr());
            } else if (entry.getValue() instanceof Map m) {
                sb.append(buildTypeFromTree(m).repr());
            }
            first = false;
        }
        sb.append(">");
        return typeSystem.expression(sb.toString());
    }

    private Type jsonNodeToType(JsonNode node) {
        if (node.isNumber()) return typeSystem.expression("Number");
        if (node.isTextual()) {
            return typeSystem.expression("'" + node.asText() + "'");
        }
        if (node.isBoolean()) return typeSystem.expression("Bool");
        if (node.isNull()) return typeSystem.expression("Null");
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder("Object<");
            boolean first = true;
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append(": ").append(jsonNodeToType(entry.getValue()).repr());
                first = false;
            }
            sb.append(">");
            return typeSystem.expression(sb.toString());
        }
        if (node.isArray()) {
             if (node.isEmpty()) return typeSystem.expression("Array");
             Type unionType = typeSystem.bottom();
             for (JsonNode child : node) {
                 unionType = unionType.union(jsonNodeToType(child));
             }
             return typeSystem.expression("Array<" + unionType.repr() + ">");
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
            Type unionResult = typeSystem.bottom();
            for (TypeDef member : union.members()) {
                Type extracted = extractProperty(typeSystem.wrap(member), propertyName);
                unionResult = unionResult.union(extracted);
            }
            return unionResult;
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
            } else if (gen.template().name().equals("Array")) {
                Type unionType = typeSystem.bottom();
                for (TypeDefParam param : gen.parameters()) {
                     if (param instanceof TypeDefParam.Positional pos && pos.index() == 0) {
                        return typeSystem.wrap(param.type());
                    }
                }
                if (gen.spreadParam() != null) {
                    return typeSystem.wrap(gen.spreadParam());
                }
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
