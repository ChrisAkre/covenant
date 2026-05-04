package dev.akre.covenant.api;

/**
 * Represents a parameter passed to a TypeConstructor.
 */
public sealed interface Parameter {

    Integer index();

    /**
     * A positional type parameter, e.g., T in Array<T>.
     */
    record Positional(Integer index, boolean variadic) implements Parameter {}

    /**
     * A named field parameter, e.g., id: String in Object<id: String>.
     */
    record Named(String name, Integer index, boolean optional) implements Parameter {}

    /**
     * A constrained field parameter, e.g., [matches /ext_/]: Int in Object<[matches /ext_/]: Int>.
     */
    record Constrained(String keyword, String value, Integer index, boolean optional) implements Parameter {}

    /**
     * A spread parameter, e.g., ... in Object<id: String, ...>.
     */
    record Spread(Integer index) implements Parameter {
        public Spread() {
            this(null);
        }
    }
}
