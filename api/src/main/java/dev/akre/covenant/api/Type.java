package dev.akre.covenant.api;

import java.util.Arrays;
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

    default boolean isNumeric() {
        return attributes().contains(TypeAttribute.NUMERIC_SEMANTICS);
    }

    default boolean isString() {
        return attributes().contains(TypeAttribute.STRING_SEMANTICS);
    }

    default boolean isBoolean() {
        return attributes().contains(TypeAttribute.BOOLEAN_SEMANTICS);
    }

    default boolean isTop() {
        return attributes().contains(TypeAttribute.TOP_SEMANTICS);
    }

    default boolean isBottom() {
        return attributes().contains(TypeAttribute.BOTTOM_SEMANTICS);
    }

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

        default GenericType construct(TypeParameter... genericParameters) {
            return construct(Arrays.asList(genericParameters));
        }
    }
}
