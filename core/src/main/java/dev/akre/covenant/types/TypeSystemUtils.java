package dev.akre.covenant.types;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeAttribute;
import dev.akre.covenant.types.FunctionType.Signature;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
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
        if (subject instanceof UnionType u) {
            return system.unionDef(u.members().stream()
                    .map(m -> termAt(system, m, segment))
                    .toArray(TypeDef[]::new));
        }
        if (subject instanceof IntersectionType i) {
            return system.intersectDef(i.members().stream()
                    .map(m -> termAt(system, m, segment))
                    .toArray(TypeDef[]::new));
        }
        if (subject instanceof NegationType n) {
            return system.negateDef(termAt(system, n.inner(), segment));
        }
        if (subject instanceof GenericTypeDef g) {
            String resolvedSegment = null;
            if (segment instanceof SymbolType s) {
                resolvedSegment = s.value();
            } else if (segment instanceof StringConstraint s && s.operator() == ValueConstraint.Operator.EQ) {
                resolvedSegment = s.value();
            } else if (segment instanceof NumberConstraint n && n.operator() == ValueConstraint.Operator.EQ) {
                resolvedSegment = n.value().toPlainString();
            }

            if (resolvedSegment == null) return system.bottomDef();

            if (g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
                TypeDefParam.Named named = findNamed(g, resolvedSegment);
                if (named == null && (resolvedSegment.startsWith("'") && resolvedSegment.endsWith("'"))) {
                    named = findNamed(g, resolvedSegment.substring(1, resolvedSegment.length() - 1));
                }
                if (named != null) {
                    return named.type();
                }
                // Check dynamic constraints
                for (TypeDefParam tp : g.parameters()) {
                    if (tp instanceof TypeDefParam.Constrained c && matches(c, resolvedSegment)) {
                        return tp.type(); // Simplification: return first match
                    }
                }
                // Check if open
                for (TypeDefParam tp : g.parameters()) {
                    if (tp instanceof TypeDefParam.Spread s) {
                        return tp.type();
                    }
                }
                return system.bottomDef();
            } else {
                // Positional/Array
                try {
                    int index = Integer.parseInt(resolvedSegment);
                    int current = 0;
                    for (TypeDefParam tp : g.parameters()) {
                        if (tp instanceof TypeDefParam.Positional pos) {
                            TypeDef type = tp.type();
                            if (pos.variadic()) {
                                if (index >= current) {
                                    TypeDef nullType = system.nilDef();
                                    if (nullType != null) {
                                        return system.unionDef(type, nullType);
                                    }
                                    return type; // Fallback if Null not defined
                                }
                            } else {
                                if (index == current) {
                                    return type;
                                }
                                current++;
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // Not an index
                }
                return system.bottomDef();
            }
        }
        return system.bottomDef();
    }

    private static boolean matches(TypeDefParam.Constrained c, String name) {
        if (!c.keyword().equals("matches")) return false;
        String regex = c.value();
        if (regex.startsWith("\"") && regex.endsWith("\"")) {
            regex = regex.substring(1, regex.length() - 1);
        }
        try {
            return java.util.regex.Pattern.compile(regex).matcher(name).find();
        } catch (Exception e) {
            return false;
        }
    }

    private static TypeDefParam.Named findNamed(GenericTypeDef g, String name) {
        for (TypeDefParam tp : g.parameters()) {
            if (tp instanceof TypeDefParam.Named n && n.name().equals(name)) {
                return n;
            }
        }
        return null;
    }

    public static Stream<TypeDef> unionStream(TypeDef t) {
        return t instanceof UnionType u ? u.members().stream() : Stream.of(t);
    }

    public static Stream<TypeDef> intersectionStream(TypeDef t) {
        return t instanceof IntersectionType i ? i.members().stream() : Stream.of(t);
    }

    public static Stream<Signature> signatureStream(TypeDef t) {
        if (t instanceof FunctionType f) {
            return f.signatures().stream();
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
            AbstractTypeSystem ignoredSystem,
            NominalDef type,
            Collection<String> parentNames,
            TypeAttribute attribute) {
        EnumSet<TypeAttribute> newAttributes = append(type.attributes(), attribute);
        Set<String> newNames = concat(type.parentNames(), parentNames);
        if (type instanceof TopType) throw new IllegalArgumentException("cannot modify top");
        if (type instanceof BottomType) throw new IllegalArgumentException("cannot modify bottom");
        if (type instanceof AtomType a) return new AtomType(a.name(), newNames, newAttributes);
        if (type instanceof TemplateType t) return new TemplateType(t.name(), newNames, t.constructor(), newAttributes);
        throw new IllegalArgumentException("unknown nominal def type");
    }

    public static TemplateType updateTemplate(
            AbstractTypeSystem ignoredSystem,
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
}
