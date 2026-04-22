package dev.akre.covenant.types;

import dev.akre.covenant.api.Parameter;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.FunctionType.Signature;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeSystemUtils {

    public static TypeDef termAt(AbstractTypeSystem system, TypeDef subject, String segment) {
        return termAt(system, subject, new SymbolType(segment));
    }

    public static TypeDef termAt(AbstractTypeSystem system, TypeDef subject, List<String> segments) {
        TypeDef current = subject;
        for (String segment : segments) {
            current = termAt(system, current, segment);
        }
        return current;
    }

    public static TypeDef termAt(AbstractTypeSystem system, TypeDef subject, TypeDef segment) {
        if (subject == null || segment == null) {
            return null;
        }
        return switch (subject) {
            case ContainerDef c ->
                switch (c) {
                    case UnionType u ->
                        system.unionDef(u.members().stream()
                                .map(m -> termAt(system, m, segment))
                                .toArray(TypeDef[]::new));
                    case IntersectionType i ->
                        system.intersectDef(i.members().stream()
                                .map(m -> termAt(system, m, segment))
                                .toArray(TypeDef[]::new));
                    case NegationType n -> system.negateDef(termAt(system, n.inner(), segment));
                };
            case GenericTypeDef g -> {
                String resolvedSegment = null;
                if (segment instanceof SymbolType(String value)) {
                    resolvedSegment = value;
                } else if (segment instanceof StringConstraint s && s.operator() == ValueConstraint.Operator.EQ) {
                    resolvedSegment = s.value();
                } else if (segment instanceof NumberConstraint n && n.operator() == ValueConstraint.Operator.EQ) {
                    resolvedSegment = n.value().toPlainString();
                }

                if (resolvedSegment == null) yield system.bottomDef();

                if (g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
                    Parameter.Named named = findNamed(g, resolvedSegment);
                    if (named == null && (resolvedSegment.startsWith("'") && resolvedSegment.endsWith("'"))) {
                        named = findNamed(g, resolvedSegment.substring(1, resolvedSegment.length() - 1));
                    }
                    if (named != null) {
                        Parameter.Named finalNamed = named;
                        yield g.parameters().stream()
                                .filter(tp -> tp.parameter().equals(finalNamed))
                                .findFirst()
                                .map(TypeDefParam::type)
                                .orElse(system.bottomDef());
                    }
                    // Check if open
                    for (TypeDefParam tp : g.parameters()) {
                        if (tp.parameter() instanceof Parameter.Spread(Integer index)) {
                            if (index != null) {
                                yield tp.type();
                            }
                            yield system.topDef(); // Any
                        }
                    }
                    yield system.bottomDef();
                } else {
                    // Positional/Array
                    try {
                        int index = Integer.parseInt(resolvedSegment);
                        int current = 0;
                        for (TypeDefParam tp : g.parameters()) {
                            Parameter p = tp.parameter();
                            if (p instanceof Parameter.Positional pos) {
                                TypeDef type = tp.type();
                                if (pos.variadic()) {
                                    if (index >= current) {
                                        TypeDef nullType = system.nilDef();
                                        if (nullType != null) {
                                            yield system.unionDef(type, nullType);
                                        }
                                        yield type; // Fallback if Null not defined
                                    }
                                } else {
                                    if (index == current) {
                                        yield type;
                                    }
                                    current++;
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        // Not an index
                    }
                    yield system.bottomDef();
                }
            }
            case ApplicableDef a -> system.bottomDef();
            case NominalDef n -> system.bottomDef();
            case ValueConstraint nc -> system.bottomDef();
            case SymbolType s -> system.bottomDef();
        };
    }

    private static Parameter.Named findNamed(GenericTypeDef g, String name) {
        for (TypeDefParam tp : g.parameters()) {
            if (tp.parameter() instanceof Parameter.Named n && n.name().equals(name)) {
                return n;
            }
        }
        return null;
    }

    public static Stream<TypeDef> unionStream(TypeDef t) {
        return t instanceof UnionType(Set<TypeDef> members) ? members.stream() : Stream.of(t);
    }

    public static Stream<TypeDef> intersectionStream(TypeDef t) {
        return t instanceof IntersectionType(Set<TypeDef> members) ? members.stream() : Stream.of(t);
    }

    public static Stream<Signature> signatureStream(TypeDef t) {
        if (t instanceof FunctionType(Set<Signature> signatures)) {
            return signatures.stream();
        } else if (t instanceof Signature s) {
            return Stream.of(s);
        } else {
            throw new IllegalArgumentException("not applicable");
        }
    }

    public static <T extends Collection<TypeDef>> Function<T, TypeDef> wrap(
            AbstractTypeSystem system, BiFunction<AbstractTypeSystem, Collection<TypeDef>, TypeDef> wrapper) {
        return c ->
                c.isEmpty() ? system.bottomDef() : c.size() == 1 ? c.iterator().next() : wrapper.apply(system, c);
    }

    public static <T extends Enum<T>> EnumSet<T> append(EnumSet<T> set, T t) {
        if (t == null) {
            return set;
        } else {
            var result = EnumSet.copyOf(set);
            result.add(t);
            return result;
        }
    }

    public static <T> List<T> append(List<T> list, T t) {
        if (t == null) {
            return list;
        } else {
            var result = new ArrayList<>(list);
            result.add(t);
            return result;
        }
    }

    public static <T> Set<T> concat(Set<T> set, Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return set;
        } else {
            var result = new HashSet<>(set);
            result.addAll(values);
            return result;
        }
    }

    public static <T> List<T> concat(List<T> list, Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return list;
        } else {
            var result = new ArrayList<>(list);
            result.addAll(values);
            return result;
        }
    }

    public static NominalDef updateNominalDef(
            AbstractTypeSystem system,
            NominalDef type,
            Collection<String> parentNames,
            dev.akre.covenant.api.TypeAttribute attribute) {
        EnumSet<dev.akre.covenant.api.TypeAttribute> newAttributes = append(type.attributes(), attribute);
        Set<String> newNames = concat(type.parentNames(), parentNames);
        return switch (type) {
            case TopType __ -> throw new IllegalArgumentException("cannot modify " + type.getClass());
            case BottomType __ -> throw new IllegalArgumentException("cannot modify " + type.getClass());
            case AtomType a -> new AtomType(a.name(), newNames, newAttributes);
            case TemplateType t -> new TemplateType(t.name(), newNames, t.constructor(), newAttributes);
        };
    }

    public static TemplateType updateTemplate(
            AbstractTypeSystem system,
            NominalDef last,
            AbstractTypeSystemBuilder.PatternConstructor.Pattern pattern,
            Integer min,
            Integer max) {
        AbstractTypeSystemBuilder.PatternConstructor constructor = last instanceof TemplateType t
                ? (AbstractTypeSystemBuilder.PatternConstructor) t.constructor()
                : new AbstractTypeSystemBuilder.PatternConstructor(pattern);
        int newMin = min != null ? min : constructor.min();
        int newMax = max != null ? max : constructor.max();
        return new TemplateType(
                last.name(),
                last.parentNames(),
                new AbstractTypeSystemBuilder.PatternConstructor(constructor.pattern(), newMin, newMax),
                last.attributes());
    }

    public static List<List<TypeDef>> permutateUnions(List<TypeDef> args) {
        return args.stream()
                .reduce(
                        List.of(Collections.emptyList()),
                        (permutations, arg) -> permutations.stream()
                                .flatMap(prefix -> unionStream(arg).map(member -> append(prefix, member)))
                                .toList(),
                        TypeSystemUtils::concat);
    }

    public static Map<String, TypeDef> asTypesDef(Map<String, Type> types) {
        return types.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> ((OwnedTypeDef) e.getValue()).def()));
    }
}
