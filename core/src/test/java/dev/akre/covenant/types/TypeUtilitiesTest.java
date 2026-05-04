package dev.akre.covenant.types;

import dev.akre.covenant.api.Type;
import org.junit.jupiter.api.Test;

public class TypeUtilitiesTest {

    private static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testConcatIncompatibleTypes() {
        Type.GenericType arrayType = (Type.GenericType) SYSTEM.expression("Array<Int>");
        Type.GenericType objectType = (Type.GenericType) SYSTEM.expression("Object<a: Int>");
        Type.GenericType intType = (Type.GenericType) SYSTEM.type("Int");

        // Array + Object -> bottom
        SYSTEM.assertThat(arrayType).concat(objectType).isBottom();

        // Object + Array -> bottom
        SYSTEM.assertThat(objectType).concat(arrayType).isBottom();

        // Object + Int -> bottom
        SYSTEM.assertThat(objectType).concat(intType).isBottom();

        // Int + Int -> bottom
        SYSTEM.assertThat(intType).concat(intType).isBottom();
    }
}
