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
        //        throw new NoSuchMethodError("handled by the type system (%s, %s)".formatted(this, other));
        // returning false here to support early subsumption check
        return false;
    }
}
