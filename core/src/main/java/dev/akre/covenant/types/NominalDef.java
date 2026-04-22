package dev.akre.covenant.types;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public sealed interface NominalDef extends TypeDef permits AtomType, TopType, BottomType, TemplateType {

    String name();

    Set<String> parentNames();

    NominalDef withAttribute(dev.akre.covenant.api.TypeAttribute attribute);

    default Set<TypeDef> parents(AbstractTypeSystem system) {
        return parentNames().stream()
                .map(system::find)
                .flatMap(Optional::stream)
                .map(t -> ((OwnedTypeDef) t).def())
                .collect(Collectors.toUnmodifiableSet());
    }

    // Nominal types do not compose
    @Override
    default Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef other) {
        if (this.satisfiesOther(system, other)) {
            return Set.of(this);
        } else if (other.satisfiesOther(system, this)) {
            return Set.of(other);
        } else if (other instanceof NominalDef) {
            return Set.of();
        }
        return null;
    }

    @Override
    default Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef other) {
        if (other.satisfiesOther(system, this)) {
            return Set.of(this);
        } else if (this.satisfiesOther(system, other)) {
            return Set.of(other);
        } else {
            return null;
        }
    }

    @Override
    default Set<TypeDef> invert(AbstractTypeSystem system) {
        // TODO if is abstract, return set of negated children.
        return null;
    }

    @Override
    default boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
        return other instanceof NominalDef n
                && (Objects.equals(this.name(), n.name())
                        || parents(system).stream()
                                .anyMatch(p -> p instanceof NominalDef pn && pn.satisfiesOther(system, n)));
    }
}
