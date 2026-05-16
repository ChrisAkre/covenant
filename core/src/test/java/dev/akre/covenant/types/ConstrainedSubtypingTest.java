package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class ConstrainedSubtypingTest {

    public static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testConstrainedSubtypingDifferentRegex() {
        // A type that allows 'foo' as Int SHOULD satisfy a type that allows anything as Number.
        SYSTEM.assertThat("Object<'foo': Int>")
                .satisfies("Object<[matches /.*/]: Number, ...>");
    }

    @Test
    public void testConstrainedSubtypingIncompatibleTypes() {
        // Object<'foo': String> should NOT satisfy Object<[matches /.*/]: Int, ...>
        try {
            SYSTEM.assertThat("Object<'foo': String>")
                    .satisfies("Object<[matches /.*/]: Int, ...>");
            throw new RuntimeException("Expected satisfaction check to fail");
        } catch (AssertionError e) {
            // Success
        }
    }
    
    @Test
    public void testNarrowerRegexSubtyping() {
        // Object<[matches /^a/]: Int> SHOULD satisfy Object<[matches /.*/]: Int, ...>
        SYSTEM.assertThat("Object<[matches /^a/]: Int>")
                .satisfies("Object<[matches /.*/]: Int, ...>");
    }

    @Test
    public void testOverlappingRegexSubtyping() {
        // [matches /^pre.*/] is a subset of [matches /.*/]
        SYSTEM.assertThat("Object<[matches /^pre.*/]: Int>")
                .satisfies("Object<[matches /.*/]: Int, ...>");
    }
}
