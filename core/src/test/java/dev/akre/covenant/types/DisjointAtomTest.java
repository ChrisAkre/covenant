package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class DisjointAtomTest {
    public static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testDisjointAtomsIntersection() {
        // A value cannot be both a String and an Integer.
        SYSTEM.assertThat("String & Int").isBottom();
    }

    @Test
    public void testObjectWithBottomProperty() {
        // If an object REQUIRES a property that is impossible (Bottom), 
        // the object itself should be impossible (Bottom).
        SYSTEM.assertThat("Object<'a': bottom>").isBottom();
    }
}
