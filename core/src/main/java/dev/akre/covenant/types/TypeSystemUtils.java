package dev.akre.covenant.types;

import dev.akre.covenant.api.TypeAttribute;
import dev.akre.covenant.types.FunctionType.Signature;
import dev.akre.covenant.types.ValueConstraint.Operator;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class TypeSystemUtils {

    public static boolean isPrimitive(TypeDef t) {
        return switch (t) {
            case UnionType u -> u.members().stream().allMatch(TypeSystemUtils::isPrimitive);
            case IntersectionType i -> i.members().stream().anyMatch(TypeSystemUtils::isPrimitive);
            case NominalDef n -> n.attributes().contains(dev.akre.covenant.api.TypeAttribute.STRING_SEMANTICS) ||
                    n.attributes().contains(dev.akre.covenant.api.TypeAttribute.NUMERIC_SEMANTICS) ||
                    n.attributes().contains(dev.akre.covenant.api.TypeAttribute.BOOLEAN_SEMANTICS) ||
                    n.attributes().contains(dev.akre.covenant.api.TypeAttribute.NULL_SEMANTICS);
            case GenericTypeDef g -> isPrimitive(g.template());
            default -> false;
        };
    }

    public static TypeDef valueTypeOf(AbstractTypeSystem system, TypeDef t) {
        return switch (t) {
            case UnionType u -> system.unionDef(u.members().stream()
                    .map(m -> valueTypeOf(system, m))
                    .toArray(TypeDef[]::new));
            case IntersectionType i -> system.intersectDef(i.members().stream()
                    .map(m -> valueTypeOf(system, m))
                    .toArray(TypeDef[]::new));
            case GenericTypeDef g -> {
                List<TypeDef> specificValueTypes = new ArrayList<>();
                TypeDef spreadType = null;
                for (TypeDefParam tp : g.parameters()) {
                    if (tp instanceof TypeDefParam.Named n) {
                        specificValueTypes.add(n.type());
                    } else if (tp instanceof TypeDefParam.Positional pos) {
                        specificValueTypes.add(pos.type());
                    } else if (tp instanceof TypeDefParam.Constrained c) {
                        specificValueTypes.add(c.type());
                    } else if (tp instanceof TypeDefParam.Spread s) {
                        spreadType = s.type();
                    }
                }
                TypeDef result;
                if (!specificValueTypes.isEmpty()) {
                    result = system.unionDef(specificValueTypes.toArray(new TypeDef[0]));
                } else if (spreadType != null) {
                    result = spreadType;
                } else {
                    result = system.bottomDef();
                }
                yield result;
            }
            case NominalDef n when n.attributes().contains(dev.akre.covenant.api.TypeAttribute.ARRAY) ||
                    n.attributes().contains(dev.akre.covenant.api.TypeAttribute.OBJECT) -> system.topDef();
            default -> system.bottomDef();
        };
    }

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
        switch (subject) {
            case UnionType u -> {
                return system.unionDef(u.members().stream()
                        .map(m -> termAt(system, m, segment))
                        .toArray(TypeDef[]::new));
            }
            case IntersectionType i -> {
                return system.intersectDef(i.members().stream()
                        .map(m -> termAt(system, m, segment))
                        .toArray(TypeDef[]::new));
            }
            case NegationType n -> {
                return system.negateDef(termAt(system, n.inner(), segment));
            }
            case GenericTypeDef g -> {
                String resolvedSegment = getResolvedSegment(segment);

                if (resolvedSegment == null) {
                    return system.bottomDef();
                } else if (g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
                    TypeDefParam.Named named = findNamed(g, resolvedSegment);
                    if (named == null && (resolvedSegment.startsWith("'") && resolvedSegment.endsWith("'"))) {
                        named = findNamed(g, resolvedSegment.substring(1, resolvedSegment.length() - 1));
                    }
                    if (named != null) {
                        return named.type();
                    }
                    // Check dynamic constraints
                    List<TypeDef> matchingConstraints = new java.util.ArrayList<>();
                    for (TypeDefParam tp : g.parameters()) {
                        if (tp instanceof TypeDefParam.Constrained c && matches(c, resolvedSegment)) {
                            matchingConstraints.add(tp.type());
                        }
                    }
                    if (!matchingConstraints.isEmpty()) {
                        return matchingConstraints.size() == 1 ? matchingConstraints.get(0) : system.intersectDef(matchingConstraints.toArray(new TypeDef[0]));
                    }
                    // Check if open
                    for (TypeDefParam tp : g.parameters()) {
                        if (tp instanceof TypeDefParam.Spread) {
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
            default -> {
            }
        }
        return system.bottomDef();
    }

    private static @Nullable String getResolvedSegment(TypeDef segment) {
        if (segment instanceof SymbolType(String value)) {
            return value;
        } else if (segment instanceof StringConstraint(
                Operator operator, String value
        ) && operator == Operator.EQ) {
            return value;
        } else if (segment instanceof NumberConstraint(
                Operator operator, java.math.BigDecimal value
        ) && operator == Operator.EQ) {
            return value.toPlainString();
        } else if (segment instanceof IntersectionType i) {
            for (TypeDef member : i.members()) {
                String resolved = getResolvedSegment(member);
                if (resolved != null) return resolved;
            }
        }
        return null;
    }

    public static boolean matches(TypeDefParam.Constrained c, String name) {
        if (c.constraint() instanceof RegexConstraint rc && rc.operator() == Operator.MATCHES) {
            return matches(rc, name);
        }
        if (c.constraint() instanceof StringConstraint sc && sc.operator() == Operator.MATCHES) {
            return matches(sc.value(), name);
        }
        return false;
    }

    public static boolean matches(RegexConstraint rc, String name) {
        if (rc.operator() != Operator.MATCHES) return false;
        return matches(rc.value(), name);
    }

    private static boolean matches(String regex, String name) {
        try {
            return com.google.re2j.Pattern.compile(regex).matcher(name).find();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRegexSubset(String sub, String sup) {
        if (sub.equals(sup)) return true;
        try {
            Automaton subA = toAutomaton(sub);
            Automaton supA = toAutomaton(sup);
            return subA.subsetOf(supA);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRegexSubset(TypeDefParam.Constrained sub, TypeDefParam.Constrained sup) {
        String subVal = null, supVal = null;
        Automaton subA = null, supA = null;

        if (sub.constraint() instanceof RegexConstraint rbc) {
            subVal = rbc.value();
            subA = rbc.automaton();
        } else if (sub.constraint() instanceof StringConstraint sbc) {
            subVal = sbc.value();
            subA = toAutomaton(subVal);
        }

        if (sup.constraint() instanceof RegexConstraint rpc) {
            supVal = rpc.value();
            supA = rpc.automaton();
        } else if (sup.constraint() instanceof StringConstraint spc) {
            supVal = spc.value();
            supA = toAutomaton(supVal);
        }

        if (subVal == null || supVal == null) return false;
        if (subVal.equals(supVal)) return true;
        try {
            return subA.subsetOf(supA);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean doRegexesOverlap(String r1, String r2) {
        if (r1.equals(r2)) return true;
        try {
            Automaton a1 = toAutomaton(r1);
            Automaton a2 = toAutomaton(r2);
            return !a1.intersection(a2).isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean doRegexesOverlap(TypeDefParam.Constrained c1, TypeDefParam.Constrained c2) {
        String v1 = null, v2 = null;
        Automaton a1 = null, a2 = null;

        if (c1.constraint() instanceof RegexConstraint rc1) {
            v1 = rc1.value();
            a1 = rc1.automaton();
        } else if (c1.constraint() instanceof StringConstraint sc1) {
            v1 = sc1.value();
            a1 = toAutomaton(v1);
        }

        if (c2.constraint() instanceof RegexConstraint rc2) {
            v2 = rc2.value();
            a2 = rc2.automaton();
        } else if (c2.constraint() instanceof StringConstraint sc2) {
            v2 = sc2.value();
            a2 = toAutomaton(v2);
        }

        if (v1 == null || v2 == null) return false;
        if (v1.equals(v2)) return true;
        try {
            return !a1.intersection(a2).isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    public static Automaton toAutomaton(String regex) {
        return new RegExp(translateRegex(stripRegex(regex))).toAutomaton();
    }

    private static String translateRegex(String regex) {
        String translated = regex;
        boolean anchoredStart = translated.startsWith("^");
        if (anchoredStart) {
            translated = translated.substring(1);
        }
        boolean anchoredEnd = translated.endsWith("$");
        if (anchoredEnd) {
            translated = translated.substring(0, translated.length() - 1);
        }

        if (!anchoredStart) {
            translated = ".*" + translated;
        }
        if (!anchoredEnd) {
            translated = translated + ".*";
        }
        return translated;
    }

    private static String stripRegex(String regex) {
        if (regex.startsWith("\"") && regex.endsWith("\"")) {
            return regex.substring(1, regex.length() - 1);
        } else if (regex.startsWith("/") && regex.endsWith("/")) {
            return regex.substring(1, regex.length() - 1);
        }
        return regex;
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
            AbstractTypeSystem ignoredSystem,
            NominalDef type,
            Collection<String> parentNames,
            TypeAttribute attribute) {
        EnumSet<TypeAttribute> newAttributes = append(type.attributes(), attribute);
        Set<String> newNames = concat(type.parentNames(), parentNames);
        return switch (type) {
            case TopType ignored -> throw new IllegalArgumentException("cannot modify top");
            case BottomType ignored -> throw new IllegalArgumentException("cannot modify bottom");
            case AtomType a -> new AtomType(a.name(), newNames, newAttributes);
            case TemplateType t -> new TemplateType(t.name(), newNames, t.constructor(), newAttributes);
        };
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

}
