package dev.akre.covenant.api;

import java.util.EnumSet;
import java.util.List;

/**
 * A minimal representation of a type for use in the API and codegen.
 */
@SuppressWarnings("unused")
public interface Type {

    TypeSystem system();

    /**
     * @return a text representation of this type.
     */
    String repr();

    /**
     * @return true if this type has numeric semantics.
     */
    boolean isNumeric();

    /**
     * @return true if this type has string semantics.
     */
    boolean isString();

    /**
     * @return true if this type has boolean semantics.
     */
    boolean isBoolean();

    /**
     * @return true if this type represents an object.
     */
    boolean isObject();

    /**
     * @return true if this type represents an array.
     */
    boolean isArray();

    /**
     * @param other another type to check assignability from.
     * @return true if other is assignable to this type.
     */
    boolean isAssignableFrom(Type other);

    Type termAt(String value);

    Type negate();

    Type union(Type other);

    Type intersect(Type other);

    EnumSet<TypeAttribute> attributes();

    default boolean is(TypeAttribute typeAttribute) {
        return attributes().contains(typeAttribute);
    }

    interface TypeFunction extends Type {
        record Overload(Type returnType, List<Type> parameters) {}

        Type evaluate(Type... args);

        List<Overload> overloads(Type... args);
    }

    interface GenericType extends Type {
        List<TypeParameter> genericParameters();

        TemplateType template();
    }

    interface TemplateType extends Type {
        GenericType construct(List<TypeParameter> genericParameters);
    }
}
