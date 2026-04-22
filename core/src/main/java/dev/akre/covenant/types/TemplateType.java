package dev.akre.covenant.types;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A named type that can be used as a template for GenericTypes.
 * Holds a TypeConstructor and structural patterns.
 */
public record TemplateType(
        String name,
        Set<String> parentNames,
        TypeConstructor constructor,
        EnumSet<dev.akre.covenant.api.TypeAttribute> attributes)
        implements NominalDef {
    public TemplateType {
        parentNames = Set.copyOf(parentNames);
        attributes = EnumSet.copyOf(attributes);
    }

    @Override
    public String repr() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TemplateType that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return repr();
    }

    public TemplateType withAttribute(dev.akre.covenant.api.TypeAttribute attribute) {
        EnumSet<dev.akre.covenant.api.TypeAttribute> newAttributes = EnumSet.copyOf(attributes);
        newAttributes.add(attribute);
        return new TemplateType(name, parentNames, constructor, newAttributes);
    }
}
