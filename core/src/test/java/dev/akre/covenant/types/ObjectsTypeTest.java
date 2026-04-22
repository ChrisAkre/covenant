package dev.akre.covenant.types;

import dev.akre.covenant.api.Type;
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

        SYSTEM.assertThat("Object<a: Int> & Object<b: String, ...>").isEquivalentTo("Object<a: Int>");
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
        SYSTEM.assertThat("Object<a: Int> & Object<b: String, ...>").term("a").isEquivalentTo("Int");
    }

    @Test
    public void testObjectConcatenation() {
        Type.GenericType aObject = (Type.GenericType) SYSTEM.expression("Object<a: Int>");
        Type.GenericType bObject = (Type.GenericType) SYSTEM.expression("Object<b: String>");
        Type.GenericType anyObject = (Type.GenericType) SYSTEM.expression("Object<...>");

        SYSTEM.assertThat(aObject).concat(bObject).isEquivalentTo("Object<a: Int, b: String>");

        SYSTEM.assertThat(aObject).concat(anyObject).isEquivalentTo("Object<...>");
    }
}
