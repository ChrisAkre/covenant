
package dev.akre.covenant.jolt;

import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.JsonTypeSystem;

public class TestParser {
    public static void main(String[] args) {
        AbstractTypeSystem system = JsonTypeSystem.INSTANCE;
        try {
            System.out.println("Trying expression 1...");
            system.expression("Object<[matches \".*\"]: Object<Range: Number, Value: ?Number, Id: String, ...>, ...>");
            System.out.println("Success!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
