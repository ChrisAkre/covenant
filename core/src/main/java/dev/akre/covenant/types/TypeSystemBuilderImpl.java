package dev.akre.covenant.types;

/**
 * Fluent builder for creating a AbstractTypeSystem.
 */
public class TypeSystemBuilderImpl extends AbstractTypeSystemBuilder<TypeSystemBuilderImpl, AbstractTypeSystem> {

    public TypeSystemBuilderImpl() {
        super(TypeSystemImpl::new);
    }

    public TypeSystemBuilderImpl(AbstractTypeSystem base) {
        super(base, TypeSystemImpl::new);
    }

    @Override
    protected TypeSystemBuilderImpl self() {
        return this;
    }
}
