package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.GenericTypeDef;
import dev.akre.covenant.types.OwnedTypeDef;
import dev.akre.covenant.api.Parameter;
import dev.akre.covenant.types.TypeDef;
import dev.akre.covenant.types.TypeDefParam;
import dev.akre.covenant.types.UnionType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JoltCovenantChecker {
    private final AbstractTypeSystem typeSystem;
    private final ObjectMapper mapper;

    public JoltCovenantChecker(AbstractTypeSystem typeSystem) {
        this.typeSystem = typeSystem;
        this.mapper = new ObjectMapper();
    }

    public JoltCovenantChecker() {
        this(JoltTypeSystem.INSTANCE);
    }

    public Type infer(Type inputSchema, String joltSpecJson) throws Exception {
        JsonNode specNode = mapper.readTree(joltSpecJson);
        Type inferred = inputSchema;
        if (specNode.isArray()) {
            for (JsonNode opNode : specNode) {
                String op = opNode.get("operation").asText();
                JsonNode spec = opNode.get("spec");
                if ("shift".equals(op)) {
                    inferred = inferShift(inferred, spec, new ArrayList<>());
                } else if ("default".equals(op)) {
                    inferred = inferDefault(inferred, spec);
                }
            }
        } else if (specNode.isObject()) {
            inferred = inferShift(inferred, specNode, new ArrayList<>());
        }
        return inferred;
    }

    private Type inferShift(Type currentType, JsonNode specNode, List<String> matchedKeys) {
        if (specNode.isTextual()) {
            String outPath = specNode.asText();
            // Handle &1 substitution
            if (outPath.contains("&1") && matchedKeys.size() >= 1) {
                outPath = outPath.replace("&1", matchedKeys.get(matchedKeys.size() - 1));
            }
            String[] parts = outPath.split("\\.");
            Type resultConstraint = currentType;
            for (int i = parts.length - 1; i >= 0; i--) {
                String part = parts[i];
                if (part.equals("&")) {
                    resultConstraint = currentType;
                } else {
                    resultConstraint = typeSystem.expression("Object<" + part + ": " + resultConstraint.repr() + ", ...>");
                }
            }
            return resultConstraint;
        } else if (specNode.isObject()) {
            Type aggregated = typeSystem.top();
            Iterator<Map.Entry<String, JsonNode>> fields = specNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode childNode = field.getValue();

                if (key.equals("$")) {
                    String outPath = childNode.asText();
                    if (outPath.contains("&1") && matchedKeys.size() >= 1) {
                        outPath = outPath.replace("&1", matchedKeys.get(matchedKeys.size() - 1));
                    }
                    String[] parts = outPath.split("\\.");
                    Type resultConstraint = typeSystem.expression("String");
                    for (int i = parts.length - 1; i >= 0; i--) {
                        String part = parts[i];
                        resultConstraint = typeSystem.expression("Object<" + part + ": " + resultConstraint.repr() + ", ...>");
                    }
                    aggregated = aggregated.intersect(resultConstraint);
                    continue;
                }

                if (key.equals("*")) {
                    List<String> knownKeys = getKnownPropertyNames(currentType);
                    // For wildcard we want to simulate applying this to all additional properties
                    // In a perfect system we would extract the spread param.
                    // For simplicity in this example, we apply it to String to represent the matched key.
                    // Actually, let's extract from spread param
                    Type extractedType = extractSpread(currentType);
                    List<String> newMatched = new ArrayList<>(matchedKeys);
                    newMatched.add("*"); // placeholder for generic match
                    Type constraint = inferShift(extractedType, childNode, newMatched);
                    // Replace * in the resulting constraint with a regex match for object
                    String repr = constraint.repr().replace("*", "matches \".*\"");
                    aggregated = aggregated.intersect(typeSystem.expression(repr));
                } else {
                    Type extractedType = extractProperty(currentType, key);
                    List<String> newMatched = new ArrayList<>(matchedKeys);
                    newMatched.add(key);
                    Type constraint = inferShift(extractedType, childNode, newMatched);
                    aggregated = aggregated.intersect(constraint);
                }
            }
            return aggregated;
        }
        return typeSystem.top();
    }

    private Type inferDefault(Type currentType, JsonNode specNode) {
        if (specNode.isObject()) {
            Type resultConstraint = currentType;
            Iterator<Map.Entry<String, JsonNode>> fields = specNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode childNode = field.getValue();

                if (key.equals("*")) {
                    // For now, simplify wildcard defaults
                    continue;
                }

                if (childNode.isObject()) {
                     Type extracted = extractProperty(currentType, key);
                     Type defType = inferDefault(extracted, childNode);
                     Type nested = typeSystem.expression("Object<" + key + ": " + defType.repr() + ", ...>");
                     resultConstraint = resultConstraint.intersect(nested);
                } else if (childNode.isNumber()) {
                     Type nested = typeSystem.expression("Object<" + key + ": Number, ...>");
                     resultConstraint = resultConstraint.intersect(nested);
                } else if (childNode.isTextual()) {
                     Type nested = typeSystem.expression("Object<" + key + ": String, ...>");
                     resultConstraint = resultConstraint.intersect(nested);
                }
            }
            return resultConstraint;
        }
        return currentType;
    }

    private List<String> getKnownPropertyNames(Type objType) {
        List<String> names = new ArrayList<>();
        if (objType.equals(typeSystem.top()) || objType.equals(typeSystem.bottom())) {
            return names;
        }
        TypeDef rawObj = ((OwnedTypeDef) objType).def();
        if (rawObj instanceof GenericTypeDef gen) {
            for (TypeDefParam param : gen.parameters()) {
                if (param.parameter() instanceof Parameter.Named named) {
                    names.add(named.name());
                }
            }
        } else if (rawObj instanceof UnionType union) {
            for (TypeDef member : union.members()) {
                names.addAll(getKnownPropertyNames(typeSystem.wrap(member)));
            }
        }
        return names;
    }

    private Type extractSpread(Type objType) {
        if (objType.equals(typeSystem.top()) || objType.equals(typeSystem.bottom())) {
            return typeSystem.top();
        }
        TypeDef rawObj = ((OwnedTypeDef) objType).def();
        if (rawObj instanceof GenericTypeDef gen) {
            if (gen.spreadParam() != null) {
                return typeSystem.wrap(gen.spreadParam());
            }
        } else if (rawObj instanceof UnionType union) {
            Type res = typeSystem.bottom();
            for (TypeDef member : union.members()) {
                res = res.union(extractSpread(typeSystem.wrap(member)));
            }
            return res;
        }
        return typeSystem.top();
    }

    private Type extractProperty(Type objType, String propertyName) {
        if (objType.equals(typeSystem.top()) || objType.equals(typeSystem.bottom())) {
            return objType;
        }

        TypeDef rawObj = ((OwnedTypeDef) objType).def();

        if (rawObj instanceof GenericTypeDef gen) {
            for (TypeDefParam param : gen.parameters()) {
                if (param.parameter() instanceof Parameter.Named named && named.name().equals(propertyName)) {
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
}
