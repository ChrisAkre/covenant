package dev.akre.covenant.types;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a type that must match a specific symbol value (quoted identifier).
 */
public record SymbolType(String value) implements TypeDef {

    @Override
    public boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
        return other instanceof SymbolType s && this.value.equals(s.value());
    }

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef other) {
        if (other instanceof SymbolType s) {
            return this.value.equals(s.value()) ? Set.of(this) : Set.of();
        }
        return null;
    }

    @Override
    public Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef other) {
        if (other instanceof SymbolType s) {
            return this.value.equals(s.value()) ? Set.of(this) : null;
        }
        return null;
    }

    @Override
    public Collection<TypeDef> invert(AbstractTypeSystem system) {
        return null;
    }

    @Override
    public String repr() {
        return "'" + value.replace("'", "''") + "'";
    }

    @Override
    public String toString() {
        return repr();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SymbolType that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
