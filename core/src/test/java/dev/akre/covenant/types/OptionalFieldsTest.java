package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class OptionalFieldsTest {

    @Test
    public void testOptionalFields() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        system.assertThat("Object<>").satisfies("Object<foo?: String>");
        system.assertThat("Object<foo: Null>").notSatisfies("Object<foo?: String>");
        system.assertThat("Object<foo: String>").satisfies("Object<foo?: String>");
        system.assertThat("Object<foo?: String>").satisfies("Object<foo?: String>");
    }
}
