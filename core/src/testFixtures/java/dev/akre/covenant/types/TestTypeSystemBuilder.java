package dev.akre.covenant.types;

import dev.akre.covenant.api.TypeSystem;

import java.util.List;
import java.util.Map;

/**
 * Builds a TestTypeSystem, which includes fluent assertion capabilities.
 */
public class TestTypeSystemBuilder extends AbstractTypeSystemBuilder<TestTypeSystemBuilder, TestTypeSystem> {

    public TestTypeSystemBuilder() {
        super(Map.of(), List.of(), TestTypeSystem::new);
    }

    public static TestTypeSystemBuilder of(TypeSystem base) {
        if (base instanceof AbstractTypeSystem system) {
            return new TestTypeSystemBuilder(system);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public TestTypeSystemBuilder(AbstractTypeSystem base) {
        super(base.typesDef(), base.parser().constraintParsers(), TestTypeSystem::new);
    }

    @Override
    protected TestTypeSystemBuilder self() {
        return this;
    }
}
