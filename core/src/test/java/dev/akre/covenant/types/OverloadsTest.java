package dev.akre.covenant.types;

import org.junit.jupiter.api.Test;

public class OverloadsTest {

    @Test
    public void testToStringExample() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("to_string", "(<T: ~Null>(T) -> String) & ((Null) -> Null)")
                .build();

        // to_string(Int | Null) should have overloads (Int) -> String and (Null) -> Null
        system.assertThat("to_string")
                .withArgs("Int | Null")
                .overloadsTo("(Int) -> String", "(Null) -> Null")
                .evaluatesTo("String?");
    }

    @Test
    public void testGenericOverloads() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("Id", "<T>(T) -> T")
                .build();

        // Id(String | Int) -> (String) -> String and (Int) -> Int
        system.assertThat("Id")
                .withArgs("String | Int")
                .overloadsTo("(String) -> String", "(Int) -> Int");
    }

    @Test
    public void testConcreteOverloads() {
        TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
                .functionAlias("f", "(Int) -> Int", "(String) -> String")
                .build();

        system.assertThat("f")
                .withArgs("Int | String")
                .overloadsTo("(Int) -> Int", "(String) -> String")
                .evaluatesTo("String|Int");
    }
}
