package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class TypeTheoryTest {

    @Test
    public void testAxioms() {
        TestTypeSystem system = new TestTypeSystemBuilder()
                .atom("top")
                .asTop()
                .atom("bottom")
                .asBottom()
                .atom("A")
                .atom("B")
                .atom("C")
                .build();

        // Reflexivity
        system.assertThat("A").satisfies("A");

        // The Universal Set (Top)
        system.assertThat("A").satisfies("top");
        system.assertThat("top").satisfies("top");
        system.assertThat("top").notSatisfies("A");

        // The Empty Set (Bottom / Contradiction)
        system.assertThat("bottom").satisfies("A");
        system.assertThat("bottom").satisfies("bottom");
        system.assertThat("A").notSatisfies("bottom");

        // ==========================================
        // 2. UNIONS (The Least Upper Bound)
        // ==========================================

        // Subsumption
        system.assertThat("A").satisfies("A | B");
        system.assertThat("B").satisfies("A | B");
        system.assertThat("A | B").notSatisfies("A");

        // Idempotence & Commutativity
        system.assertThat("A | A").satisfies("A");
        system.assertThat("A").satisfies("A | A");
        system.assertThat("A | B").satisfies("B | A");

        // Top/Bottom Identity
        system.assertThat("A | bottom").satisfies("A");
        system.assertThat("A").satisfies("A | bottom");
        system.assertThat("A | top").satisfies("top");
        system.assertThat("top").satisfies("A | top");

        // ==========================================
        // 3. INTERSECTIONS (The Greatest Lower Bound)
        // ==========================================

        // Narrowing
        system.assertThat("A & B").satisfies("A");
        system.assertThat("A & B").satisfies("B");
        system.assertThat("A").notSatisfies("A & B");

        // Idempotence & Commutativity
        system.assertThat("A & A").satisfies("A");
        system.assertThat("A").satisfies("A & A");
        system.assertThat("A & B").satisfies("B & A");

        // Top/Bottom Identity
        system.assertThat("A & top").satisfies("A");
        system.assertThat("A").satisfies("A & top");
        system.assertThat("A & bottom").satisfies("bottom");

        // ==========================================
        // 4. DISTRIBUTIVITY
        // ==========================================

        // Intersection distributes over Union: A & (B | C) == (A & B) | (A & C)
        system.assertThat("A & (B | C)").satisfies("(A & B) | (A & C)");
        system.assertThat("(A & B) | (A & C)").satisfies("A & (B | C)");

        // Union distributes over Intersection: A | (B & C) == (A | B) & (A | C)
        system.assertThat("A | (B & C)").satisfies("(A | B) & (A | C)");
        system.assertThat("(A | B) & (A | C)").satisfies("A | (B & C)");

        // ==========================================
        // 5. NEGATION & COMPLEMENTS
        // ==========================================

        // Double Negation Elimination
        system.assertThat("~(~A)").satisfies("A");
        system.assertThat("A").satisfies("~(~A)");

        // Bound Inversion
        system.assertThat("~top").satisfies("bottom");
        system.assertThat("~bottom").satisfies("top");

        // Law of Non-Contradiction (Intersection with complement is empty)
        system.assertThat("A & ~A").satisfies("bottom");

        // Law of Excluded Middle (Union with complement is universal)
        system.assertThat("top").satisfies("A | ~A");

        // Contrapositive (If A <: A | B, then ~(A | B) <: ~A)
        system.assertThat("~(A | B)").satisfies("~A");
        system.assertThat("~A").notSatisfies("~(A | B)");

        // ==========================================
        // 6. DE MORGAN'S LAWS
        // ==========================================

        // ~(A | B) == ~A & ~B
        system.assertThat("~(A | B)").satisfies("~A & ~B");
        system.assertThat("~A & ~B").satisfies("~(A | B)");

        // ~(A & B) == ~A | ~B
        system.assertThat("~(A & B)").satisfies("~A | ~B");
        system.assertThat("~A | ~B").satisfies("~(A & B)");
    }

    @Test
    public void testIdentities() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        // Self-satisfaction
        system.assertThat("String").satisfies("String");
        system.assertThat("'foo'").satisfies("'foo'");
        system.assertThat("top").satisfies("top");

        // Top and Bottom
        system.assertThat("bottom").isBottom();
        system.assertThat("String").satisfies("top");
        system.assertThat("Any").satisfies("top");
        system.assertThat("top").satisfies("Any");
        system.assertThat("bottom").satisfies("Any");
    }

    @Test
    public void testLiteralOrthogonality() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("'foo'").notSatisfies("'bar'");
        system.assertThat("'foo'").notSatisfies("String"); // Orthogonal atoms
    }

    @Test
    public void testNominalUniverse() {
        TestTypeSystem system = new TestTypeSystemBuilder()
                .atom("top")
                .asTop()
                .atom("bottom")
                .asBottom()
                .atom("A")
                .atom("B")
                .atom("C")
                .build();

        system.assertThat("~A").satisfies("B | C");
        system.assertThat("~A & ~B").satisfies("C");
        system.assertThat("~A & ~B & ~C").isBottom();
    }

    @Test
    public void testFunctionAxioms() {
        TestTypeSystem system = new TestTypeSystemBuilder()
                .atom("top")
                .asTop()
                .atom("bottom")
                .asBottom()
                .atom("A")
                .atom("B")
                .atom("C")
                .atom("P1")
                .atom("P2")
                .atom("R1")
                .atom("R2")
                .functionAlias("f", "(P1) -> R1", "(P2) -> R2")
                .build();

        // ==========================================
        // 1. REFLEXIVITY
        // ==========================================
        system.assertThat("(A) -> B").satisfies("(A) -> B");

        // ==========================================
        // 2. COVARIANCE (Return Types)
        // ==========================================
        // If the return type is narrower, the function is narrower.
        // Since R1 & R2 <: R1, then (A) -> (R1 & R2) <: (A) -> R1
        system.assertThat("(A) -> (R1 & R2)").satisfies("(A) -> R1");
        system.assertThat("(A) -> R1").notSatisfies("(A) -> (R1 & R2)");

        // ==========================================
        // 3. CONTRAVARIANCE (Parameter Types)
        // ==========================================
        // If the parameter type is wider, the function is narrower (it can handle more inputs).
        // Since P1 <: P1 | P2, then (P1 | P2) -> B <: (P1) -> B
        system.assertThat("(P1 | P2) -> B").satisfies("(P1) -> B");
        system.assertThat("(P1) -> B").notSatisfies("(P1 | P2) -> B");

        // ==========================================
        // 4. ARROW DISTRIBUTIVITY (Algebraic Equivalence)
        // ==========================================

        // Union of Parameters:
        // A function accepting A or B must satisfy the requirements of a function accepting A AND a function accepting
        // B.
        //        system.assertThat("(A | B) -> C").satisfies("((A) -> C) & ((B) -> C)");
        //        system.assertThat("((A) -> C) & ((B) -> C)").satisfies("(A | B) -> C");

        // ==========================================
        // 5. UNIVERSAL FUNCTION BOUNDS
        // ==========================================

        // The Top Function: Accepts nothing (vacuously), returns anything. Every function is a subtype of this.
        system.assertThat("(A) -> B").satisfies("(bottom) -> top");

        // The Bottom Function: Accepts anything, never returns. It is a subtype of every function.
        system.assertThat("(top) -> bottom").satisfies("(A) -> B");

        system.assertThat("f").withArgs("P1").evaluatesTo("R1");
        system.assertThat("f").withArgs("P2").evaluatesTo("R2");
        system.assertThat("f").withArgs("P1|P2").evaluatesTo("R1|R2");
    }
}
