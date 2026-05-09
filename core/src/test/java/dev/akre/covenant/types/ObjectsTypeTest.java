package dev.akre.covenant.types;

import dev.akre.covenant.api.Type;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class ObjectsTypeTest {

    public static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testObjectBasics() {
        SYSTEM.assertThat("Object").satisfies("Object");
        SYSTEM.assertThat("Object<id: String>").satisfies("Object");
        SYSTEM.assertThat("Object").notSatisfies("Object<id: String>");
    }

    @Test
    public void testObjectStructural() {
        // Extra fields in source are fine (width subtyping)
        SYSTEM.assertThat("Object<id: String, age: Int>").satisfies("Object<id: String, ...>");

        // Covariance of field types (depth subtyping)
        SYSTEM.assertThat("Object<id: Int>").satisfies("Object<id: Float>");

        // Missing required field fails
        SYSTEM.assertThat("Object<name: String>").notSatisfies("Object<id: String>");
    }

    @Test
    public void testObjectAlgebra() {
        // Union
        SYSTEM.assertThat("Object<foo: String> | Object<bar: Int>").satisfiedBy("Object<foo: String>");
        SYSTEM.assertThat("Object<foo: String> | Object<bar: Int>").satisfiedBy("Object<bar: Int>");

        // Intersection (Property Combination)
        // Note: Object<foo: String, bar: Int, ...> should be equivalent to Object<foo: String, ...> & Object<bar: Int,
        // ...>
        SYSTEM.assertThat("Object<foo: String, bar: Int, ...>")
                .satisfies("Object<foo: String, ...> & Object<bar: Int, ...>");

        // The other way: an intersection satisfies the combined object
        SYSTEM.assertThat("Object<foo: String, ...> & Object<bar: Int, ...>")
                .satisfies("Object<foo: String, bar: Int, ...>");

        SYSTEM.assertThat("Object<foo: String, bar: Int, ...>")
                .isEquivalentTo("Object<foo: String, ...> & Object<bar: Int, ...>");

        SYSTEM.assertThat("Object<foo: String, bar: Int, ...>")
                .isEquivalentTo("Object<foo: String, ...> & Object<bar: Int, ...>");

        SYSTEM.assertThat("Object<foo: String>")
                .intersect("Object<foo: String>")
                .isEquivalentTo("Object<foo: String>");

        // Closed & Open Intersection: Result is bottom because 'b' is missing in the closed object
        SYSTEM.assertThat("Object<a: Int> & Object<b: String, ...>").isBottom();
    }

    @Test
    public void testObjectNegation() {
        SYSTEM.assertThat("Object<foo: String> & ~Object<foo: String>").isBottom();
    }

    @Test
    public void testObjectTermNavigation() {
        SYSTEM.assertThat("Object<id: String, age: Int>").term("id").satisfies("String");
        SYSTEM.assertThat("Object<id: String, age: Int>").term("age").satisfies("Int");
        SYSTEM.assertThat("Object<id: String>").term("missing").isBottom();

        // Open object
        SYSTEM.assertThat("Object<id: String, ...>").term("any").satisfies("Any");

        // Escaped segments
        SYSTEM.assertThat("Object<id: String>").term("'id'").satisfies("String");

        // Deep navigation
        SYSTEM.assertThat("Object<user: Object<name: String>>")
                .term("user")
                .term("name")
                .satisfies("String");

        // Alternatively, use path expressions directly in the type SYSTEM parsing:
        SYSTEM.assertThat("Object<user: Object<name: String>>:user:name").evaluatesTo("String");

        // Algebraic
        SYSTEM.assertThat("Object<a: Int> | Object<a: String>").term("a").isEquivalentTo("Int | String");
        SYSTEM.assertThat("Object<a: Int> & Object<b: String, ...>").term("a").isBottom();
    }

    @Test
    public void testConstrainedParameters() {
        // structural equivalence
        SYSTEM.assertThat("Object<[matches \"^ext_\"]: Int>")
                .satisfies("Object<[matches \"^ext_\"]: Int>");

        // subtyping (depth subtyping)
        SYSTEM.assertThat("Object<[matches \"^ext_\"]: Int>")
                .satisfies("Object<[matches \"^ext_\"]: Number>");
        SYSTEM.assertThat("Object<[matches \"^ext_\"]: Number>")
                .notSatisfies("Object<[matches \"^ext_\"]: Int>");

        // basic algebra (exact constraint matches)
        SYSTEM.assertThat("Object<[matches \"^ext_\"]: Int> | Object<[matches \"^ext_\"]: String>")
                .isEquivalentTo("Object<[matches \"^ext_\"]: Int | String>");

        SYSTEM.assertThat("Object<[matches \"^ext_\"]: Int> & Object<[matches \"^ext_\"]: String>")
                .isBottom();

        // Note on regex algebra:
        // A full implementation of prune/graft for constrained parameters would involve complex regex algebra
        // (e.g., calculating the intersection of [matches "^a_"] and [matches ".*_b$"]).
        // This is currently outside the scope of the core type system.
        // If a regex library supporting intersection/subsumption were used, we would be able to do
        // more advanced canonicalization and algebraic simplification.
    }

    @Test
    public void testMultipleConstrainedParameters() {
        // Intersection of multiple constraints (exact matches)
        SYSTEM.assertThat("Object<[matches \"^a_\"]: Int, [matches \"^b_\"]: Int>")
                .intersect("Object<[matches \"^a_\"]: Number, [matches \"^b_\"]: Number>")
                .isEquivalentTo("Object<[matches \"^a_\"]: Int, [matches \"^b_\"]: Int>");

        // Intersection of multiple constraints (different patterns)
        // Note: Without regex algebra, they are treated as separate property requirements.
        // Object<[matches "^a_"]: Int, [matches "^b_"]: String, ...>
        SYSTEM.assertThat("Object<[matches \"^a_\"]: Int, ...> & Object<[matches \"^b_\"]: String, ...>")
                .isEquivalentTo("Object<[matches \"^a_\"]: Int, [matches \"^b_\"]: String, ...>");

        // Union of multiple constraints (exact matches)
        SYSTEM.assertThat("Object<[matches \"^a_\"]: Int, [matches \"^b_\"]: Int> | Object<[matches \"^a_\"]: String, [matches \"^b_\"]: String>")
                .isEquivalentTo("Object<[matches \"^a_\"]: Int | String, [matches \"^b_\"]: Int | String>");

        // Union of multiple constraints (different patterns - should NOT merge)
        SYSTEM.assertThat("Object<[matches \"^a_\"]: Int> | Object<[matches \"^b_\"]: Int>")
                .isEquivalentTo("Object<[matches \"^a_\"]: Int> | Object<[matches \"^b_\"]: Int>");
    }

    @Test
    public void testObjectConcatenation() {
        Type.GenericType aObject = SYSTEM.expression("Object<a: Int>");
        Type.GenericType bObject = SYSTEM.expression("Object<b: String>");
        Type.GenericType anyObject = SYSTEM.expression("Object<...>");

        SYSTEM.assertThat(aObject).concat(bObject).isEquivalentTo("Object<a: Int, b: String>");

        SYSTEM.assertThat(aObject).concat(anyObject).isEquivalentTo("Object<...>");
    }

        @Test
        public void testSpreadIntersectionUnification() {
            SYSTEM.assertThat("Object<[matches /.*/]: Object<Range: Number, ...>>")
                    .intersect("Object<[matches /.*/]: Object<Id: String, ...>>")
                    .isEquivalentTo("Object<[matches /.*/]: Object<Range: Number, Id: String, ...>>");

            SYSTEM.assertThat("Object<[matches /.*/]: Object<a: Int>>")
                    .intersect("Object<[matches /.*/]: Object<a: String>>")
                    .isEquivalentTo("Bottom");

            SYSTEM.assertThat("Object<[matches /a.*/]: Object<[matches /b.*/]: Int>>")
                    .intersect("Object<[matches /a.*/]: Object<[matches /b.*/]: Number>>")
                    .isEquivalentTo("Object<[matches /a.*/]: Object<[matches /b.*/]: Int>>");
        }
}
