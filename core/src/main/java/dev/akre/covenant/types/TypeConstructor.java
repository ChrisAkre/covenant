package dev.akre.covenant.types;

import java.util.List;

/**
 * A factory for creating complex types based on generic parameters.
 */
public interface TypeConstructor {
    /**
     * Constructs a new TypeDef from the provided parameters.
     */
    TypeDef construct(
            AbstractTypeSystem system, TemplateType origin, List<TypeDefParam> parameters);
}
