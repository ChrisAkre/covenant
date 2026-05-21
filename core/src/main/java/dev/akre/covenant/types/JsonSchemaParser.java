package dev.akre.covenant.types;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeParameter;
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

    public Type parse(JsonNode schema) {
        if (schema.isString()) {
            return system.typeExpression(schema.asString());
        }

        if (schema.has("type")) {
            JsonNode typeNode = schema.get("type");
            if (typeNode.isString()) {
                String typeName = typeNode.asString();
                return switch (typeName) {
                    case "string" -> {
                        Type type = system.type("String");
                        if (schema.has("pattern")) {
                            type = system.intersect(type, system.expression("matches \"" + schema.get("pattern").asString() + "\""));
                        }
                        if (schema.has("minLength")) {
                            type = system.intersect(type, system.expression("minlength " + schema.get("minLength").asInt()));
                        }
                        if (schema.has("maxLength")) {
                            type = system.intersect(type, system.expression("maxlength " + schema.get("maxLength").asInt()));
                        }
                        yield type;
                    }
                    case "number" ->
                        system.type("Number");
                    case "integer" ->
                        system.type("Int");
                    case "boolean" ->
                        system.type("Bool");
                    case "null" ->
                        system.type("Null");
                    case "array" -> parseArray(schema);
                    case "object" -> parseObject(schema);
                    default ->
                        system.find(typeName).orElseThrow();
                };
            }
        }

        if (schema.has("enum")) {
            List<Type> members = new ArrayList<>();
            for (JsonNode member : schema.get("enum")) {
                if (member.isString()) {
                    members.add(system.intersect(
                            system.type("String"),
                            system.expression("eq '" + member.asString().replace("'", "''") + "'")));
                } else if (member.isNumber()) {
                    members.add(system.intersect(
                            system.type("Number"),
                            system.expression("eq " + member.asString())));
                } else if (member.isBoolean()) {
                    members.add(system.intersect(
                            system.type("Bool"),
                            system.expression("eq " + member.asString())));
                }
            }
            return system.union(members.toArray(Type[]::new));
        }

        if (schema.has("anyOf")) {
            return system.union(schema.get("anyOf").valueStream().map(this::parse).toArray(Type[]::new));
        }

        if (schema.has("allOf")) {
            return system.intersect(schema.get("allOf").valueStream().map(this::parse).toArray(Type[]::new));
        }

        if (schema.has("oneOf")) {
            return system.union(schema.get("anyOf").valueStream().map(this::parse).toArray(Type[]::new));
        }

        if (schema.has("not")) {
            return parse(schema.get("not")).negate();
        }

        return system.top();
    }

    private Type parseArray(JsonNode schema) {
        List<dev.akre.covenant.api.TypeParameter> params = new ArrayList<>();
        JsonNode items = schema.get("items");
        JsonNode prefixItems = schema.get("prefixItems");
        
        if (prefixItems != null && prefixItems.isArray()) {
            int i = 0;
            for (JsonNode item : prefixItems) {
                params.add(new dev.akre.covenant.api.TypeParameter.Positional(parse(item), i++, false));
            }
            if (items != null) {
                params.add(new dev.akre.covenant.api.TypeParameter.Positional(parse(items), i, true));
            }
        } else if (items != null && items.isArray()) {
            int i = 0;
            for (JsonNode item : items) {
                params.add(new dev.akre.covenant.api.TypeParameter.Positional(parse(item), i++, false));
            }
            JsonNode additionalItems = schema.get("additionalItems");
            if (additionalItems != null) {
                params.add(new dev.akre.covenant.api.TypeParameter.Positional(parse(additionalItems), i, true));
            }
        } else {
            Type itemsType = items != null ? parse(items) : system.top();
            params.add(new dev.akre.covenant.api.TypeParameter.Positional(itemsType, 0, true));
        }

        Type arrayType = system.template("Array").construct(params);
        if (schema.has("minItems")) {
            arrayType = system.intersect(arrayType, system.expression("minitems " + schema.get("minItems").asInt()));
        }
        if (schema.has("maxItems")) {
            arrayType = system.intersect(arrayType, system.expression("maxitems " + schema.get("maxItems").asInt()));
        }

        return arrayType;
    }

    private Type parseObject(JsonNode schema) {
        List<TypeParameter> params = new ArrayList<>();
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
                params.add(new TypeParameter.Named(parse(entry.getValue()), name, !required.contains(name)));
            }
        }

        if (schema.has("patternProperties")) {
            JsonNode patternProps = schema.get("patternProperties");
            for (Map.Entry<String, JsonNode> entry : patternProps.properties()) {
                String pattern = entry.getKey();
                params.add(new TypeParameter.Constrained(parse(entry.getValue()), "matches", "\"" + pattern + "\"", true));
            }
        }

        if (schema.has("additionalProperties")) {
            JsonNode addProps = schema.get("additionalProperties");
            if (addProps.isBoolean()) {
                if (addProps.asBoolean()) {
                    params.add(new TypeParameter.Spread(system.top()));
                }
            } else {
                params.add(new TypeParameter.Spread(parse(addProps)));
            }
        } else {
            params.add(new TypeParameter.Spread(system.top()));
        }

        return system.template("Object").construct(params);
    }
}
