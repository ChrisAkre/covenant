package dev.akre.covenant.types;

import dev.akre.covenant.api.Type;
import java.util.Map.Entry;

/**
 * A predefined type system containing standard JSON types and constructors.
 */
public final class JsonTypeSystem {

    public static final AbstractTypeSystem INSTANCE = new TypeSystemBuilderImpl()
            .atom("top")
            .asTop()
            .atom("bottom")
            .asBottom()
            .atom("Null")
            .asNull()
            .typeAlias("Any", "top")
            .atom("Bool")
            .asBoolean()
            .atom("String")
            .asString()
            .atom("Symbol")
            .atom("Number")
            .asNumeric()
            .atom("Float")
            .satisfies("Number")
            .atom("Int")
            .satisfies("Number")
            .satisfies("Float")
            .atom("Array")
            .arrayPattern()
            .atom("Object")
            .objectPattern()
            .build();

    /**
     * Parses a Jackson 3 JsonNode schema into a TypeDef using the default JsonTypeSystem.
     */
    public static Type fromSchema(tools.jackson.databind.JsonNode schema) {
        return INSTANCE.wrap(new JsonSchemaParser(INSTANCE).parse(schema));
    }

    /**
     * Contract verification: Checks if the target system supports all standard JSON atoms and aliases.
     */
    public static void checkContract(AbstractTypeSystem other) {
        if (other == INSTANCE) {
            return;
        }

        for (Entry<String, TypeDef> entry : INSTANCE.typesDef().entrySet()) {
            String name = entry.getKey();
            OwnedTypeDef refType = INSTANCE.wrap(entry.getValue());
            OwnedTypeDef otherType = other.find(name)
                    .map(t -> (OwnedTypeDef) t)
                    .orElseThrow(() ->
                            new IllegalArgumentException("AbstractTypeSystem is missing required JSON type: " + name));

            // Atom/Template Hierarchy check
            if (refType.def() instanceof AtomType refAtom) {
                for (String pName : refAtom.parentNames()) {
                    OwnedTypeDef otherParent = other.find(pName)
                            .map(t -> (OwnedTypeDef) t)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "AbstractTypeSystem is missing required JSON type: " + pName));
                    if (!otherType.isAssignableTo(otherParent)) {
                        throw new IllegalArgumentException(
                                "Atom '%s' in target system must satisfy '%s' to be JSON-compatible"
                                        .formatted(name, pName));
                    }
                }
            } else if (refType.def() instanceof TemplateType refTemplate) {
                for (String pName : refTemplate.parentNames()) {
                    OwnedTypeDef otherParent = other.find(pName)
                            .map(t -> (OwnedTypeDef) t)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "AbstractTypeSystem is missing required JSON type: " + pName));
                    if (!otherType.isAssignableTo(otherParent)) {
                        throw new IllegalArgumentException(
                                "Template '%s' in target system must satisfy '%s' to be JSON-compatible"
                                        .formatted(name, pName));
                    }
                }
                // Check constructor presence
                if (refTemplate.constructor() != null
                        && (!(otherType.def() instanceof TemplateType t) || t.constructor() == null)) {
                    throw new IllegalArgumentException("Template '%s' must have a TypeConstructor".formatted(name));
                }
            } else {
                // Logic for aliases/functions (Semantic round-trip)
                OwnedTypeDef requiredSpec = other.typeExpression(refType.repr());
                if (!other.satisfies(otherType.def(), requiredSpec.def())) {
                    throw new IllegalArgumentException(
                            "Type '%s' is incompatible with JsonTypeSystem. Expected at least: %s, Found: %s"
                                    .formatted(name, requiredSpec.repr(), otherType.repr()));
                }
            }
        }
    }
}
