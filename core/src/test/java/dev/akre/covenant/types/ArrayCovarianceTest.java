package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class ArrayCovarianceTest {
    public static final TestTypeSystem SYSTEM = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

    @Test
    public void testArrayCovarianceWithUnions() {
        // Array<Int> should satisfy Array<Int | String>
        SYSTEM.assertThat("Array<Int>")
                .satisfies("Array<Int | String>");
    }

    @Test
    public void testArrayCovarianceWithNull() {
        // This specifically reproduces the Jolt failure in prefixDataToArray.json
        SYSTEM.assertThat("Array<Object<Id: String>>")
                .satisfies("Array<Null | Object<Id: String>>");
    }
}
