package dev.akre.covenant.types;

import static org.junit.jupiter.api.Assertions.*;

import dev.akre.covenant.api.Parameter;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeParameter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TypeSystemSmokeTest {

    @Test
    public void testStringAndIntIsBottom() {
        TestTypeSystem system = new TestTypeSystemBuilder()
                .atom("top")
                .asTop()
                .atom("bottom")
                .asBottom()
                .atom("String")
                .atom("Int")
                .build();

        system.assertThat("String & Int").satisfies("bottom");
    }

    @Test
    public void testAlgebra() {
        TestTypeSystem system = new TestTypeSystemBuilder()
                .atom("Float")
                .atom("Int")
                .satisfies("Float")
                .build();

        system.assertThat("Int & Float").satisfies("Int");
        system.assertThat("Int | Float").satisfies("Float");
    }

    @Test
    public void testOptional() {
        TestTypeSystem system =
                new TestTypeSystemBuilder().atom("String").atom("Null").asNull().build();

        system.assertThat("String").satisfies("String?");
        system.assertThat("Null").satisfies("String?");
        system.assertThat("String?").satisfiedBy("String");
        system.assertThat("String").notSatisfiedBy("Null");
    }

    @Test
    public void testJsonTypeSystem() {
        AbstractTypeSystem json = JsonTypeSystem.INSTANCE;
        assertNotNull(json.type("String"));
        assertNotNull(json.type("Number"));
        assertNotNull(json.type("Int"));
        assertNotNull(json.type("Float"));

        // Verify hierarchy
        assertTrue(((OwnedTypeDef) json.find("Int").orElseThrow())
                .isAssignableTo((OwnedTypeDef) json.find("Float").orElseThrow()));
        assertTrue(((OwnedTypeDef) json.find("Int").orElseThrow())
                .isAssignableTo((OwnedTypeDef) json.find("Number").orElseThrow()));
        assertTrue(((OwnedTypeDef) json.find("Float").orElseThrow())
                .isAssignableTo((OwnedTypeDef) json.find("Number").orElseThrow()));
    }

    @Test
    public void testFooString() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .typeAlias("FooString", "String & 'foo'")
                .build();

        system.assertThat("FooString").satisfies("String").satisfies("'foo'");
    }

    @Test
    public void testContradiction() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE).build();

        system.assertThat("Null & ~Null").satisfies("bottom");
        system.assertThat("String & ~String").satisfies("bottom");
    }

    @Test
    public void testScalarTermNavigation() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .atom("Decimal")
                .atom("CurrencyCode")
                .atom("Scalar")
                .positionalPattern()
                .minParams(2)
                .build();

        system.assertThat("Scalar<Decimal, 'USD'>").term(0).satisfies("Decimal");
        system.assertThat("Scalar<Decimal, 'USD'>").term(1).satisfies("'USD'");

        // Raw literal term does NOT satisfy a branded identity
        system.assertThat("Scalar<Decimal, 'USD'>").term(1).notSatisfies("CurrencyCode & 'USD'");
    }

    @Test
    public void testQuotedIdentifiers() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .atom("SomeQuotedName")
                .typeAlias("QuotedAlias", "'SomeQuotedName'")
                .build();

        system.assertThat("Object<>").term("'no exists'").isNotAssignableFrom("String");

        system.assertThat("Object<'1': Int, 'property with spaces': String>")
                .term("'1'")
                .isAssignableTo("Int")
                .term("'2'")
                .isBottom()
                //                .term("'property with spaces'").isAssignableTo("String")
                .term("'no exists'")
                .isBottom();

        system.assertThat("QuotedAlias").isAssignableTo("'SomeQuotedName'");
    }

    @Test
    public void testFunctions() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        system.assertThat("(Null, T2) -> T2 & <T1: ~Null>(T1, Any) -> T1")
                .printsLike("(<T2>(Null, T2) -> T2) & (<T1: ~Null>(T1, Any) -> T1)")
                .withArgs("Null", "Int").evaluatesTo("Int");

        system.assertThat("(T1, T2) -> T1 & ~Null | T2")
                .printsLike("<T1, T2>(T1, T2) -> T1 & ~Null | T2")
                .withArgs("Null", "Int").evaluatesTo("Int");

        system.assertThat("(String) -> ((Int) -> Int & (Null) -> Null)")
                .printsLike("(String) -> (Int) -> Int & (Null) -> Null");
    }

    @Test
    @Disabled
    public void testSpread() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        Type stringsObject = system.template("Object").construct(TypeParameter.spread(system.type("String")));
        system.assertThat(stringsObject).printsLike("Object<...String>");

    }
}
