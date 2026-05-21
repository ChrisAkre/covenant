package dev.akre.covenant.api;

public sealed interface TypeParameter {
    Type type();

    record Positional(Type type, Integer index, boolean variadic) implements TypeParameter {}

    record Named(Type type, String name, boolean optional) implements TypeParameter {}

    record Constrained(Type type, String keyword, String value, boolean optional) implements TypeParameter {}

    record Spread(Type type) implements TypeParameter {}

    static TypeParameter spread(Type type) {
        return new Spread(type);
    }

    static TypeParameter matches(String name, Type type) {
        return new Constrained(type, "matches", name, true);
    }

    static TypeParameter named(String name, Type type) {
        return name.endsWith("?")
                ? new Named(type, name.substring(0, name.length() - 1), true)
                : new Named(type, name, false);
    }

    static TypeParameter at(Integer position, Type type) {
        return at(position, type, false);
    }

    static TypeParameter at(Integer position, Type type, boolean varArg) {
        return new Positional(type, position, false);
    }
}
