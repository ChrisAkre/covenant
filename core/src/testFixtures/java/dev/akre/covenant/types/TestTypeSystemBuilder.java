package dev.akre.covenant.types;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeSystem;

/**
 * Builds a TestTypeSystem, which includes fluent assertion capabilities.
 */
public class TestTypeSystemBuilder extends AbstractTypeSystemBuilder<TestTypeSystemBuilder, TestTypeSystem> {

    public TestTypeSystemBuilder() {
        super(TestTypeSystem::new);
    }

    public TestTypeSystemBuilder(TypeSystem base) {
        super(TypeSystemUtils.asTypesDef(base.types()), TestTypeSystem::new);
    }

    @Override
    protected TestTypeSystemBuilder self() {
        return this;
    }
}
