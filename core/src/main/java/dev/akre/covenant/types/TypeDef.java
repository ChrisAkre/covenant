package dev.akre.covenant.types;

import java.util.*;

/**
 * The base interface for all types in the Covenant type system.
 */
public sealed interface TypeDef
        permits ApplicableDef, ValueConstraint, ContainerDef, GenericTypeDef, NominalDef, SymbolType {

    default EnumSet<dev.akre.covenant.api.TypeAttribute> attributes() {
        return EnumSet.noneOf(dev.akre.covenant.api.TypeAttribute.class);
    }

    String repr();

    boolean satisfiesOther(AbstractTypeSystem system, TypeDef other);

    Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef typeDef);

    Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef typeDef);

    Collection<TypeDef> invert(AbstractTypeSystem system);
}
