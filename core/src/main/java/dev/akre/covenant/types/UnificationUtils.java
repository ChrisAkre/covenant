package dev.akre.covenant.types;

import dev.akre.covenant.api.Parameter;
import java.util.*;
import java.util.stream.Collectors;

public class UnificationUtils {

    // Fix #7: Extract the mutable extraction state into a record to shrink the
    // extract() parameter list from 8 down to 4.
    private record ExtractionContext(
            AbstractTypeSystem system,
            Set<String> typeVars,
            Map<String, TypeDef> context,
            Map<String, TypeDef> lowerBounds,
            Map<String, TypeDef> upperBounds) {

        // Fix #5: Build the Bindings once per context rather than on every
        // concrete-fallback call.
        Bindings bindings() {
            return new Bindings(system, context);
        }
    }

    public static Optional<Bindings> unify(
            AbstractTypeSystem system,
            TypeExpr.SignatureExpr expr,
            List<TypeDef> args,
            Map<String, TypeDef> startingBindings) {

        if (expr == null || expr.typeParams().size() != args.size()) {
            return Optional.empty();
        }

        // Fix #8: Compute typeVars once; reuse the VarExpr list from expr directly
        // in the CONVERGE loop so we iterate expr.typeVars() only once.
        Set<String> typeVarNames =
                expr.typeVars().stream().map(TypeExpr.VarExpr::name).collect(Collectors.toSet());

        Map<String, TypeDef> lowerBounds = new LinkedHashMap<>();
        Map<String, TypeDef> upperBounds = new LinkedHashMap<>();

        // Fix #3: Seed lowerBounds from startingBindings so pre-solved variables
        // are not inadvertently loosened to their upper bound during CONVERGE.
        for (TypeExpr.VarExpr tv : expr.typeVars()) {
            String name = tv.name();
            TypeDef prebound = startingBindings.get(name);
            if (prebound != null) {
                lowerBounds.put(name, prebound);
            }
        }

        ExtractionContext ctx = new ExtractionContext(system, typeVarNames, startingBindings, lowerBounds, upperBounds);

        // 1. EXTRACT: Walk the AST and accumulate bounds positionally.
        for (int i = 0; i < expr.typeParams().size(); i++) {
            if (!extract(ctx, expr.typeParams().get(i), args.get(i), true)) {
                return Optional.empty();
            }
        }

        // 2. CONVERGE & VALIDATE
        Map<String, TypeDef> finalBindings = new LinkedHashMap<>(startingBindings);
        Bindings defaultScope = new Bindings(system, startingBindings);

        for (TypeExpr.VarExpr tv : expr.typeVars()) {
            String name = tv.name();
            TypeDef lower = lowerBounds.get(name);
            TypeDef upper = upperBounds.getOrDefault(name, system.topDef());
            TypeDef syntacticUpper = defaultScope.resolve(tv.upperBound());

            if (syntacticUpper == null) {
                return Optional.empty();
            }

            TypeDef combinedUpper = system.intersectDef(upper, syntacticUpper);

            // Never encountered covariantly → fall back to combined upper bound.
            TypeDef resolved = (lower == null) ? combinedUpper : lower;

            if (!system.satisfies(resolved, combinedUpper)) {
                return Optional.empty();
            }

            finalBindings.put(name, resolved);
        }

        // 3. FINAL VERIFICATION: Test args against the fully resolved signature.
        Bindings evalScope = new Bindings(system, finalBindings);
        for (int i = 0; i < expr.typeParams().size(); i++) {
            TypeDef expectedParamType = evalScope.resolve(expr.typeParams().get(i));
            if (expectedParamType == null || !system.satisfies(args.get(i), expectedParamType)) {
                return Optional.empty();
            }
        }

        return Optional.of(evalScope);
    }

    private static boolean extract(ExtractionContext ctx, TypeExpr expected, TypeDef actual, boolean isCovariant) {

        if (expected == null || actual == null) {
            return false;
        }

        switch (expected) {

            // Base Case: Type Variable Binding
            case TypeExpr.RefExpr ref
            when ctx.typeVars().contains(ref.name()) -> {
                String name = ref.name();
                if (isCovariant) {
                    // Fix #6: Use merge() to eliminate the double-lookup pattern.
                    ctx.lowerBounds().merge(name, actual, ctx.system()::unionDef);
                } else {
                    ctx.upperBounds().merge(name, actual, ctx.system()::intersectDef);
                }
                return true;
            }

            // Algebraic Union Extraction: C <: A | T  =>  C & ~A <: T
            case TypeExpr.UnionExpr u -> {
                if (isCovariant) {
                    List<TypeExpr> unknown = new ArrayList<>();
                    TypeDef knownUnion = ctx.system().bottomDef();
                    Bindings tempScope = ctx.bindings();

                    for (TypeExpr member : u.members()) {
                        if (containsTypeVars(member, ctx.typeVars())) {
                            unknown.add(member);
                        } else {
                            TypeDef resolved = tempScope.resolve(member);
                            if (resolved == null) {
                                return false;
                            }
                            knownUnion = ctx.system().unionDef(knownUnion, resolved);
                        }
                    }

                    // Fully known union: check satisfaction directly.
                    if (unknown.isEmpty()) {
                        return ctx.system().satisfies(actual, knownUnion);
                    }

                    // Non-deterministic branch (e.g., T | K): too complex for linear extraction.
                    if (unknown.size() > 1) {
                        return false;
                    }

                    // Core CDNF Subtraction: subtract known components from actual.
                    TypeDef remainder =
                            ctx.system().intersectDef(actual, ctx.system().negateDef(knownUnion));
                    return extract(ctx, unknown.getFirst(), remainder, true);
                } else {
                    // Contravariant Union (rare): C >: A | T  =>  C >: A  AND  C >: T
                    return u.members().stream().allMatch(member -> extract(ctx, member, actual, false));
                }
            }

            // Intersection Extraction
            case TypeExpr.IntersectionExpr i -> {
                if (isCovariant) {
                    // Covariant Intersection: C <: A & T  =>  C <: A  AND  C <: T
                    return i.members().stream().allMatch(member -> extract(ctx, member, actual, true));
                } else {
                    // Contravariant Intersection: C >: A & T  =>  C >: A  OR  C >: T
                    return i.members().stream().anyMatch(member -> extract(ctx, member, actual, false));
                }
            }

            case TypeExpr.SpreadExpr s -> {
                return true;
            }

            // Generics (ApplyExpr)
            case TypeExpr.ApplyExpr a
            when actual instanceof GenericTypeDef g -> {
                List<TypeExpr.ParamExpr> expArgs = a.arguments();
                List<TypeDefParam> actParams = g.parameters();

                for (int i = 0; i < expArgs.size(); i++) {
                    TypeExpr.ParamExpr expArg = expArgs.get(i);
                    boolean expectedIsVariadic = expArg.parameter() instanceof Parameter.Positional ep && ep.variadic();

                    TypeDef actualMember;

                    if (i < actParams.size()) {
                        TypeDefParam tp = actParams.get(i);
                        actualMember =
                                tp.type() != null ? tp.type() : ctx.system().topDef();
                        Parameter actParam = tp.parameter();

                        // Variadic Null-Padding: actual spreads, expected is strictly positional.
                        if (actParam instanceof Parameter.Positional p && p.variadic() && !expectedIsVariadic) {
                            TypeDef nullType = ctx.system().nilDef();
                            if (nullType != null) {
                                actualMember = ctx.system().unionDef(actualMember, nullType);
                            }
                        }
                    } else {
                        // Actual is out of explicit elements.
                        boolean actualLastIsVariadic = !actParams.isEmpty()
                                && actParams.getLast().parameter() instanceof Parameter.Positional p
                                && p.variadic();

                        if (actualLastIsVariadic) {
                            // The actual array has a spread (e.g. `Bool...`); keep extracting against it.
                            actualMember = actParams.getLast().type() != null
                                    ? actParams.getLast().type()
                                    : ctx.system().topDef();

                            if (!expectedIsVariadic) {
                                // Apply null-padding if expected is positional.
                                TypeDef nullType = ctx.system().nilDef();
                                if (nullType != null) {
                                    actualMember = ctx.system().unionDef(actualMember, nullType);
                                }
                            }
                        } else if (expectedIsVariadic) {
                            // Expected has a spread but actual is exhausted → bottom (0 elements).
                            actualMember = ctx.system().bottomDef();
                        } else {
                            // Expected is positional but actual is finite and exhausted.
                            return false;
                        }
                    }

                    if (!extract(ctx, expArg.type(), actualMember, isCovariant)) {
                        return false;
                    }
                }
                return true;
            }

            // Function Signatures (handling contravariance)
            case TypeExpr.SignatureExpr sig
            when actual
                    instanceof
                    FunctionType.Signature(TypeExpr.SignatureExpr expr, Map<String, TypeDef> partialBindings) -> {
                // Initialize the evaluation scope for the actual function using its
                // captured lexical environment.
                Bindings closure = new Bindings(ctx.system(), partialBindings);

                // Reify the actual return type before extracting.
                TypeDef actualReturn = closure.resolve(expr.returnType());
                if (actualReturn == null || !extract(ctx, sig.returnType(), actualReturn, isCovariant)) {
                    return false;
                }

                // Fix #2: Reject signatures with mismatched arity rather than silently
                // unifying only the matching prefix.
                if (sig.typeParams().size() != expr.typeParams().size()) {
                    return false;
                }

                // Contravariant Parameters: flip the polarity.
                // Note: these are positional runtime parameters, not generic type parameters.
                for (int i = 0; i < sig.typeParams().size(); i++) {
                    TypeDef actualParam = closure.resolve(expr.typeParams().get(i));
                    if (actualParam == null || !extract(ctx, sig.typeParams().get(i), actualParam, !isCovariant)) {
                        return false;
                    }
                }
                return true;
            }

            default -> {}
        }

        // Concrete Fallback: Fix #5 — reuse ctx.bindings() instead of constructing a
        // fresh Bindings on every call.
        TypeDef resolved = ctx.bindings().resolve(expected);
        return resolved != null
                && (isCovariant
                        ? ctx.system().satisfies(actual, resolved)
                        : ctx.system().satisfies(resolved, actual));
    }

    private static boolean containsTypeVars(TypeExpr expr, Set<String> typeVars) {
        return switch (expr) {
            case TypeExpr.RefExpr ref -> typeVars.contains(ref.name());
            case TypeExpr.UnionExpr u -> u.members().stream().anyMatch(m -> containsTypeVars(m, typeVars));
            case TypeExpr.IntersectionExpr i -> i.members().stream().anyMatch(m -> containsTypeVars(m, typeVars));
            case TypeExpr.ApplyExpr a -> a.arguments().stream().anyMatch(arg -> containsTypeVars(arg.type(), typeVars));
            case TypeExpr.SignatureExpr s ->
                containsTypeVars(s.returnType(), typeVars)
                        || s.typeParams().stream().anyMatch(p -> containsTypeVars(p, typeVars));
            case null, default -> false;
        };
    }
}
