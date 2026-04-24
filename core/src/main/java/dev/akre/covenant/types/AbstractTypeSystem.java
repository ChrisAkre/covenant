package dev.akre.covenant.types;

import static dev.akre.covenant.types.NormalizerUtils.cartesianProduct;
import static dev.akre.covenant.types.NormalizerUtils.unionCollector;
import static java.util.Optional.*;

import dev.akre.covenant.api.Parameter;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeSystem;
import dev.akre.covenant.types.parser.Parser;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public interface AbstractTypeSystem extends TypeSystem {

    default Type type(String name) throws java.util.NoSuchElementException {
        return find(name).orElseThrow(() -> new java.util.NoSuchElementException("Type not found: " + name));
    }

    default java.util.Optional<Type> find(String name) {
        return Optional.ofNullable(typesDef().get(name)).map(this::wrap);
    }

    default Type expression(String expression) {
        return typeExpression(expression);
    }

    default Type.TypeFunction typeFunction(String name) throws java.util.NoSuchElementException {
        TypeDef result = Optional.ofNullable(typesDef().get(name)).orElse(bottomDef());
        if (result instanceof ApplicableDef) {
            return wrap(result);
        } else {
            throw new java.util.NoSuchElementException("Not a function: " + name);
        }
    }

    @Override
    default Type.TemplateType template(String name) throws java.util.NoSuchElementException {
        TypeDef result = Optional.ofNullable(typesDef().get(name))
                .orElseThrow(() -> new java.util.NoSuchElementException("Type not found: " + name));
        if (result instanceof TemplateType) {
            return wrap(result);
        } else {
            throw new java.util.NoSuchElementException("Not a template: " + name);
        }
    }

    /**
     * Returns the complete map of types in this system.
     */
    Map<String, TypeDef> typesDef();

    default List<Parser<TypeExpr>> customConstraints() {
        return List.of();
    }

    default Map<String, Type> types() {
        return Collections.unmodifiableMap(
                typesDef().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> wrap(e.getValue()))));
    }

    /**
     * Returns the universal top type.
     */
    TypeDef topDef();

    default OwnedTypeDef top() {
        return wrap(topDef());
    }

    /**
     * Returns the universal bottom type.
     */
    TypeDef bottomDef();

    default OwnedTypeDef bottom() {
        return wrap(bottomDef());
    }

    /**
     * Returns the null type.
     */
    TypeDef nilDef();

    default OwnedTypeDef nil() {
        return wrap(nilDef());
    }

    /**
     * Unwraps an OwnedTypeDef to its underlying TypeDef, ensuring it belongs to this system.
     */
    default TypeDef unwrap(OwnedTypeDef owned) {
        if (owned == null) return null;
        if (owned.system() != this) {
            throw new IllegalArgumentException("OwnedTypeDef does not belong to this TypeSystem");
        }
        return owned.def();
    }

    default TypeDef unwrap(Type type) {
        if (type == null) return null;
        if (type instanceof OwnedTypeDef o) return unwrap(o);
        throw new IllegalArgumentException(
                "Not an OwnedTypeDef: " + type.getClass().getName());
    }

    /**
     * Constructs a type using the constructor associated with an atom.
     */
    default TypeDef constructDef(String name, List<TypeDef> members, List<Parameter> parameters) {
        TypeDef def = typesDef().get(name);
        if (!(def instanceof TemplateType template) || template.constructor() == null) {
            throw new IllegalArgumentException("Unknown type template or atom: " + name);
        }
        return template.constructor().construct(this, template, members, parameters);
    }

    default OwnedTypeDef construct(String name, List<TypeDef> members, List<Parameter> parameters) {
        return wrap(constructDef(name, members, parameters));
    }


    TypeParser parser();

    /**
     * Parses a type expression string (e.g., "String & ~Null") into a TypeDef.
     */
    default TypeDef typeExpressionDef(String expression) {
        return parser().parseDef(this, expression);
    }



    default OwnedTypeDef typeExpression(String expression) {
        return wrap(typeExpressionDef(expression));
    }

    /**
     * Look up a function by name and evaluate it with a list of TypeDefs.
     */
    @Deprecated
    default TypeDef evaluateDef(String name, List<TypeDef> args) {
        TypeDef type = typesDef().get(name);
        if (type instanceof FunctionType func) {
            return func.evaluate(this, args);
        }
        return bottomDef();
    }

    default TypeDef evaluateDef(String name, TypeDef... args) {
        return evaluateDef(typesDef().get(name), args);
    }

    default TypeDef evaluateDef(TypeDef type, TypeDef... args) {
        if (type instanceof ApplicableDef func) {
            return func.evaluate(this, List.of(args));
        }
        return bottomDef();
    }

    default OwnedTypeDef evaluate(String name, OwnedTypeDef... args) {
        return wrap(evaluateDef(name, Arrays.stream(args).map(this::unwrap).toArray(TypeDef[]::new)));
    }

    default OwnedTypeDef evaluate(String name, Type... args) {
        return wrap(evaluateDef(name, Arrays.stream(args).map(this::unwrap).toArray(TypeDef[]::new)));
    }

    default OwnedTypeDef evaluate(Type fun, Type... args) {
        return wrap(
                evaluateDef(unwrap(fun), Arrays.stream(args).map(this::unwrap).toArray(TypeDef[]::new)));
    }

    default OwnedTypeDef evaluate(OwnedTypeDef type, OwnedTypeDef... args) {
        return wrap(
                evaluateDef(unwrap(type), Arrays.stream(args).map(this::unwrap).toArray(TypeDef[]::new)));
    }

    default List<Type.TypeFunction.Overload> overloads(OwnedTypeDef type, Type... args) {
        if (unwrap(type) instanceof ApplicableDef func) {
            List<TypeDef> argDefs = Arrays.stream(args).map(this::unwrap).toList();
            return func.overloads(this, argDefs).stream().map(this::wrap).toList();
        }
        throw new UnsupportedOperationException("Type " + unwrap(type).getClass() + " is not an ApplicableDef");
    }

    /**
     * Wraps an OverloadDef with this TypeSystem into a Type.TypeFunction.Overload.
     */
    default Type.TypeFunction.Overload wrap(ApplicableDef.OverloadDef def) {
        return new Type.TypeFunction.Overload(
                wrap(def.returnType()),
                def.parameters().stream().map(this::wrap).map(Type.class::cast).toList());
    }

    default OwnedTypeDef intersect(OwnedTypeDef... types) {
        TypeDef[] defs = new TypeDef[types.length];
        for (int i = 0; i < types.length; i++) {
            defs[i] = unwrap(types[i]);
        }
        return wrap(intersectDef(defs));
    }

    default OwnedTypeDef union(OwnedTypeDef... types) {
        TypeDef[] defs = new TypeDef[types.length];
        for (int i = 0; i < types.length; i++) {
            defs[i] = unwrap(types[i]);
        }
        return wrap(unionDef(defs));
    }

    default OwnedTypeDef negate(OwnedTypeDef type) {
        return wrap(negateDef(unwrap(type)));
    }

    default TypeDef construct(TypeDef target, List<TypeDef> args) {
        throw new NoSuchMethodError();
    }

    default boolean satisfies(TypeDef self, TypeDef other) {
        // check A & ~B = bottom
        return intersectDef(self, negateDef(other)).equals(bottomDef());
    }

    @Override
    default boolean isAssignableTo(Type self, Type other) {
        return self instanceof OwnedTypeDef s
                && other instanceof OwnedTypeDef o
                && !bottomDef().equals(unwrap(s))
                && satisfies(unwrap(s), unwrap(o));
    }

    default boolean isEquivalentTo(OwnedTypeDef self, OwnedTypeDef other) {
        return (unwrap(self) instanceof BottomType && unwrap(other) instanceof BottomType)
                || (self.isAssignableTo(other) && self.isAssignableFrom(other));
    }

    default TypeDef unionDef(TypeDef... types) {
        // Concatenate OR branches, graft overlaps, identity is bottom
        return Arrays.stream(types).flatMap(TypeSystemUtils::unionStream).collect(unionCollector(this));
    }

    default TypeDef intersectTypes(TypeDef[] types) {
        // Define the intersection operation
        BinaryOperator<TypeDef> intersectWith = (self, other) -> Stream.of(self, other)
                .flatMap(TypeSystemUtils::intersectionStream)
                .collect(NormalizerUtils.intersectionCollector(this));

        // Pairwise Cartesian product of ORs, prune AND leaves, identity is top
        return Arrays.stream(types)
                .reduce(cartesianProduct(intersectWith, bottomDef(), unionCollector(this)))
                .orElseGet(this::topDef);
    }

    default TypeDef negateDef(TypeDef type) {
        return switch (type) {
            // ~(A | B) -> ~A & ~B
            // ~Bottom -> Top
            case UnionType u ->
                u.members().stream()
                        .map(this::negateDef)
                        .reduce(this::intersectDef)
                        .orElseGet(this::topDef);
            // ~(A & B) -> ~A | ~B
            // ~Top -> Bottom
            case IntersectionType i ->
                i.members().stream().map(this::negateDef).reduce(this::unionDef).orElseGet(this::bottomDef);
            // ~(~A) -> A
            case NegationType n -> n.inner();
            case TopType __ -> bottomDef();
            case BottomType __ -> topDef();
            // Constraint Inversion
            default ->
                ofNullable(type.invert(this))
                        .map(c -> c.stream().reduce(this::unionDef).orElseGet(this::bottomDef))
                        .orElseGet(() -> new NegationType(type));
        };
    }

    default TypeDef intersectDef(TypeDef... types) {
        if (Arrays.asList(types).contains(bottomDef())) {
            return bottomDef();
        } else if (Arrays.stream(types).allMatch(ApplicableDef.class::isInstance)) {
            return intersectFunctions(types);
            //        } else if (Arrays.stream(types).anyMatch(ApplicableDef.class::isInstance)) {
            //            throw new IllegalArgumentException("cannot intersect type and functions: " +
            // Arrays.toString(types));
        } else {
            return intersectTypes(types);
        }
    }

    default TypeDef intersectFunctions(TypeDef[] types) {
        return new FunctionType(Arrays.stream(types)
                .flatMap(TypeSystemUtils::signatureStream)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new)));
    }

    /**
     * Wraps a TypeDef with this TypeSystem into an OwnedTypeDef.
     */
    default OwnedTypeDef wrap(TypeDef def) {
        return def == null ? null : new OwnedTypeDef(this, def);
    }

    default OwnedTypeDef adopt(Type type) {
        if (type instanceof OwnedTypeDef o) {
            return wrap(unwrap(o));
        } else {
            throw new IllegalArgumentException();
        }
    }
}
