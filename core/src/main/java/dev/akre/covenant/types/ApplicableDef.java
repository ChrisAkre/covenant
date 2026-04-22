package dev.akre.covenant.types;

import java.util.List;

public sealed interface ApplicableDef extends TypeDef permits FunctionType, FunctionType.Signature {
    record OverloadDef(TypeDef returnType, List<TypeDef> parameters) {}

    TypeDef evaluate(AbstractTypeSystem system, List<TypeDef> args);

    List<OverloadDef> overloads(AbstractTypeSystem system, List<TypeDef> args);
}
