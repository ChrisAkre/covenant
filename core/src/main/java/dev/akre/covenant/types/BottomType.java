package dev.akre.covenant.types;

import static dev.akre.covenant.api.TypeAttribute.*;

import dev.akre.covenant.api.TypeAttribute;
import java.util.EnumSet;
import java.util.Set;

/**
 * The "Bottom" type (empty set), which is satisfied by no types.
 */
public record BottomType(String name) implements NominalDef {
    public static final BottomType INSTANCE = new BottomType("bottom");

    @Override
    public NominalDef withAttribute(TypeAttribute attribute) {
        return this;
    }

    @Override
    public String repr() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BottomType;
    }

    @Override
    public EnumSet<TypeAttribute> attributes() {
        return EnumSet.of(ABSTRACT);
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }

    @Override
    public String toString() {
        return repr();
    }

    @Override
    public Set<String> parentNames() {
        return Set.of();
    }
}
