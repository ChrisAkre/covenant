package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

public class ReDoSProtectionTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testReDoSProtection() {
        TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);

        // A classic ReDoS pattern: (a+)+$
        // With java.util.regex, matching this against a long string of 'a's followed by '!' would take exponential time.
        // RE2J should handle this in linear time.
        String maliciousRegex = "(a+)+$";
        String input = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!";

        // Using the DSL via TestTypeSystem which eventually calls our fixed matches() methods.
        // We can test ValueConstraint (StringConstraint)
        String typeExpr = "matches \"" + maliciousRegex + "\"";

        // This should not hang and should return false (or true if it somehow matches, but the point is no hang)
        system.assertThat("\"" + input + "\"").notSatisfies(typeExpr);
    }
}
