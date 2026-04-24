package dev.akre.covenant.types;

import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TypeSystemImpl(
        Map<String, TypeDef> typesDef,
        TypeParser parser,
        TypeDef topDef,
        TypeDef bottomDef,
        TypeDef nilDef)
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

    public TypeSystemImpl(Map<String, TypeDef> types, TypeParser parser) {
        this(types, parser, null, null, null);
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
    public @NonNull String toString() {
        return "TypeSystemImpl[types=" + typesDef.keySet() + "]";
    }
}
