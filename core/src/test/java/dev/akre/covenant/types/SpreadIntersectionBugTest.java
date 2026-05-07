package dev.akre.covenant.types;

import dev.akre.covenant.api.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpreadIntersectionBugTest {

    @org.junit.jupiter.api.Disabled
    @Test
    public void testSpreadIntersectionUnification() {
        AbstractTypeSystem system = JsonTypeSystem.INSTANCE;

        // Let's create two types that represent nested spread indexing mapping constraints
        // We use standard string notation: Object<..., Type>
        Type type1 = system.expression("Object<..., Object<Range: Number, ...>>");
        Type type2 = system.expression("Object<..., Object<Id: String, ...>>");

        // Expected Intersection: Object<..., Object<Range: Number, Id: String, ...>>
        Type expectedIntersection = system.expression("Object<..., Object<Range: Number, Id: String, ...>>");

        // Perform intersection
        Type actualIntersection = type1.intersect(type2);

        // Assert they are structurally equivalent exactly!
        System.out.println("Expected: " + expectedIntersection.repr());
        System.out.println("Actual:   " + actualIntersection.repr());

        // Because of the bug, the actual intersection drops Range and just keeps Id!
        // actualIntersection.repr() returns "Object<..., Object<Id: String, ...>>"
        assertEquals(expectedIntersection.repr(), actualIntersection.repr(), "Intersection failed to combine inner spread structures.");
    }
}
