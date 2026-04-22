package dev.akre.covenant.types;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents the complement of a type.
 */
public record NegationType(TypeDef inner) implements ContainerDef {

    @Override
    public String repr() {
        String r = inner.repr();
        return (inner instanceof UnionType || inner instanceof IntersectionType || inner instanceof FunctionType)
                ? "~(" + inner.repr() + ")"
                : "~" + inner.repr();
    }

    @Override
    public String toString() {
        return repr();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NegationType that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return Objects.hash("not", inner);
    }

    @Override
    public Collection<TypeDef> members() {
        return List.of(inner);
    }
}
