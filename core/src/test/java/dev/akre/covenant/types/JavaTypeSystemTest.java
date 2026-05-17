package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JavaTypeSystemTest {
    @Test
    public void testContractValid() {
        assertDoesNotThrow(() -> {
            JavaTypeSystem.checkContract(JavaTypeSystem.INSTANCE);
        });
    }

    @Test
    public void testContractInvalid() {
        AbstractTypeSystem badSystem = new TypeSystemBuilderImpl()
                .atom("top").asTop()
                .atom("bottom").asBottom()
                .build();

        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            JavaTypeSystem.checkContract(badSystem);
        });
        assertTrue(ex.getMessage().contains("missing required Java type"));
    }
}
