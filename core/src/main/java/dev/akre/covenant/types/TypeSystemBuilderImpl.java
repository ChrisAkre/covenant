package dev.akre.covenant.types;

import java.util.List;
import java.util.Map;

/**
 * Fluent builder for creating a AbstractTypeSystem.
 */
public class TypeSystemBuilderImpl extends AbstractTypeSystemBuilder<TypeSystemBuilderImpl, AbstractTypeSystem> {

    public TypeSystemBuilderImpl() {
        super(Map.of(), List.of(), TypeSystemImpl::new);
    }

    public TypeSystemBuilderImpl(AbstractTypeSystem base) {
        super(base.typesDef(), base.parser().constraintParsers(), TypeSystemImpl::new);
    }

    @Override
    protected TypeSystemBuilderImpl self() {
        return this;
    }
}
