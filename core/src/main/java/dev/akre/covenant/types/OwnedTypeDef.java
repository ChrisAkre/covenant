package dev.akre.covenant.types;

import dev.akre.covenant.api.Parameter;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeAttribute;
import dev.akre.covenant.api.TypeParameter;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A record that pairs a {@link TypeDef} with its owning {@link AbstractTypeSystem}.
 * This provides a fluent API for type operations that require type system context.
 */
@SuppressWarnings("unused")
public record OwnedTypeDef(AbstractTypeSystem system, TypeDef def)
        implements Type, Type.TypeFunction, Type.GenericType, Type.TemplateType {

    @Override
    public boolean isNumeric() {
        return attributes().contains(TypeAttribute.NUMERIC_SEMANTICS);
    }

    @Override
    public boolean isString() {
        return attributes().contains(TypeAttribute.STRING_SEMANTICS);
    }

    @Override
    public boolean isBoolean() {
        return attributes().contains(TypeAttribute.BOOLEAN_SEMANTICS);
    }

    @Override
    public boolean isObject() {
        return def instanceof GenericTypeDef g
                && g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT;
    }

    @Override
    public boolean isArray() {
        return def instanceof GenericTypeDef g
                && (g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.ARRAY
                        || g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.POSITIONAL);
    }

    @Override
    public boolean isAssignableFrom(Type other) {
        if (other instanceof OwnedTypeDef o) {
            return isAssignableFrom(o);
        }
        return false;
    }

    public boolean isAssignableTo(OwnedTypeDef other) {
        return system.isAssignableTo(this, other);
    }

    public boolean isAssignableFrom(OwnedTypeDef other) {
        return system.isAssignableTo(other, this);
    }

    @Override
    public OwnedTypeDef negate() {
        return system.negate(this);
    }

    @Override
    public Type union(Type other) {
        return system.union(this, system.adopt(other));
    }

    @Override
    public Type intersect(Type other) {
        return system.intersect(this, system.adopt(other));
    }

    public EnumSet<TypeAttribute> attributes() {
        return def.attributes();
    }

    public boolean equivalentTo(OwnedTypeDef other) {
        return isAssignableTo(other) && isAssignableFrom(other);
    }

    public String repr() {
        return def.repr();
    }

    @Override
    public @NonNull String toString() {
        return def.repr();
    }

    public OwnedTypeDef termAt(String segment) {
        return system.wrap(TypeSystemUtils.termAt(system, def, segment));
    }

    public OwnedTypeDef termAt(OwnedTypeDef segment) {
        return system.wrap(TypeSystemUtils.termAt(system, def, segment.def));
    }

    public OwnedTypeDef termAt(List<String> segments) {
        return system.wrap(TypeSystemUtils.termAt(system, def, segments));
    }

    @Override
    public Type.TypeFunction evaluate(Type... args) {
        return system.evaluate(this, args);
    }

    @Override
    public List<Type.TypeFunction.Overload> overloads(Type... args) {
        return system.overloads(this, args);
    }

    @Override
    public List<TypeParameter> genericParameters() {
        if (def instanceof GenericTypeDef g) {
            return g.parameters().stream()
                    .map(tp -> {
                        Type type = tp.type() != null ? system.wrap(tp.type()) : null;
                        return new TypeParameter(type, tp.parameter());
                    })
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public Type.TemplateType template() {
        if (def instanceof GenericTypeDef g) {
            return system.wrap(g.template());
        }
        throw new UnsupportedOperationException("Not a generic type");
    }

    @Override
    public Type.GenericType construct(List<TypeParameter> genericParameters) {
        if (def instanceof dev.akre.covenant.types.TemplateType template) {
            List<TypeDef> members = new ArrayList<>();
            List<Parameter> parameters = new ArrayList<>();
            for (TypeParameter tp : genericParameters) {
                Parameter p = tp.parameter();
                if (tp.type() != null) {
                    int newIndex = members.size();
                    members.add(system.unwrap(tp.type()));
                    p = switch (p) {
                        case Parameter.Positional pos -> new Parameter.Positional(newIndex, pos.variadic());
                        case Parameter.Named n -> new Parameter.Named(n.name(), newIndex, n.optional());
                        case Parameter.Constrained c ->
                            new Parameter.Constrained(c.keyword(), c.value(), newIndex, c.optional());
                        case Parameter.Spread s -> new Parameter.Spread(newIndex);
                    };
                }
                parameters.add(p);
            }
            return system.construct(template.name(), members, parameters);
        }
        throw new UnsupportedOperationException(
                "Not a template type: " + (def == null ? "null" : def.getClass().getName()));
    }
}
