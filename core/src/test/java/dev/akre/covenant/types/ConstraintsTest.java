package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class ConstraintsTest {

    @Test
    public void testIntConstraints() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("gt 0").isEquivalentTo("gt 0");
        system.assertThat("gt 0 | gt 1").isEquivalentTo("gt 0");
        system.assertThat("gt 0 & gt 1").isEquivalentTo("gt 1");
        system.assertThat("gt 0 | eq 1").isEquivalentTo("gt 0");
        system.assertThat("gt 0 | eq 1").notSatisfies("eq 1");
        system.assertThat("gt 0 & eq 1").isEquivalentTo("eq 1");
    }

    @Test
    public void testLiteralConstraints() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("1 & ( Int & ( gt 0 ) )").isEquivalentTo("Int & (eq 1)");
    }

    @Test
    public void testFloatConstraints() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("gt 0.5").isEquivalentTo("gt 0.5");
        system.assertThat("gt 0.5 & lt 1.5").isEquivalentTo("gt 0.5 & lt 1.5");
        system.assertThat("1.0").isEquivalentTo("Float & eq 1.0");
        system.assertThat("1.0 & gt 0.5").isEquivalentTo("Float & eq 1.0");
    }

    @Test
    public void testMixedNumericConstraints() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        // Int is a subtype of Float in JsonTypeSystem
        system.assertThat("Int & gt 0.5").isEquivalentTo("Int & gt 0.5");
        system.assertThat("1.0 & Int").isEquivalentTo("Int & eq 1.0");

        // FIXME: Int & eq 1.5 should probably be bottom, but we don't have integer-ness enforcement yet
        // system.assertThat("1.5 & Int").isEquivalentTo("bottom");
    }

    @Test
    public void testStringConstraints() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("\"foo\"").isEquivalentTo("String & eq \"foo\"");
        system.assertThat("gt \"abc\"").isEquivalentTo("gt \"abc\"");
        system.assertThat("\"abc\" & gt \"abc\"").isEquivalentTo("bottom");
        system.assertThat("\"def\" & gt \"abc\"").isEquivalentTo("String & eq \"def\"");
    }

    @Test
    public void testBooleanConstraints() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("true").isEquivalentTo("Bool & eq true");
        system.assertThat("false").isEquivalentTo("Bool & eq false");
        system.assertThat("true & false").isEquivalentTo("bottom");
        system.assertThat("true & eq true").isEquivalentTo("Bool & eq true");
        system.assertThat("true & neq true").isEquivalentTo("bottom");
    }

    @Test
    public void testRegexConstraints() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("matches \"^foo.*\"").isEquivalentTo("matches \"^foo.*\"");
        system.assertThat("nmatches \"^foo.*\"").isEquivalentTo("nmatches \"^foo.*\"");

        system.assertThat("\"foobar\"").satisfies("matches \"^foo.*\"");
        system.assertThat("\"barfoo\"").notSatisfies("matches \"^foo.*\"");

        system.assertThat("\"foobar\"").notSatisfies("nmatches \"^foo.*\"");
        system.assertThat("\"barfoo\"").satisfies("nmatches \"^foo.*\"");

        system.assertThat("\"foobar\" & matches \"^foo.*\"").isEquivalentTo("String & eq \"foobar\"");
        system.assertThat("\"barfoo\" & matches \"^foo.*\"").isEquivalentTo("bottom");

        system.assertThat("matches \"^foo.*\" & nmatches \"^foo.*\"").isEquivalentTo("bottom");
    }
}
