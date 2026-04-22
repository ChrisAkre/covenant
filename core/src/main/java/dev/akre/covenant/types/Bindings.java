package dev.akre.covenant.types;

import static dev.akre.covenant.types.TypeSystemUtils.concat;
import static java.util.function.Predicate.not;

import dev.akre.covenant.api.Parameter;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Evaluator/Scope context for TypeExpressions.
 * This class is responsible for the "resolution" phase, turning syntactic
 * TypeExpr nodes into canonical TypeDef objects using the AbstractTypeSystem.
 */
public record Bindings(AbstractTypeSystem system, Map<String, TypeDef> values) {
    public Bindings {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public Bindings(AbstractTypeSystem system) {
        this(system, new LinkedHashMap<>());
    }

    /**
     * Resolves a TypeExpr into a physical TypeDef.
     * Uses an enhanced switch to act as a stateless interpreter for the AST.
     */
    public TypeDef resolve(TypeExpr expr) {
        return switch (expr) {
            //            case null -> null; # If we have a null, it's an error
            case TypeExpr.UnionExpr u ->
                system.unionDef(u.members().stream().map(this::resolve).toArray(TypeDef[]::new));

            case TypeExpr.IntersectionExpr i ->
                system.intersectDef(i.members().stream().map(this::resolve).toArray(TypeDef[]::new));

            case TypeExpr.NegationExpr n -> system.negateDef(resolve(n.inner()));

            case TypeExpr.RefExpr r -> {
                String name = r.name();
                if (name.equals("true") || name.equals("false")) {
                    yield system.intersectDef(
                            resolve(new TypeExpr.RefExpr("Bool")),
                            new BooleanConstraint(ValueConstraint.Operator.EQ, Boolean.parseBoolean(name)));
                }
                yield system.find(name)
                        .map(t -> ((OwnedTypeDef) t).def())
                        .or(() -> var(name))
                        .orElse(system.bottomDef());
            }

            case TypeExpr.ApplyExpr a -> {
                TypeDef target = resolve(a.target());
                if (!(target instanceof TemplateType t)) {
                    throw new IllegalStateException(
                            "Expected template type, but was: '" + a.target() + "' (" + target.getClass() + ")");
                }
                List<TypeDef> args = a.arguments().stream().map(this::resolve).toList();
                List<Parameter> params = a.arguments().stream()
                        .map(TypeExpr.ParamExpr::parameter)
                        .toList();
                // system.apply handles constructing Generics or invoking Constraint Factories
                yield t.constructor().construct(system, t, args, params);
            }

            case TypeExpr.PathExpr p -> {
                TypeDef target = resolve(p.target());
                yield TypeSystemUtils.termAt(system, target, p.segment());
            }

            case TypeExpr.SymbolExpr s -> new SymbolType(s.symbol());
            case TypeExpr.StringExpr s ->
                system.intersectDef(
                        resolve(new TypeExpr.RefExpr("String")),
                        new StringConstraint(ValueConstraint.Operator.EQ, s.value()));
            case TypeExpr.IntExpr i ->
                system.intersectDef(
                        resolve(new TypeExpr.RefExpr("Int")),
                        new NumberConstraint(ValueConstraint.Operator.EQ, i.value()));
            case TypeExpr.FloatExpr f ->
                system.intersectDef(
                        resolve(new TypeExpr.RefExpr("Float")),
                        new NumberConstraint(
                                ValueConstraint.Operator.EQ,
                                new BigDecimal(f.value().toString())));

            // --- FUNCTION SIGNATURES ---
            case TypeExpr.SignatureExpr s -> {
                Set<String> declared =
                        s.typeVars().stream().map(TypeExpr.VarExpr::name).collect(Collectors.toSet());
                List<TypeExpr.VarExpr> undeclared = s.typeParams().stream()
                        .flatMap(Bindings::collectUndeclaredTypeVars)
                        .filter(not(declared::contains))
                        .filter(isEmpty(system::find))
                        .filter(not(values::containsKey))
                        .collect(Collectors.toSet())
                        .stream()
                        .map(name -> new TypeExpr.VarExpr(name, new TypeExpr.RefExpr("top")))
                        .toList();
                var sig = undeclared.isEmpty()
                        ? s
                        : new TypeExpr.SignatureExpr(concat(s.typeVars(), undeclared), s.typeParams(), s.returnType());
                yield new FunctionType.Signature(sig, values);
            }
            case TypeExpr.ParamExpr a -> resolve(a.type());
            case TypeExpr.ConstraintExpr c -> {
                ValueConstraint.Operator op =
                        switch (c.keyword()) {
                            case "gt" -> ValueConstraint.Operator.GT;
                            case "gte" -> ValueConstraint.Operator.GTE;
                            case "lt" -> ValueConstraint.Operator.LT;
                            case "lte" -> ValueConstraint.Operator.LTE;
                            case "eq" -> ValueConstraint.Operator.EQ;
                            case "neq" -> ValueConstraint.Operator.NEQ;
                            case "matches" -> ValueConstraint.Operator.MATCHES;
                            case "nmatches" -> ValueConstraint.Operator.NOT_MATCHES;
                            default ->
                                throw new UnsupportedOperationException("Unknown constraint keyword: " + c.keyword());
                        };

                String val = c.value();
                if (val.equals("true") || val.equals("false")) {
                    yield new BooleanConstraint(op, Boolean.parseBoolean(val));
                }

                try {
                    yield new NumberConstraint(op, new BigDecimal(val));
                } catch (NumberFormatException e) {
                    yield new StringConstraint(op, val);
                }
            }
            case TypeExpr.NullExpr __ -> system.nilDef();
            case TypeExpr.SpreadExpr __ -> new SymbolType("...");
        };
    }

    private static Stream<String> collectUndeclaredTypeVars(TypeExpr expr) {
        return switch (expr) {
            case TypeExpr.RefExpr r -> Stream.of(r.name());
            case TypeExpr.UnionExpr u -> u.members().stream().flatMap(Bindings::collectUndeclaredTypeVars);
            case TypeExpr.IntersectionExpr i -> i.members().stream().flatMap(Bindings::collectUndeclaredTypeVars);
            case TypeExpr.NegationExpr n -> collectUndeclaredTypeVars(n.inner());
            case TypeExpr.ApplyExpr a ->
                Stream.concat(
                        collectUndeclaredTypeVars(a.target()),
                        a.arguments().stream().flatMap(Bindings::collectUndeclaredTypeVars));
            case TypeExpr.PathExpr p -> collectUndeclaredTypeVars(p.target());
            case TypeExpr.ParamExpr p -> collectUndeclaredTypeVars(p.type());
            case TypeExpr.SignatureExpr s -> {
                Set<String> named =
                        s.typeVars().stream().map(TypeExpr.VarExpr::name).collect(Collectors.toSet());
                yield s.typeParams().stream()
                        .flatMap(Bindings::collectUndeclaredTypeVars)
                        .filter(not(named::contains));
            }
            case null, default -> Stream.empty();
        };
    }

    public static <T> Predicate<T> isEmpty(Function<T, Optional<?>> f) {
        return t -> f.apply(t).isEmpty();
    }

    private Optional<TypeDef> var(String name) {
        return Optional.ofNullable(values.get(name));
    }

    /**
     * Creates a new Bindings context with additional mappings,
     * used when entering a new lexical scope (like a function call).
     */
    public Bindings extend(Map<String, TypeDef> newValues) {
        Map<String, TypeDef> combined = new LinkedHashMap<>(this.values);
        combined.putAll(newValues);
        return new Bindings(system, combined);
    }
}
