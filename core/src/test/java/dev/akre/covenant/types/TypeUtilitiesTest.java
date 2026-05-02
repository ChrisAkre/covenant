package dev.akre.covenant.types;

import static org.junit.jupiter.api.Assertions.*;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeUtilities;
import org.junit.jupiter.api.Test;

public class TypeUtilitiesTest {

    private static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testConcatArrayTypes() {
        Type.GenericType arrayInt = SYSTEM.expression("Array<Int>");
        Type.GenericType arrayString = SYSTEM.expression("Array<String>");
        Type.GenericType arrayEmpty = SYSTEM.expression("Array<>");
        Type.GenericType arrayIntVariadic = SYSTEM.expression("Array<Int...>");
        Type.GenericType arrayBool = SYSTEM.expression("Array<Bool>");

        // Fixed + Fixed
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(arrayInt, arrayString))
                .isEquivalentTo("Array<Int, String>");

        // Empty + Fixed
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(arrayEmpty, arrayInt))
                .isEquivalentTo("Array<Int>");
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(arrayInt, arrayEmpty))
                .isEquivalentTo("Array<Int>");

        // Left Variadic + Fixed
        // Array<Int...> + Array<Bool> -> Array<(Int | Bool)...>
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(arrayIntVariadic, arrayBool))
                .isEquivalentTo("Array<(Int | Bool)...>");

        // Fixed + Variadic
        // Array<Int> + Array<Int...> -> Array<Int, Int...>
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(arrayInt, arrayIntVariadic))
                .isEquivalentTo("Array<Int, Int...>");

        // Variadic + Variadic
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(arrayIntVariadic, SYSTEM.expression("Array<String...>")))
                .isEquivalentTo("Array<(Int | String)...>");
    }

    @Test
    public void testConcatObjectTypes() {
        Type.GenericType objA = SYSTEM.expression("Object<a: Int>");
        Type.GenericType objB = SYSTEM.expression("Object<b: String>");
        Type.GenericType objAOpt = SYSTEM.expression("Object<a?: Int>");
        Type.GenericType objANew = SYSTEM.expression("Object<a: Float>");
        Type.GenericType objOpen = SYSTEM.expression("Object<...>");

        // Simple concat
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(objA, objB))
                .isEquivalentTo("Object<a: Int, b: String>");

        // Overlapping properties (Right overrides Left)
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(objA, objANew))
                .isEquivalentTo("Object<a: Float>");

        // Optional property on left, required on right -> required (from right)
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(objAOpt, objA))
                .isEquivalentTo("Object<a: Int>");

        // Required property on left, optional on right -> union type and keeps left optionality
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(objA, objAOpt))
                .isEquivalentTo("Object<a: Int>");

        // Open objects
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(objA, objOpen))
                .isEquivalentTo("Object<...>");

        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(objOpen, objA))
                .isEquivalentTo("Object<a: Int, ...>");

        // Open object with open object (Any is Top)
        Type.GenericType objOpenAny = SYSTEM.expression("Object<...>");
        SYSTEM.assertThat(TypeUtilities.concatGenericTypes(objOpenAny, objOpen))
                .isEquivalentTo("Object<...>");
    }

    @Test
    public void testIncompatibleTypes() {
        Type.GenericType arrayInt = SYSTEM.expression("Array<Int>");
        Type.GenericType objA = SYSTEM.expression("Object<a: Int>");

        assertThrows(IllegalArgumentException.class, () -> TypeUtilities.concatGenericTypes(arrayInt, objA));
        assertThrows(IllegalArgumentException.class, () -> TypeUtilities.concatGenericTypes(objA, arrayInt));
    }
}
