package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;
import java.util.Set;
import dev.akre.covenant.api.Type;

public class MultipleInheritanceTest {

    @Test
    public void testMultipleInheritanceIntersection() {
        TestTypeSystem system = new TestTypeSystemBuilder()
                .atom("Any").asTop()
                .atom("None").asBottom()
                .atom("A").asAbstract()
                .atom("B").asAbstract()
                .atom("D").asAbstract()
                .atom("C").satisfies("A", "B")
                .atom("E").satisfies("C", "D")
                .atom("F").satisfies("A")
                .build();

        // 1. Basic Multiple Inheritance
        // A & B -> C (C is the greatest lower bound, E is a subtype of C)
        system.assertThat("A & B").isEquivalentTo("C");

        // 2. Intersection across branches
        // C & D -> E
        system.assertThat("C & D").isEquivalentTo("E");

        // 3. Indirect Multiple Inheritance
        // A & D -> E (E satisfies A via C, and D directly)
        system.assertThat("A & D").isEquivalentTo("E");
        
        // 4. Disjoint branches with common ancestor
        // C & F -> Bottom (Both satisfy A, but no type satisfies both C and F)
        system.assertThat("C & F").isBottom();
    }

    @Test
    public void testMultipleGreatestLowerBounds() {
        TestTypeSystem system = new TestTypeSystemBuilder()
                .atom("Any").asTop()
                .atom("None").asBottom()
                .atom("A").asAbstract()
                .atom("B").asAbstract()
                // C and D are independent GLBs for A & B
                .atom("C").satisfies("A", "B")
                .atom("D").satisfies("A", "B")
                .build();

        // A & B -> C | D
        system.assertThat("A & B").isEquivalentTo("C | D");
    }

    @Test
    public void testCommonSubtypesApi() {
        TestTypeSystem system = new TestTypeSystemBuilder()
                .atom("Any").asTop()
                .atom("None").asBottom()
                .atom("A").asAbstract()
                .atom("B").asAbstract()
                .atom("C").satisfies("A", "B")
                .atom("D").satisfies("A", "B")
                .build();

        Type a = system.type("A");
        Type b = system.type("B");
        Set<Type> common = system.commonSubtypes(a, b);

        // Should contain C and D
        org.junit.jupiter.api.Assertions.assertEquals(2, common.size());
        org.junit.jupiter.api.Assertions.assertTrue(common.contains(system.type("C")));
        org.junit.jupiter.api.Assertions.assertTrue(common.contains(system.type("D")));
    }
}
