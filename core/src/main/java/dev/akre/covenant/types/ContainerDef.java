package dev.akre.covenant.types;

import java.util.Collection;

public sealed interface ContainerDef extends TypeDef permits IntersectionType, UnionType, NegationType {

    Collection<TypeDef> members();

    // container types are handled by the type system
    @Override
    default Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef other) {
        throw new NoSuchMethodError("handled by the type system (%s, %s)".formatted(this, other));
    }

    @Override
    default Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef other) {
        throw new NoSuchMethodError("handled by the type system (%s, %s)".formatted(this, other));
    }

    @Override
    default Collection<TypeDef> invert(AbstractTypeSystem system) {
        throw new NoSuchMethodError("handled by the type system (%s)".formatted(this));
    }

    @Override
    default boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
        // returning false here to support early subsumption check
        if (this instanceof IntersectionType intersection) {
            // An intersection type satisfies 'other' if ANY of its members satisfies 'other'
            return intersection.members().stream().anyMatch(m -> system.satisfies(m, other));
        }
        if (this instanceof UnionType union) {
            // A union type satisfies 'other' if ALL of its members satisfy 'other'
            return union.members().stream().allMatch(m -> system.satisfies(m, other));
        }
        return false;
    }
}
