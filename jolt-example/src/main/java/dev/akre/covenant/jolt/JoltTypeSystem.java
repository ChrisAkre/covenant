package dev.akre.covenant.jolt;

import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.JsonTypeSystem;

public class JoltTypeSystem {
    // JsonTypeSystem is final, so we just use its INSTANCE instead of extending it.
    public static final AbstractTypeSystem INSTANCE = JsonTypeSystem.INSTANCE;
}
