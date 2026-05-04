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
        return inferShift(inputSchema, specNode);
    }

    private Type inferShift(Type currentType, JsonNode specNode) {
        if (specNode.isTextual()) {
            String outPath = specNode.asText();
            String[] parts = outPath.split("\\.");
            Type resultConstraint = currentType;
            for (int i = parts.length - 1; i >= 0; i--) {
                String part = parts[i];
                if (part.equals("&")) {
                    // For "&", we just return the current type
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

                Type extractedType = extractProperty(currentType, key);
                Type constraint = inferShift(extractedType, childNode);
                aggregated = aggregated.intersect(constraint);
            }
            return aggregated;
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
