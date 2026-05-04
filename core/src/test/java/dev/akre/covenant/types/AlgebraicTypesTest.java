package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class AlgebraicTypesTest {

    @Test
    public void testUnion() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("String").satisfies("String | Int");
        system.assertThat("Int").satisfies("String | Int");
        system.assertThat("String | Int").satisfiedBy("String");
        system.assertThat("Bool").notSatisfies("String | Int");
    }

    @Test
    public void testIntersection() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("String & 'foo'").satisfies("String");
        system.assertThat("String & 'foo'").satisfies("'foo'");
        system.assertThat("String").notSatisfies("String & 'foo'");

        system.assertThat("Null & ~Null").isBottom();
        system.assertThat("String & ~Null")
                // TODO Figure out why this is String & ~Null
                //                .printsLike("String")
                .isEquivalentTo("String");
    }

    @Test
    public void testOptionalAlgebra() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("String").satisfies("String?");
        system.assertThat("Null").satisfies("String?");
        system.assertThat("Null").notSatisfies("String? & 'foo'"); // User example
    }

    @Test
    public void testManualNullCoalesce() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        OwnedTypeDef t1 = system.typeExpression("String | Null");
        OwnedTypeDef t2 = system.typeExpression("String");
        OwnedTypeDef nullType = (OwnedTypeDef) system.find("Null").orElseThrow();

        // (T1 & ~Null) | T2
        OwnedTypeDef result = t1.intersect(nullType.negate()).union(t2);

        system.assertThat(result).satisfies("String");
        system.assertThat("String").satisfies(result.repr());
    }
}
