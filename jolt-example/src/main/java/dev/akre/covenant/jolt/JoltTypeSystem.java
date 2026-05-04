package dev.akre.covenant.jolt;

import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.TypeSystemBuilderImpl;
import dev.akre.covenant.types.JsonTypeSystem;

public class JoltTypeSystem {
    public static final AbstractTypeSystem INSTANCE = new TypeSystemBuilderImpl(JsonTypeSystem.INSTANCE).build();
}
