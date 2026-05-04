import subprocess
import os

java_code = """
package dev.akre.covenant.types;

import dev.akre.covenant.types.parser.Lexer;
import dev.akre.covenant.types.parser.Parser;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.JsonTypeSystem;
import org.junit.jupiter.api.Test;

public class TestParser {
    @Test
    public void test() {
        AbstractTypeSystem system = JsonTypeSystem.INSTANCE;
        try {
            system.expression("Object<a: matches \\\".*\\\", ...>");
            System.out.println("Success!");

            system.expression("Object<[matches \\\".*\\\"]: String, ...>");
            System.out.println("Success 2!");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception: " + e.getMessage());
        }
    }
}
"""
with open("core/src/test/java/dev/akre/covenant/types/TestParser.java", "w") as f:
    f.write(java_code)
