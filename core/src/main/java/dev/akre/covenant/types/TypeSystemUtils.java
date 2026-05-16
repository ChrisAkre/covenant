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
                    for (TypeDefParam tp : g.parameters()) {
                        if (tp instanceof TypeDefParam.Constrained c && matches(c, resolvedSegment)) {
                            return tp.type(); // Simplification: return first match
                        }
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
        String resolvedSegment = null;
        if (segment instanceof SymbolType(String value)) {
            resolvedSegment = value;
        } else if (segment instanceof StringConstraint(
                Operator operator, String value
        ) && operator == Operator.EQ) {
            resolvedSegment = value;
        } else if (segment instanceof NumberConstraint(
                Operator operator, java.math.BigDecimal value
        ) && operator == Operator.EQ) {
            resolvedSegment = value.toPlainString();
        }
        return resolvedSegment;
    }

    public static boolean matches(TypeDefParam.Constrained c, String name) {
        if (!c.keyword().equals("matches")) return false;
        String regex = c.value();
        if (regex.startsWith("\"") && regex.endsWith("\"")) {
            regex = regex.substring(1, regex.length() - 1);
        } else if (regex.startsWith("/") && regex.endsWith("/")) {
            regex = regex.substring(1, regex.length() - 1);
        }
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
        if (sub.value().equals(sup.value())) return true;
        try {
            return sub.automaton().subsetOf(sup.automaton());
        } catch (Exception e) {
            return false;
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
