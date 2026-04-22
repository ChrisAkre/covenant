package dev.akre.covenant.types;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A concrete atomic named type in the hierarchy.
 */
public record AtomType(String name, Set<String> parentNames, EnumSet<dev.akre.covenant.api.TypeAttribute> attributes)
        implements NominalDef {
    public AtomType {
        parentNames = Set.copyOf(parentNames);
        attributes = EnumSet.copyOf(attributes);
    }

    @Override
    public NominalDef withAttribute(dev.akre.covenant.api.TypeAttribute attribute) {
        EnumSet<dev.akre.covenant.api.TypeAttribute> newAttributes = EnumSet.copyOf(attributes);
        newAttributes.add(attribute);
        return new AtomType(name, parentNames, newAttributes);
    }

    @Override
    public String repr() {
        return name;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || (object instanceof AtomType other && name().equals(other.name()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return repr();
    }
}
