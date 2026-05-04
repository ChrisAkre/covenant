package dev.akre.covenant.types;

import java.util.*;

public record TypeSystemImpl(Map<String, TypeDef> typesDef, TypeDef topDef, TypeDef bottomDef, TypeDef nilDef)
        implements AbstractTypeSystem {

    public TypeSystemImpl {
        typesDef = Collections.unmodifiableMap(new LinkedHashMap<>(typesDef));
        if (topDef == null) {
            topDef = typesDef.values().stream()
                    .filter(TopType.class::isInstance)
                    .findFirst()
                    .orElseThrow();
        }
        if (bottomDef == null) {
            bottomDef = typesDef.values().stream()
                    .filter(BottomType.class::isInstance)
                    .findFirst()
                    .orElseThrow();
        }
        if (nilDef == null) {
            nilDef = typesDef.values().stream()
                    .filter(t -> t.attributes().contains(dev.akre.covenant.api.TypeAttribute.NULL_SEMANTICS))
                    .findFirst()
                    .orElse(null);
        }
    }

    public TypeSystemImpl(Map<String, TypeDef> types) {
        this(types, null, null, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeSystemImpl that)) return false;
        return Objects.equals(typesDef, that.typesDef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typesDef);
    }

    @Override
    public String toString() {
        return "TypeSystemImpl[types=" + typesDef.keySet() + "]";
    }
}
