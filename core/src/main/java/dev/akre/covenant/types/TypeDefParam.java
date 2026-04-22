package dev.akre.covenant.types;

import dev.akre.covenant.api.Parameter;
import java.util.Objects;

public record TypeDefParam(TypeDef type, Parameter parameter) {
    public TypeDefParam {
        Objects.requireNonNull(type);
    }
}
