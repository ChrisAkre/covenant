package dev.akre.covenant.api;

/**
 * Represents a parameter passed to a TypeConstructor.
 */
public sealed interface Parameter {

    /**
     * A positional type parameter, e.g., T in {@code Array<T>}.
     */
    record Positional(Integer index, boolean variadic) implements Parameter {}

    /**
     * A named field parameter, e.g., {@code id: String} in {@code Object<id: String>}.
     */
    record Named(String name, boolean optional) implements Parameter {}

    /**
     * A constrained field parameter, e.g., {@code [matches /ext_/]: Int} in {@code Object<[matches /ext_/]: Int>}.
     */
    record Constrained(String keyword, String value, boolean optional) implements Parameter {}

    /**
     * A spread parameter, e.g., {@code ...} in {@code Object<id: String, ...>}.
     */
    record Spread() implements Parameter {}
}
