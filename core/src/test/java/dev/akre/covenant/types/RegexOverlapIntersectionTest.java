package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class RegexOverlapIntersectionTest {

    public static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testOverlappingRegexIntersection() {
        // Properties matching both /^a/ and /b$/ (e.g. "ab") 
        // must satisfy both Int and String -> Bottom.
        SYSTEM.assertThat("Object<[matches /^a/]: Int, ...> & Object<[matches /b$/]: String, ...>")
                .term("ab").isBottom();
    }

    @Test
    public void testCharacterClassOverlap() {
        // [ab]+ and [bc]+ overlap on "b", "bb", etc.
        // If one requires Int and other requires String, "b" must be bottom.
        SYSTEM.assertThat("Object<[matches /^[ab]+$/]: Int, ...> & Object<[matches /^[bc]+$/]: String, ...>")
                .term("b").isBottom();
        SYSTEM.assertThat("Object<[matches /^[ab]+$/]: Int, ...> & Object<[matches /^[bc]+$/]: String, ...>")
                .term("bb").isBottom();
        
        // Non-overlapping keys should still be fine
        SYSTEM.assertThat("Object<[matches /^[ab]+$/]: Int, ...> & Object<[matches /^[bc]+$/]: String, ...>")
                .term("a").satisfies("Int");
        SYSTEM.assertThat("Object<[matches /^[ab]+$/]: Int, ...> & Object<[matches /^[bc]+$/]: String, ...>")
                .term("c").satisfies("String");
    }

    @Test
    public void testMultipleOverlapIntersection() {
        // Key "abc" matches all three
        SYSTEM.assertThat("Object<[matches /^a.*/]: Number, ...> & Object<[matches /.*b.*/]: Int, ...> & Object<[matches /.*c$/]: Float, ...>")
                .term("abc").isEquivalentTo("Int"); // Int & Number & Float -> Int
    }

    @Test
    public void testComplexNestedOverlap() {
        // Intersection of objects with nested constrained objects
        // Outer keys overlap at "ax", inner keys overlap at "by"
        String type1 = "Object<[matches /^a.*/]: Object<[matches /^b.*/]: Int, ...>, ...>";
        String type2 = "Object<[matches /.*x$/]: Object<[matches /.*y$/]: String, ...>, ...>";
        
        SYSTEM.assertThat(type1).intersect(type2)
                .term("ax")
                .term("by")
                .isBottom();
    }
}
