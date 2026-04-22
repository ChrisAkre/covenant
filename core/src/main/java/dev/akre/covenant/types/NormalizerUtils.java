package dev.akre.covenant.types;

import static java.util.function.Predicate.not;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class NormalizerUtils {
    public static Collector<TypeDef, ?, TypeDef> intersectionCollector(AbstractTypeSystem system) {
        return Rewriter.rewriteCollector(
                resolveBounds(
                        system,
                        resolveComplementBounds(
                                (t1, t2) -> t1.satisfiesOther(system, t2),
                                flattenIntersections(system, (t1, t2) -> t1.prune(system, t2)),
                                (t1, t2) -> t1.graft(system, t2)),
                        system.bottomDef(),
                        system.topDef()),
                system.bottomDef(),
                checkBoundedExhaustion(
                        system, TypeSystemUtils.wrap(system, (sys, members) -> new IntersectionType(members))));
    }

    private static Function<LinkedHashSet<TypeDef>, TypeDef> checkBoundedExhaustion(
            AbstractTypeSystem system, Function<Collection<TypeDef>, TypeDef> wrap) {
        // TODO this needs to handle bounded universes like `Bool & true & false` and `Number & ~Int & ~Float` and
        // enums.
        Set<TypeDef> invertedNominalUniverse = system.typesDef().values().stream()
                .filter(NominalDef.class::isInstance)
                .map(NominalDef.class::cast)
                .filter(not(n -> n.attributes().contains(dev.akre.covenant.api.TypeAttribute.ABSTRACT)))
                .map(NegationType::new)
                .collect(Collectors.toSet());
        return result -> result.containsAll(invertedNominalUniverse) ? system.bottomDef() : wrap.apply(result);
    }

    public static Collector<TypeDef, ?, TypeDef> unionCollector(AbstractTypeSystem system) {
        return Rewriter.rewriteCollector(
                resolveBounds(
                        system,
                        resolveComplementBounds(
                                (t1, t2) -> isSatisfiedByOther(system, t1, t2),
                                flattenUnions(system, (t1, t2) -> t1.graft(system, t2)),
                                (t1, t2) -> t1.prune(system, t2)),
                        system.topDef(),
                        system.bottomDef()),
                system.topDef(),
                TypeSystemUtils.wrap(system, (sys, members) -> new UnionType(members)));
    }

    public static BinaryOperator<TypeDef> cartesianProduct(
            BinaryOperator<TypeDef> operation, TypeDef uncollectable, Collector<TypeDef, ?, TypeDef> collector) {
        return (self, other) -> TypeSystemUtils.unionStream(self)
                .flatMap(t1 -> TypeSystemUtils.unionStream(other).map(t2 -> operation.apply(t1, t2)))
                .filter(not(uncollectable::equals))
                .collect(collector);
    }

    public static BiFunction<TypeDef, TypeDef, Collection<TypeDef>> resolveComplementBounds(
            BiPredicate<TypeDef, TypeDef> subsumptionCheck,
            BiFunction<TypeDef, TypeDef, Collection<TypeDef>> operator,
            BiFunction<TypeDef, TypeDef, Collection<TypeDef>> inverseOperator) {
        return (self, other) -> {
            if (self instanceof NegationType(TypeDef inner) && other instanceof NegationType(TypeDef otherInner)) {
                return subsumptionCheck.test(inner, otherInner)
                        ? Set.of(other)
                        : subsumptionCheck.test(otherInner, inner)
                                ? Set.of(self)
                                : Optional.ofNullable(inverseOperator.apply(inner, otherInner))
                                        .map(r -> r.stream()
                                                .<TypeDef>map(NegationType::new)
                                                .collect(Collectors.toSet()))
                                        .orElse(null);
            } else if (self instanceof NegationType(TypeDef inner)) {
                return subsumptionCheck.test(other, inner) ? Set.of() : null;
            } else if (other instanceof NegationType(TypeDef otherInner)) {
                return subsumptionCheck.test(self, otherInner) ? Set.of() : null;
            } else if (subsumptionCheck.test(self, other)) {
                return Set.of(self);
            } else if (subsumptionCheck.test(other, self)) {
                return Set.of(other);
            } else {
                return operator.apply(self, other);
            }
        };
    }

    public static boolean isSatisfiedByOther(AbstractTypeSystem system, TypeDef self, TypeDef other) {
        return other.satisfiesOther(system, self);
    }

    public static BiFunction<TypeDef, TypeDef, Collection<TypeDef>> resolveBounds(
            AbstractTypeSystem system,
            BiFunction<TypeDef, TypeDef, Collection<TypeDef>> operator,
            TypeDef uncollectable,
            TypeDef identity) {
        return (self, other) -> {
            if (self.equals(uncollectable) || other.equals(uncollectable)) {
                return Set.of(); // Triggers the poison pill, collapsing the branch
            }
            if (self.equals(identity)) {
                return Set.of(other); // Absorbs identity, keeping the other
            }
            if (other.equals(identity)) {
                return Set.of(self); // Absorbs identity, keeping the other
            }
            return operator.apply(self, other);
        };
    }

    // Renamed to reflect AST node flattening
    public static BiFunction<TypeDef, TypeDef, Collection<TypeDef>> flattenUnions(
            AbstractTypeSystem system, BiFunction<TypeDef, TypeDef, Collection<TypeDef>> operator) {
        return (self, other) -> {
            if (self instanceof UnionType(Set<TypeDef> members)) {
                return other instanceof UnionType(Set<TypeDef> otherMembers)
                        ? TypeSystemUtils.concat(members, otherMembers)
                        : null;
            } else if (self instanceof IntersectionType || other instanceof IntersectionType) {
                return null;
            }
            return operator.apply(self, other);
        };
    }

    // Renamed to reflect AST node flattening
    public static BiFunction<TypeDef, TypeDef, Collection<TypeDef>> flattenIntersections(
            AbstractTypeSystem system, BiFunction<TypeDef, TypeDef, Collection<TypeDef>> operator) {
        return (self, other) -> self instanceof UnionType && other instanceof UnionType
                ? null
                : self instanceof IntersectionType(Set<TypeDef> members)
                                && other instanceof IntersectionType(Set<TypeDef> otherMembers)
                        ? TypeSystemUtils.concat(members, otherMembers)
                        : operator.apply(self, other);
    }

    static class Rewriter {
        public static <T, R> Collector<T, LinkedHashSet<T>, R> rewriteCollector(
                BiFunction<T, T, Collection<T>> operator, T uncollectable, Function<LinkedHashSet<T>, R> finalizer) {

            BiConsumer<LinkedHashSet<T>, T> accumulator = rewriteAccumulator(operator, uncollectable);
            return Collector.of(LinkedHashSet::new, accumulator, rewriteCombiner(accumulator), finalizer);
        }

        private static <T> BinaryOperator<LinkedHashSet<T>> rewriteCombiner(
                BiConsumer<LinkedHashSet<T>, T> accumulator) {
            return (self, other) -> {
                other.forEach(t -> accumulator.accept(self, t));
                return self;
            };
        }

        private static <T> BiConsumer<LinkedHashSet<T>, T> rewriteAccumulator(
                BiFunction<T, T, Collection<T>> operator, T uncollectable) {
            return (LinkedHashSet<T> stable, T t) -> {
                if (stable.contains(uncollectable)) return;

                Queue<T> unstable = new ArrayDeque<>(List.of(t));

                while (!unstable.isEmpty()) {
                    T current = unstable.poll();
                    boolean reduced = false;

                    for (T s : stable) {
                        Collection<T> result = operator.apply(current, s);
                        if (result != null) {
                            stable.remove(s);

                            if (result.isEmpty()) {
                                stable.clear();
                                stable.add(uncollectable);
                                return;
                            }

                            unstable.addAll(result);
                            reduced = true;
                            break;
                        }
                    }

                    if (!reduced) {
                        stable.add(current);
                    }
                }
            };
        }
    }
}
