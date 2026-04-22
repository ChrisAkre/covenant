package dev.akre.covenant.types;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public record IntersectionType(Set<TypeDef> members) implements ContainerDef {
    public IntersectionType {
        members = Collections.unmodifiableSet(new LinkedHashSet<>(members));
    }

    public IntersectionType(Collection<TypeDef> members) {
        this(Collections.unmodifiableSet(new LinkedHashSet<>(members)));
    }

    @Override
    public String repr() {
        return members.stream()
                .map(m -> (m instanceof FunctionType) ? "(" + m.repr() + ")" : m.repr())
                .sorted()
                .collect(Collectors.joining(" & "));
    }

    @Override
    public String toString() {
        return repr();
    }

    @Override
    public int hashCode() {
        return members().hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return this == object || (object instanceof IntersectionType other && members.equals(other.members));
    }
}
