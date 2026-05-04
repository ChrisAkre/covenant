package dev.akre.covenant.api;

import java.util.List;

public record TypeParameter(Type type, Parameter parameter) {

    public static TypeParameter spread(Type type) {
        return new TypeParameter(type, new Parameter.Spread());
    }
    public static TypeParameter named(String name, Type type) {
        return name.endsWith("?")
                ? new TypeParameter(type, new Parameter.Named(name.substring(0, name.length()-1), null, true))
                : new TypeParameter(type, new Parameter.Named(name, null, false));
    }

    public static TypeParameter at(String name, Type type) {
        return name.endsWith("...")
                ? new TypeParameter(type, new Parameter.Positional( null, true))
                : new TypeParameter(type, new Parameter.Named(name, null, false));
    }

}
