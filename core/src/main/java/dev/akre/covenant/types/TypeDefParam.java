package dev.akre.covenant.types;

import java.util.Objects;

public sealed interface TypeDefParam {
    TypeDef type();

    record Positional(TypeDef type, Integer index, boolean variadic) implements TypeDefParam {
        public Positional { Objects.requireNonNull(type); }
    }

    record Named(TypeDef type, String name, boolean optional) implements TypeDefParam {
        public Named { Objects.requireNonNull(type); }
    }

    record Constrained(TypeDef type, String keyword, String value, boolean optional) implements TypeDefParam {
        public Constrained { Objects.requireNonNull(type); }
    }

    record Spread(TypeDef type) implements TypeDefParam {
        public Spread { Objects.requireNonNull(type); }
    }
}
