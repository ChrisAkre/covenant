package dev.akre.covenant.core;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.JsonTypeSystem;
import dev.akre.covenant.types.TestTypeSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JoltIdentifiedDefectsTest {

    @Test
    public void testAtomVsStringConstraintAssignability() throws Exception {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);
        system.assertThat("\"disabled\"").isEquivalentTo("String & eq 'disabled'");

        Type atomType = JsonTypeSystem.INSTANCE.expression("\"disabled\"");
        Type stringConstraint = JsonTypeSystem.INSTANCE.intersect(
                JsonTypeSystem.INSTANCE.type("String"),
                JsonTypeSystem.INSTANCE.expression("eq 'disabled'")
        );

        // They represent the exact same set of values, so they MUST be mutually assignable.
        assertTrue(atomType.isAssignableFrom(stringConstraint), "string literal");
        assertTrue(stringConstraint.isAssignableFrom(atomType), "StringConstraint MUST be assignable from AtomType");
    }

    @Test
    public void testAtomVsStringConstraintIntersection() throws Exception {
        Type atomType = JsonTypeSystem.INSTANCE.expression("'disabled'");
        Type stringConstraint = JsonTypeSystem.INSTANCE.intersect(
                JsonTypeSystem.INSTANCE.type("String"),
                JsonTypeSystem.INSTANCE.expression("eq 'disabled'")
        );

        Type intersection = JsonTypeSystem.INSTANCE.intersect(atomType, stringConstraint);
        assertTrue(!intersection.isBottom(), "Intersection should not be bottom");
    }

    @Test
    public void testOptionalRegexVsLiteralObject() throws Exception {
        Type patternObj = JsonTypeSystem.INSTANCE.expression("Object<[matches /^photo-.*-id$/]?: String, ...>");
        Type exactObj = JsonTypeSystem.INSTANCE.expression("Object<'photo-0-id': String, 'photo-1-id': String>");

        Type intersection = JsonTypeSystem.INSTANCE.intersect(patternObj, exactObj);
        assertTrue(!intersection.isBottom(), "Intersection should not be bottom");

        // patternObj should be assignable from exactObj since exactObj is a subset!
        assertTrue(patternObj.isAssignableFrom(exactObj), "Pattern object MUST be assignable from exact object (Subset)");
    }

    @Test
    public void testOpenVsClosedGenericObject() throws Exception {
        Type openObj = JsonTypeSystem.INSTANCE.expression("Object<'a': String, ...>");
        Type closedObj = JsonTypeSystem.INSTANCE.expression("Object<'a': String>");

        // Both have the same explicit keys, but openObj allows additional properties (spread top).
        // closedObj is a strict subset of openObj.
        assertTrue(openObj.isAssignableFrom(closedObj), "Open object MUST be assignable from a Closed object with identical required fields");
        assertFalse(closedObj.isAssignableFrom(openObj), "Closed object MUST NOT be assignable from an Open object");
    }
}
