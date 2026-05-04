package dev.akre.covenant.api;

/**
 * Fluent builder for creating a TypeSystem.
 */
public interface TypeSystemBuilder {

    /**
     * Registers a new atomic type identity.
     */
    TypeSystemBuilder atom(String name);

    /**
     * Declares the current atom as the universal top type.
     */
    TypeSystemBuilder asTop();

    /**
     * Declares the current atom as the universal bottom type.
     */
    TypeSystemBuilder asBottom();

    /**
     * Opts the current atom into null semantics.
     */
    TypeSystemBuilder asNull();

    /**
     * Opts the current atom into numeric range constraint capabilities.
     */
    TypeSystemBuilder supportsNumericBounds();

    /**
     * Opts the current atom into textual length constraint capabilities.
     */
    TypeSystemBuilder supportsTextualBounds();

    /**
     * Declares that the current atom satisfies the requirements of the specified parent.
     */
    TypeSystemBuilder satisfies(String parent);

    /**
     * Declares that the current atom is satisfied by the specified child.
     */
    TypeSystemBuilder satisfiedBy(String childName);

    /**
     * Configures the current atom with a positional parameter constructor pattern.
     */
    TypeSystemBuilder positionalPattern();

    /**
     * Configures the current atom with an array-like constructor pattern.
     */
    TypeSystemBuilder arrayPattern();

    /**
     * Configures the current atom with an object-like constructor pattern.
     */
    TypeSystemBuilder objectPattern();

    /**
     * Sets the minimum number of parameters for the current constructor.
     */
    TypeSystemBuilder minParams(int min);

    /**
     * Sets the maximum number of parameters for the current constructor.
     */
    TypeSystemBuilder maxParams(int max);

    /**
     * Registers a type alias.
     */
    TypeSystemBuilder typeAlias(String name, String expression);

    /**
     * Registers a custom constraint parser.
     */
    default TypeSystemBuilder registerConstraint(Object parser) {
        return this;
    }

    /**
     * Registers an overloaded function alias with one or more signatures.
     */
    TypeSystemBuilder functionAlias(String name, String... signatures);

    /**
     * Builds the final TypeSystem instance.
     */
    TypeSystem build();
}
