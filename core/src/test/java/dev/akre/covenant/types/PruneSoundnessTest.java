package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class PruneSoundnessTest {
    public static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testOpenObjectIntersectionSoundness() {
        String s1 = "Object<'a': Int, ...>";
        String s2 = "Object<'b': String, ...>";
        
        // This should not collapse to bottom. It should merge the required properties.
        SYSTEM.assertThat(s1).intersect(s2).isEquivalentTo("Object<'a': Int, 'b': String, ...>");
    }
}
