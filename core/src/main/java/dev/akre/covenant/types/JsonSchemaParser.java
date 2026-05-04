package dev.akre.covenant.types;

import dev.akre.covenant.api.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Parser that translates a Jackson 3 JsonNode (JSON Schema) into a TypeDef.
 */
public class JsonSchemaParser {
    private final AbstractTypeSystem system;

    public JsonSchemaParser(AbstractTypeSystem system) {
        JsonTypeSystem.checkContract(system);
        this.system = system;
    }

    public TypeDef parse(JsonNode schema) {
        if (schema.isString()) {
            return system.typeExpressionDef(schema.asString());
        }

        if (schema.has("type")) {
            JsonNode typeNode = schema.get("type");
            if (typeNode.isString()) {
                String typeName = typeNode.asString();
                return switch (typeName) {
                    case "string" ->
                        system.find("String").map(t -> ((OwnedTypeDef) t).def()).orElseThrow();
                    case "number" ->
                        system.find("Number").map(t -> ((OwnedTypeDef) t).def()).orElseThrow();
                    case "integer" ->
                        system.find("Int").map(t -> ((OwnedTypeDef) t).def()).orElseThrow();
                    case "boolean" ->
                        system.find("Bool").map(t -> ((OwnedTypeDef) t).def()).orElseThrow();
                    case "null" ->
                        system.find("Null").map(t -> ((OwnedTypeDef) t).def()).orElseThrow();
                    case "array" -> parseArray(schema);
                    case "object" -> parseObject(schema);
                    default ->
                        system.find(typeName).map(t -> ((OwnedTypeDef) t).def()).orElseThrow();
                };
            }
        }

        if (schema.has("enum")) {
            List<TypeDef> members = new ArrayList<>();
            for (JsonNode member : schema.get("enum")) {
                if (member.isTextual()) {
                    members.add(system.intersectDef(
                            system.find("String")
                                    .map(t -> ((OwnedTypeDef) t).def())
                                    .orElseThrow(),
                            new StringConstraint(ValueConstraint.Operator.EQ, member.asText())));
                } else if (member.isNumber()) {
                    members.add(system.intersectDef(
                            system.find("Number")
                                    .map(t -> ((OwnedTypeDef) t).def())
                                    .orElseThrow(),
                            new NumberConstraint(
                                    ValueConstraint.Operator.EQ, new java.math.BigDecimal(member.asText()))));
                } else if (member.isBoolean()) {
                    members.add(system.intersectDef(
                            system.find("Bool")
                                    .map(t -> ((OwnedTypeDef) t).def())
                                    .orElseThrow(),
                            new BooleanConstraint(ValueConstraint.Operator.EQ, member.asBoolean())));
                }
            }
            return system.unionDef(members.toArray(TypeDef[]::new));
        }

        if (schema.has("anyOf")) {
            List<TypeDef> members = new ArrayList<>();
            for (JsonNode member : schema.get("anyOf")) {
                members.add(parse(member));
            }
            return system.unionDef(members.toArray(TypeDef[]::new));
        }

        if (schema.has("allOf")) {
            List<TypeDef> members = new ArrayList<>();
            for (JsonNode member : schema.get("allOf")) {
                members.add(parse(member));
            }
            return system.intersectDef(members.toArray(TypeDef[]::new));
        }

        if (schema.has("oneOf")) {
            // approximation: oneOf is treated as a union here
            List<TypeDef> members = new ArrayList<>();
            for (JsonNode member : schema.get("oneOf")) {
                members.add(parse(member));
            }
            return system.unionDef(members.toArray(TypeDef[]::new));
        }

        if (schema.has("not")) {
            return system.negateDef(parse(schema.get("not")));
        }

        return system.topDef();
    }

    private TypeDef parseArray(JsonNode schema) {
        List<TypeDef> members = new ArrayList<>();
        List<Parameter> params = new ArrayList<>();
        if (schema.has("items")) {
            TypeDef itemsType = parse(schema.get("items"));
            int index = members.size();
            members.add(itemsType);
            params.add(new Parameter.Positional(index, true));
        } else {
            int index = members.size();
            members.add(system.topDef());
            params.add(new Parameter.Positional(index, true));
        }
        return system.constructDef("Array", members, params);
    }

    private TypeDef parseObject(JsonNode schema) {
        List<TypeDef> members = new ArrayList<>();
        List<Parameter> params = new ArrayList<>();
        Set<String> required = new HashSet<>();
        if (schema.has("required")) {
            for (JsonNode req : schema.get("required")) {
                required.add(req.asString());
            }
        }

        if (schema.has("properties")) {
            JsonNode props = schema.get("properties");
            for (Map.Entry<String, JsonNode> entry : props.properties()) {
                String name = entry.getKey();
                TypeDef propType = parse(entry.getValue());
                int index = members.size();
                members.add(propType);
                params.add(new Parameter.Named(name, index, !required.contains(name)));
            }
        }

        if (schema.has("additionalProperties")) {
            JsonNode addProps = schema.get("additionalProperties");
            if (addProps.isBoolean()) {
                if (addProps.asBoolean()) {
                    params.add(new Parameter.Spread());
                }
            } else {
                int addIndex = members.size();
                members.add(parse(addProps));
                params.add(new Parameter.Spread(addIndex));
            }
        } else {
            params.add(new Parameter.Spread());
        }

        return system.constructDef("Object", members, params);
    }
}
