package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConstraintParsingTest {

    private final TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testLengthConstraintParsing() {
        system.assertThat("minlength 5").isEquivalentTo("minlength 5");
        system.assertThat("maxlength 10").isEquivalentTo("maxlength 10");
        system.assertThat("length 7").isEquivalentTo("length 7");
        system.assertThat("minitems 3").isEquivalentTo("minitems 3");
        system.assertThat("maxitems 8").isEquivalentTo("maxitems 8");
        
        // Combined length constraints
        system.assertThat("minlength 5 & maxlength 10").isEquivalentTo("minlength 5 & maxlength 10");
    }

    @Test
    public void testBooleanConstraintParsing() {
        system.assertThat("eq true").isEquivalentTo("eq true");
        system.assertThat("neq false").isEquivalentTo("neq false");
        system.assertThat("eq false").isEquivalentTo("eq false");
        system.assertThat("neq true").isEquivalentTo("neq true");
    }

    @Test
    public void testNumberConstraintParsing() {
        system.assertThat("gt 5").isEquivalentTo("gt 5");
        system.assertThat("gte 5").isEquivalentTo("gte 5");
        system.assertThat("lt 10").isEquivalentTo("lt 10");
        system.assertThat("lte 10").isEquivalentTo("lte 10");
        system.assertThat("eq 42").isEquivalentTo("eq 42");
        system.assertThat("neq 0").isEquivalentTo("neq 0");
        
        // Literal comparison (requires intersection with base type)
        system.assertThat("Int & eq 42").isEquivalentTo("42");
    }

    @Test
    public void testStringConstraintParsing() {
        system.assertThat("eq \"hello\"").isEquivalentTo("eq \"hello\"");
        system.assertThat("neq \"world\"").isEquivalentTo("neq \"world\"");
        system.assertThat("matches \"^a.*\"").isEquivalentTo("matches \"^a.*\"");
        
        // String literal comparison
        system.assertThat("String & eq \"hello\"").isEquivalentTo("\"hello\"");
        
        // Symbol literal comparison (Symbols are NOT StringConstraints)
        system.assertThat("'foo'").isEquivalentTo("'foo'");
        
        // Regex literal
        system.assertThat("matches /^b.*/").isEquivalentTo("matches /^b.*/");
    }

    @Test
    public void testConstraintFirstDibs() {
        // "gt" is a constraint keyword, should be parsed as NumberConstraint(GT, 5)
        // rather than a RefExpr("gt") followed by something else.
        TypeExpr expr = system.parser().parse("gt 5");
        assertTrue(expr instanceof TypeExpr.ConstraintExpr);
        TypeExpr.ConstraintExpr ce = (TypeExpr.ConstraintExpr) expr;
        assertTrue(ce.constraint() instanceof NumberConstraint);
        assertEquals(ValueConstraint.Operator.GT, ((NumberConstraint)ce.constraint()).operator());
    }

    @Test
    public void testLengthConstraintMerging() {
        // Intersecting minlength and maxlength
        system.assertThat("minlength 2 & maxlength 4").isEquivalentTo("minlength 2 & maxlength 4");
        
        // Overlapping ranges
        system.assertThat("minlength 2 & minlength 5").isEquivalentTo("minlength 5");
        system.assertThat("maxlength 10 & maxlength 5").isEquivalentTo("maxlength 5");
        
        // Disjoint ranges
        system.assertThat("minlength 10 & maxlength 5").isEquivalentTo("bottom");
    }
}
