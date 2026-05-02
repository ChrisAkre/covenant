package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class NominalInversionTest {

    @Test
    public void testAbstractNominalInversion() {
        TestTypeSystem system = new TestTypeSystemBuilder()
                .atom("Any").asTop()
                .atom("Nothing").asBottom()
                .atom("Shape").asAbstract()
                .atom("Circle").satisfies("Shape")
                .atom("Square").satisfies("Shape")
                .build();

        // If Shape is abstract, ~Shape should be equivalent to the intersection of its negated children.
        // ~Shape -> ~(Circle | Square) -> ~Circle & ~Square
        system.assertThat("~Shape").isEquivalentTo("~Circle & ~Square");
    }

    @Test
    public void testDeepAbstractNominalInversion() {
        TestTypeSystem system = new TestTypeSystemBuilder()
                .atom("Any").asTop()
                .atom("Nothing").asBottom()
                .atom("Shape").asAbstract()
                .atom("Polygon").asAbstract().satisfies("Shape")
                .atom("Circle").satisfies("Shape")
                .atom("Triangle").satisfies("Polygon")
                .atom("Square").satisfies("Polygon")
                .build();

        // ~Polygon -> ~Triangle & ~Square
        system.assertThat("~Polygon").isEquivalentTo("~Triangle & ~Square");

        // ~Shape -> ~Polygon & ~Circle -> (~Triangle & ~Square) & ~Circle
        system.assertThat("~Shape").isEquivalentTo("~Triangle & ~Square & ~Circle");
    }
}
