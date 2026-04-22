package dev.akre.covenant.types;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The "Top" type (universal set), which is satisfied by all other types.
 */
public record TopType(String name) implements NominalDef {

    @Override
    public NominalDef withAttribute(dev.akre.covenant.api.TypeAttribute attribute) {
        return this;
    }

    @Override
    public String repr() {
        return name;
    }

    @Override
    public Set<String> parentNames() {
        return Set.of();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TopType topType)) return false;
        return Objects.equals(name, topType.name);
    }

    @Override
    public EnumSet<dev.akre.covenant.api.TypeAttribute> attributes() {
        return EnumSet.of(dev.akre.covenant.api.TypeAttribute.ABSTRACT);
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }

    @Override
    public String toString() {
        return repr();
    }
}
