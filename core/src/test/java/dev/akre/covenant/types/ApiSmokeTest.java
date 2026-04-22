package dev.akre.covenant.types;

import static org.junit.jupiter.api.Assertions.*;

import dev.akre.covenant.api.Parameter;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeParameter;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ApiSmokeTest {

    @Test
    public void testApiInterfaces() {
        AbstractTypeSystem ts = JsonTypeSystem.INSTANCE;

        // Test Template lookup
        Type.TemplateType arrayTemplate = ts.template("Array");
        assertNotNull(arrayTemplate);
        assertEquals("Array", arrayTemplate.repr());

        // Test Construction via API
        Type intType = ts.type("Int");
        Type.GenericType intArray =
                arrayTemplate.construct(List.of(new TypeParameter(intType, new Parameter.Positional(0, false))));

        assertNotNull(intArray);
        assertTrue(intArray.isArray());
        assertEquals("Array<Int>", intArray.repr());

        // Test Generic Parameters retrieval
        List<TypeParameter> params = intArray.genericParameters();
        assertEquals(1, params.size());
        assertTrue(params.get(0).parameter() instanceof Parameter.Positional);
        Parameter.Positional p0 = (Parameter.Positional) params.get(0).parameter();
        assertEquals("Int", params.get(0).type().repr());
        assertFalse(p0.variadic());
    }

    @Test
    public void testObjectConstruction() {
        AbstractTypeSystem ts = JsonTypeSystem.INSTANCE;
        Type.TemplateType objectTemplate = ts.template("Object");
        Type stringType = ts.type("String");
        Type intType = ts.type("Int");

        Type.GenericType personType = objectTemplate.construct(List.of(
                new TypeParameter(stringType, new Parameter.Named("name", 0, false)),
                new TypeParameter(intType, new Parameter.Named("age", 0, false))));

        assertNotNull(personType);
        assertTrue(personType.isObject());
        // Note: The internal repr might vary depending on how it was constructed,
        // but it should contain name and age.
        assertTrue(personType.repr().contains("name: String"));
        assertTrue(personType.repr().contains("age: Int"));

        List<TypeParameter> params = personType.genericParameters();
        assertEquals(2, params.size());

        TypeParameter pName = params.stream()
                .filter(p ->
                        p.parameter() instanceof Parameter.Named n && n.name().equals("name"))
                .findFirst()
                .orElseThrow();
        assertEquals("String", pName.type().repr());
    }

    @Test
    public void testSetOperations() {
        AbstractTypeSystem ts = JsonTypeSystem.INSTANCE;
        Type stringType = ts.type("String");
        Type intType = ts.type("Int");
        Type nullType = ts.type("Null");

        // Union
        Type stringOrInt = stringType.union(intType);
        assertEquals("Int | String", stringOrInt.repr());

        // Intersect
        Type empty = stringType.intersect(intType);
        assertEquals("bottom", empty.repr());

        // Negate
        Type notNull = nullType.negate();
        assertEquals("~Null", notNull.repr());

        // Complex
        Type complex = stringType.union(intType).intersect(notNull);
        // The normalizer distributes the intersection: Int & ~Null | String & ~Null
        assertEquals("Int & ~Null | String & ~Null", complex.repr());
    }
}
