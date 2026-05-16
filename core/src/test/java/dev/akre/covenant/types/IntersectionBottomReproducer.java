package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class IntersectionBottomReproducer {
    public static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testShiftToTrashSimulated() {
        // This simulates the placements in shiftToTrash
        String s1 = "Object<foo: Object<a: String, ...>, ...>";
        String s2 = "Object<foo: Object<b: String, ...>, ...>";
        
        SYSTEM.assertThat(s1).intersect(s2).satisfies("Object<foo: Object<a: String, b: String, ...>, ...>");
    }
}
