package dev.akre.covenant.types;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a type that must satisfy at least one of multiple types.
 */
public record UnionType(Set<TypeDef> members) implements ContainerDef {
    public UnionType {
        members = Collections.unmodifiableSet(new LinkedHashSet<>(members));
    }

    public UnionType(Collection<TypeDef> members) {
        this(Collections.unmodifiableSet(new LinkedHashSet<>(members)));
    }

    @Override
    public String repr() {
        return members.stream().map(TypeDef::repr).sorted().collect(Collectors.joining(" | "));
    }

    @Override
    public String toString() {
        return repr();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof UnionType(Set<TypeDef> otherMembers) && members.equals(otherMembers));
    }

    @Override
    public int hashCode() {
        return Objects.hash(members);
    }
}
