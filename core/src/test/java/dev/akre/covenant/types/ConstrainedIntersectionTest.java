package dev.akre.covenant.types;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class ConstrainedIntersectionTest {

    public static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testNamedAndConstrainedIntersectionDisjoint() {
        // 'foo' must be both Int and String -> Bottom
        SYSTEM.assertThat("Object<'foo': Int, ...> & Object<[matches /.*/]: String, ...>")
                .isBottom();
    }
    
    @Test
    public void testNamedAndConstrainedIntersectionOverlapping() {
        // 'foo' must be both Int and Number -> Int
        SYSTEM.assertThat("Object<'foo': Int, ...> & Object<[matches /.*/]: Number, ...>")
                .isEquivalentTo("Object<[matches /.*/]: Number, 'foo': Int, ...>");
    }

    @Test
    public void testCrossConstrainedIntersection() {
        SYSTEM.assertThat("Object<'foo': Int, 'bar': String, ...> & Object<[matches /^f.*/]: Number, ...>")
                .isEquivalentTo("Object<[matches /^f.*/]: Number, 'foo': Int, 'bar': String, ...>");
    }

    @Test
    public void testUnionsWithConstraints() {
        // (Object<'foo': Int> | Object<'bar': Int>) & Object<[matches /.*/]: Number>
        // should yield Object<'foo': Int, [matches /.*/]: Number> | Object<'bar': Int, [matches /.*/]: Number>
        SYSTEM.assertThat("(Object<'foo': Int, ...> | Object<'bar': Int, ...>) & Object<[matches /.*/]: Number, ...>")
                .isEquivalentTo("Object<[matches /.*/]: Number, 'foo': Int, ...> | Object<[matches /.*/]: Number, 'bar': Int, ...>");
    }

    @Disabled("Future enhancement: unifying overlapping regex constraints")
    @Test
    public void testComplexRegexConstraintsIntersection() {
        // Intersection of complex constraints
        SYSTEM.assertThat("Object<[matches /^prefix/]: Int, ...> & Object<[matches /suffix$/]: Number, ...>")
                .satisfies("Object<[matches /^prefix.*suffix$/]: Int, ...>");
    }

    @Disabled("Future enhancement: unifying disjoint regex constraints")
    @Test
    public void testComplexRegexConstraintsDisjoint() {
        // If constraints are mutually exclusive and closed, it should be bottom.
        // Wait, if they are closed, intersection of open objects just merges constraints.
        SYSTEM.assertThat("Object<[matches /^a/]: Int, ...> & Object<[matches /^b/]: String, ...>")
                .isEquivalentTo("Object<[matches /^a/]: Int, [matches /^b/]: String, ...>");
    }
}
