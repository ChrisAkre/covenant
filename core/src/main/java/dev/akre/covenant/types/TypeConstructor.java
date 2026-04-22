package dev.akre.covenant.types;

import dev.akre.covenant.api.Parameter;
import java.util.List;

/**
 * A factory for creating complex types based on generic parameters.
 */
public interface TypeConstructor {
    /**
     * Constructs a new TypeDef from the provided members and parameters.
     */
    TypeDef construct(
            AbstractTypeSystem system, TemplateType origin, List<TypeDef> members, List<Parameter> parameters);
}
