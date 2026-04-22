package dev.akre.covenant.types;

import static dev.akre.covenant.types.TypeSystemUtils.permutateUnions;

import java.util.*;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;

/**
 * Represents a function type, which can be an intersection of multiple signatures (overloading).
 */
public record FunctionType(Set<Signature> signatures) implements ApplicableDef {
    public FunctionType {
        signatures = Collections.unmodifiableSet(new LinkedHashSet<>(signatures));
    }

    /**
     * Evaluates the function call against the provided argument types.
     */
    @Override
    public TypeDef evaluate(AbstractTypeSystem system, List<TypeDef> args) {
        List<OverloadDef> resolvedOverloads = overloads(system, args);
        if (resolvedOverloads.isEmpty()) {
            return system.bottomDef();
        }
        return system.unionDef(
                resolvedOverloads.stream().map(OverloadDef::returnType).toArray(TypeDef[]::new));
    }

    @Override
    public List<OverloadDef> overloads(AbstractTypeSystem system, List<TypeDef> args) {
        if (args.stream().anyMatch(a -> a instanceof UnionType)) {
            List<OverloadDef> aggregated = new ArrayList<>();
            for (List<TypeDef> permutation : permutateUnions(args)) {
                aggregated.addAll(overloadsConcrete(system, permutation));
            }
            return aggregated.stream().distinct().toList();
        }
        return overloadsConcrete(system, args);
    }

    private List<OverloadDef> overloadsConcrete(AbstractTypeSystem system, List<TypeDef> args) {
        for (Signature sig : signatures) {
            List<OverloadDef> result = sig.overloadsConcrete(system, args);
            if (!result.isEmpty()) {
                return result; // The first overload to succeed in this permutation wins
            }
        }
        return List.of();
    }

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef other) {
        if (this.satisfiesOther(system, other)) {
            return Set.of(this);
        } else if (other.satisfiesOther(system, this)) {
            return Set.of(other);
        } else if (other instanceof ApplicableDef) {
            // they are disjoint if both are applicable but neither satisfies the other
            // actually, function intersections are just combinations, not necessarily bottom
            return null;
        }
        return null;
    }

    @Override
    public Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef other) {
        if (other.satisfiesOther(system, this)) {
            return Set.of(this);
        } else if (this.satisfiesOther(system, other)) {
            return Set.of(other);
        } else if (other instanceof ApplicableDef) {
            return null;
        }
        return null;
    }

    @Override
    public Collection<TypeDef> invert(AbstractTypeSystem system) {
        return null;
    }

    @Override
    public boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
        if (other instanceof FunctionType(Set<Signature> otherSigs)) {
            return otherSigs.stream().allMatch(os -> signatures.stream().anyMatch(s -> s.satisfiesOther(system, os)));
        }
        if (other instanceof Signature otherSig) {
            return signatures.stream().anyMatch(s -> s.satisfiesOther(system, otherSig));
        }
        return false;
    }

    @Override
    public String repr() {
        if (signatures.size() == 1) return signatures.iterator().next().repr();
        return signatures.stream().map(s -> "(" + s.repr() + ")").collect(Collectors.joining(" & "));
    }

    @Override
    public @NonNull String toString() {
        return repr();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof FunctionType(Set<Signature> otehrSigs)) {
            return Objects.equals(signatures, otehrSigs);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash("functions", signatures);
    }

    /**
     * Represents a single function signature: (Arg1, Arg2, ...) -> ReturnType
     */
    public record Signature(TypeExpr.SignatureExpr expr, Map<String, TypeDef> partialBindings)
            implements ApplicableDef {

        @Override
        public List<OverloadDef> overloads(AbstractTypeSystem system, List<TypeDef> args) {
            if (args.stream().anyMatch(a -> a instanceof UnionType)) {
                List<OverloadDef> aggregated = new ArrayList<>();
                for (List<TypeDef> permutation : permutateUnions(args)) {
                    aggregated.addAll(overloadsConcrete(system, permutation));
                }
                return aggregated.stream().distinct().toList();
            }
            return overloadsConcrete(system, args);
        }

        private List<OverloadDef> overloadsConcrete(AbstractTypeSystem system, List<TypeDef> args) {
            return UnificationUtils.unify(system, expr, args, partialBindings)
                    .map(scope -> {
                        TypeDef ret = scope.resolve(expr.returnType());
                        List<TypeDef> params =
                                expr.typeParams().stream().map(scope::resolve).toList();
                        return new OverloadDef(ret, params);
                    })
                    .stream()
                    .toList();
        }

        @Override
        public TypeDef evaluate(AbstractTypeSystem system, List<TypeDef> args) {
            List<OverloadDef> resolvedOverloads = overloads(system, args);
            if (resolvedOverloads.isEmpty()) {
                return system.bottomDef();
            }
            return system.unionDef(
                    resolvedOverloads.stream().map(OverloadDef::returnType).toArray(TypeDef[]::new));
        }

        @Override
        public boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
            if (other instanceof FunctionType(Set<Signature> otherSignatures)) {
                return otherSignatures.stream().allMatch(os -> satisfiesOther(system, os));
            }
            if (other instanceof Signature otherSig) {
                if (this.equals(otherSig)) return true;
                if (expr == null || otherSig.expr() == null) return false;
                if (expr.typeParams().size() != otherSig.expr().typeParams().size()) return false;

                // Only handle non-generic signatures for standalone satisfaction check for now
                if (!expr.typeVars().isEmpty() || !otherSig.expr().typeVars().isEmpty()) return false;

                // Contravariance of arguments
                Bindings thisScope = new Bindings(system, partialBindings);
                Bindings otherScope = new Bindings(system, otherSig.partialBindings());

                for (int i = 0; i < expr.typeParams().size(); i++) {
                    try {
                        TypeDef thisArg = thisScope.resolve(expr.typeParams().get(i));
                        TypeDef otherArg =
                                otherScope.resolve(otherSig.expr().typeParams().get(i));
                        if (!system.satisfies(otherArg, thisArg)) return false;
                    } catch (Exception e) {
                        return false;
                    }
                }

                // Covariance of return type
                try {
                    TypeDef thisRet = thisScope.resolve(expr.returnType());
                    TypeDef otherRet = otherScope.resolve(otherSig.expr().returnType());
                    return system.satisfies(thisRet, otherRet);
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        }

        @Override
        public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef other) {
            if (this.satisfiesOther(system, other)) {
                return Set.of(this);
            } else if (other.satisfiesOther(system, this)) {
                return Set.of(other);
            } else if (other instanceof ApplicableDef) {
                // they are disjoint if both are applicable but neither satisfies the other
                // actually, function intersections are just combinations, not necessarily bottom
                return null;
            }
            return null;
        }

        @Override
        public Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef other) {
            if (other.satisfiesOther(system, this)) {
                return Set.of(this);
            } else if (this.satisfiesOther(system, other)) {
                return Set.of(other);
            } else if (other instanceof ApplicableDef) {
                return null;
            }
            return null;
        }

        @Override
        public Collection<TypeDef> invert(AbstractTypeSystem system) {
            return null;
        }

        @Override
        public String repr() {
            return expr != null ? expr.toString() : "null";
        }

        @Override
        public boolean equals(Object o) {
            return this == o
                    || o instanceof Signature(TypeExpr.SignatureExpr otherExpr, Map<String, TypeDef> otherBindings)
                            && Objects.equals(expr, otherExpr)
                            && Objects.equals(partialBindings, otherBindings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(expr, partialBindings);
        }
    }
}
