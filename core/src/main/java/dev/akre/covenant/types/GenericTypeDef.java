package dev.akre.covenant.types;

import dev.akre.covenant.types.AbstractTypeSystemBuilder.PatternConstructor.Pattern;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A type created by applying parameters to a TemplateType.
 */
public record GenericTypeDef(TemplateType template, Pattern pattern, List<TypeDefParam> parameters) implements TypeDef {
    public GenericTypeDef(TemplateType template, Pattern pattern, List<TypeDefParam> parameters) {
        this.template = template;
        this.pattern = pattern;
        this.parameters = List.copyOf(parameters);
    }

    @Override
    public java.util.EnumSet<dev.akre.covenant.api.TypeAttribute> attributes() {
        return template.attributes();
    }

    private TypeDefParam findNamed(String name) {
        for (TypeDefParam tp : parameters) {
            if (tp instanceof TypeDefParam.Named n && n.name().equals(name)) {
                return tp;
            }
        }
        return null;
    }

    private TypeDefParam findConstrained(String k, String v) {
        for (TypeDefParam tp : parameters) {
            if (tp instanceof TypeDefParam.Constrained c
                    && c.keyword().equals(k)
                    && c.value().equals(v)) {
                return tp;
            }
        }
        return null;
    }

    private TypeDefParam findPositional(int index) {
        for (TypeDefParam tp : parameters) {
            if (tp instanceof TypeDefParam.Positional pos && pos.index() == index) {
                return tp;
            }
        }
        return null;
    }

    public TypeDef spreadParam() {
        return this.parameters.stream()
                .filter(tp -> tp instanceof TypeDefParam.Spread)
                .findFirst()
                .map(TypeDefParam::type)
                .orElse(BottomType.INSTANCE);
    }

    @Override
    public boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
        if (other instanceof NominalDef n && n.name().equals(template.name())) {
            return true;
        }

        if (other instanceof GenericTypeDef otherGeneric) {
            if (!template.name().equals(otherGeneric.template().name())) {
                return false;
            }

            if (pattern == Pattern.OBJECT) {
                return satisfiesObject(system, otherGeneric);
            } else {
                return satisfiesPositional(system, otherGeneric);
            }
        }
        return false;
    }

    private boolean satisfiesPositional(AbstractTypeSystem system, GenericTypeDef other) {
        TypeDef nullType = system.nilDef();

        int i = 0;
        int j = 0;
        while (i < this.parameters.size() && j < other.parameters.size()) {
            TypeDefParam tp1 = this.parameters.get(i);
            TypeDefParam tp2 = other.parameters.get(j);

            if (tp1 instanceof TypeDefParam.Positional pos1
                    && tp2 instanceof TypeDefParam.Positional pos2) {

                TypeDef type1 = tp1.type();
                TypeDef type2 = tp2.type();

                if (pos1.variadic()) { // Source is variadic (e.g. S...)
                    if (!pos2.variadic()) { // Target is fixed (e.g. T)
                        // Unroll source: S... satisfies T if (S | Null) satisfies T
                        if (!system.satisfies(system.unionDef(type1, nullType), type2)) {
                            return false;
                        }
                        j++; // Advance target, source variadic stays
                        continue;
                    }
                    // Both variadic: S... satisfies T... if S satisfies T
                    return system.satisfies(type1, type2);
                }

                if (pos2.variadic()) { // Target is variadic (e.g. T...)
                    // Match ALL remaining source fixed elements to this target variadic
                    while (i < this.parameters.size()) {
                        TypeDefParam rem = this.parameters.get(i);
                        if (rem instanceof TypeDefParam.Positional remPos) {
                            TypeDef t = rem.type();
                            TypeDef sourceType = remPos.variadic() ? system.unionDef(t, nullType) : t;
                            if (!system.satisfies(sourceType, system.unionDef(type2, nullType))) return false;
                        }
                        i++;
                    }
                    return true;
                }

                // Both fixed: S satisfies T
                if (!system.satisfies(type1, type2)) {
                    return false;
                }
                i++;
                j++;
            } else {
                i++;
                j++; // Skip non-positional params
            }
        }

        // If source ran out, target remaining must be variadic (which are inherently optional)
        while (j < other.parameters.size()
                && other.parameters.get(j) instanceof TypeDefParam.Positional p
                && p.variadic()) {
            j++;
        }

        // Success if we consumed all parameters
        return i == this.parameters.size() && j == other.parameters.size();
    }

    private boolean satisfiesObject(AbstractTypeSystem system, GenericTypeDef other) {
        TypeDef thisSpreadType = this.spreadParam();
        TypeDef otherSpreadType = other.spreadParam();
        boolean thisOpen = !(thisSpreadType instanceof BottomType);
        boolean otherIsOpen = !(otherSpreadType instanceof BottomType);

        for (TypeDefParam tp2 : other.parameters) {
            if (tp2 instanceof TypeDefParam.Named n2) {
                TypeDef type = tp2.type();
                TypeDefParam tp1 = findNamed(n2.name());
                if (tp1 == null) {
                    if (thisOpen) {
                        if (!system.satisfies(thisSpreadType, type)) return false;
                    } else if (!otherIsOpen) {
                        // Field missing in source. Allowed if target is optional OR accepts Null.
                        TypeDef nullType = system.nilDef();
                        boolean acceptsNull = nullType != null && system.satisfies(nullType, type);
                        if (!n2.optional() && !acceptsNull) {
                            return false;
                        }
                    }
                } else {
                    if (!system.satisfies(tp1.type(), type)) {
                        return false;
                    }
                }
            } else if (tp2 instanceof TypeDefParam.Constrained c2) {
                TypeDefParam tp1 = findConstrained(c2.keyword(), c2.value());
                if (tp1 == null) {
                    if (thisOpen) {
                        if (!system.satisfies(thisSpreadType, tp2.type())) return false;
                    } else if (!otherIsOpen) {
                        return false;
                    }
                } else {
                    if (!system.satisfies(tp1.type(), tp2.type())) {
                        return false;
                    }
                }
            }
        }

        for (TypeDefParam tp1 : this.parameters) {
            if (tp1 instanceof TypeDefParam.Named n1) {
                if (other.findNamed(n1.name()) == null) {
                    if (!otherIsOpen) {
                        return false;
                    } else if (otherSpreadType != null) {
                        if (!system.satisfies(tp1.type(), otherSpreadType)) {
                            return false;
                        }
                    }
                }
            } else if (tp1 instanceof TypeDefParam.Constrained c1) {
                if (other.findConstrained(c1.keyword(), c1.value()) == null) {
                    if (!otherIsOpen) {
                        return false;
                    } else if (otherSpreadType != null) {
                        if (!system.satisfies(tp1.type(), otherSpreadType)) {
                            return false;
                        }
                    }
                }
            } else if (tp1 instanceof TypeDefParam.Spread s1) {
                if (!otherIsOpen) {
                    return false;
                }
                if (otherSpreadType != null && !system.satisfies(tp1.type(), otherSpreadType)) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public String repr() {
        String inner = parameters.stream()
                .map(tp -> {
                    if (tp instanceof TypeDefParam.Positional pos) {
                        return tp.type().repr() + (pos.variadic() ? "..." : "");
                    }
                    if (tp instanceof TypeDefParam.Named named) {
                        String name = named.name();
                        // Quote if contains spaces or is a number
                        if (name.contains(" ") || name.matches("\\d+")) {
                            name = "'" + name.replace("'", "''") + "'";
                        }
                        return name
                                + (named.optional() ? "?: " : ": ")
                                + tp.type().repr();
                    }
                    if (tp instanceof TypeDefParam.Constrained constrained) {
                        String name = constrained.value();
                        if (name.contains(" ") || name.matches("\\d+")) {
                            name = "'" + name.replace("'", "''") + "'";
                        }
                        return "[" + constrained.keyword() + " " + name + "]" + (constrained.optional() ? "?: " : ": ")
                                + tp.type().repr();
                    }
                    return "...";
                })
                .collect(Collectors.joining(", "));

        return template.name() + "<" + inner + ">";
    }

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef other) {
        if (this.satisfiesOther(system, other)) {
            return Set.of(this);
        } else if (other.satisfiesOther(system, this)) {
            return Set.of(other);
        }

        // Disjoint templates
        if (other instanceof GenericTypeDef g && !g.template().equals(template)) return Set.of();
        if (other instanceof TemplateType t && !t.equals(template)) return Set.of();
        if (other instanceof AtomType a && !system.satisfies(template, a)) return Set.of();

        if (other instanceof GenericTypeDef otherGeneric && template.equals(otherGeneric.template())) {
            if (pattern == Pattern.POSITIONAL && otherGeneric.pattern == Pattern.POSITIONAL) {
                if (!this.satisfiesOther(system, otherGeneric) && !otherGeneric.satisfiesOther(system, this)) {
                    // Positional types are generally disjoint if they don't satisfy each other
                    // (this is a simplification for Array/Tuple behavior)
                    return Set.of();
                }
            } else if (pattern == Pattern.OBJECT && otherGeneric.pattern == Pattern.OBJECT) {
                List<TypeDefParam> mergedParams = new java.util.ArrayList<>();

                TypeDefParam s1 = this.parameters.stream()
                        .filter(tp -> tp instanceof TypeDefParam.Spread)
                        .findFirst()
                        .orElse(null);
                TypeDefParam s2 = otherGeneric.parameters.stream()
                        .filter(tp -> tp instanceof TypeDefParam.Spread)
                        .findFirst()
                        .orElse(null);

                boolean thisOpen = s1 != null;
                boolean otherOpen = s2 != null;
                TypeDef t1Spread = thisOpen ? s1.type() : system.bottomDef();
                TypeDef t2Spread = otherOpen ? s2.type() : system.bottomDef();

                java.util.Set<String> processedNamed = new java.util.HashSet<>();
                java.util.Set<String> processedConstrained = new java.util.HashSet<>();

                for (TypeDefParam tp1 : this.parameters) {
                    if (tp1 instanceof TypeDefParam.Named n1) {
                        TypeDefParam tp2 = otherGeneric.findNamed(n1.name());
                        TypeDef t1 = n1.type();
                        if (tp2 != null) {
                            TypeDef t2 = tp2.type();
                            TypeDef intersected = system.intersectDef(t1, t2);
                            if (intersected instanceof BottomType) return Set.of();
                            mergedParams.add(new TypeDefParam.Named(
                                    intersected,
                                    n1.name(),
                                    n1.optional() && ((TypeDefParam.Named) tp2).optional()));
                        } else {
                            // Property in this, not in other.
                            // Intersect with other's spread (which is bottom if closed).
                            TypeDef intersected = system.intersectDef(t1, t2Spread);
                            if (intersected instanceof BottomType && !n1.optional()) return Set.of();
                            if (!(intersected instanceof BottomType)) {
                                mergedParams.add(new TypeDefParam.Named(intersected, n1.name(), n1.optional()));
                            }
                        }
                        processedNamed.add(n1.name());
                    } else if (tp1 instanceof TypeDefParam.Constrained c1) {
                        TypeDefParam tp2 = otherGeneric.findConstrained(c1.keyword(), c1.value());
                        TypeDef t1 = c1.type();
                        String key = c1.keyword() + ":" + c1.value();
                        if (tp2 != null) {
                            TypeDef t2 = tp2.type();
                            TypeDef intersected = system.intersectDef(t1, t2);
                            if (intersected instanceof BottomType) return Set.of();
                            mergedParams.add(new TypeDefParam.Constrained(
                                    intersected,
                                    c1.keyword(),
                                    c1.value(),
                                    c1.optional() && ((TypeDefParam.Constrained) tp2).optional()));
                        } else {
                            TypeDef intersected = system.intersectDef(t1, t2Spread);
                            if (intersected instanceof BottomType && !c1.optional()) return Set.of();
                            if (!(intersected instanceof BottomType)) {
                                mergedParams.add(new TypeDefParam.Constrained(
                                        intersected, c1.keyword(), c1.value(), c1.optional()));
                            }
                        }
                        processedConstrained.add(key);
                    }
                }

                for (TypeDefParam tp2 : otherGeneric.parameters) {
                    if (tp2 instanceof TypeDefParam.Named n2) {
                        if (processedNamed.contains(n2.name())) continue;
                        TypeDef t2 = n2.type();
                        TypeDef intersected = system.intersectDef(t2, t1Spread);
                        if (intersected instanceof BottomType && !n2.optional()) return Set.of();
                        if (!(intersected instanceof BottomType)) {
                            mergedParams.add(new TypeDefParam.Named(intersected, n2.name(), n2.optional()));
                        }
                    } else if (tp2 instanceof TypeDefParam.Constrained c2) {
                        String key = c2.keyword() + ":" + c2.value();
                        if (processedConstrained.contains(key)) continue;
                        TypeDef t2 = c2.type();
                        TypeDef intersected = system.intersectDef(t2, t1Spread);
                        if (intersected instanceof BottomType && !c2.optional()) return Set.of();
                        if (!(intersected instanceof BottomType)) {
                            mergedParams.add(new TypeDefParam.Constrained(
                                    intersected, c2.keyword(), c2.value(), c2.optional()));
                        }
                    }
                }

                if (thisOpen && otherOpen) {
                    TypeDef intersected = system.intersectDef(t1Spread, t2Spread);
                    if (!(intersected instanceof BottomType)) {
                        mergedParams.add(new TypeDefParam.Spread(intersected));
                    }
                }

                return Set.of(new GenericTypeDef(template, pattern, mergedParams));
            }
        }

        return null;
    }

    @Override
    public Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef other) {
        if (this.satisfiesOther(system, other)) {
            return Set.of(other);
        } else if (other.satisfiesOther(system, this)) {
            return Set.of(this);
        } else if (other instanceof NominalDef n && n.equals(template)) {
            return Set.of(other);
        }

        if (other instanceof GenericTypeDef otherGeneric
                && template.equals(otherGeneric.template())
                && pattern == Pattern.OBJECT
                && otherGeneric.pattern == Pattern.OBJECT) {
            boolean thisOpen = !(this.spreadParam() instanceof BottomType);
            boolean otherOpen = !(otherGeneric.spreadParam() instanceof BottomType);

            if (thisOpen == otherOpen) {
                boolean canMerge = true;
                long thisCount = this.parameters.stream()
                        .filter(tp -> tp instanceof TypeDefParam.Named
                                || tp instanceof TypeDefParam.Constrained)
                        .count();
                long otherCount = otherGeneric.parameters.stream()
                        .filter(tp -> tp instanceof TypeDefParam.Named
                                || tp instanceof TypeDefParam.Constrained)
                        .count();

                if (thisCount != otherCount) {
                    canMerge = false;
                } else {
                    for (TypeDefParam tp1 : this.parameters) {
                        if (tp1 instanceof TypeDefParam.Named n1) {
                            TypeDefParam tp2 = otherGeneric.findNamed(n1.name());
                            if (!(tp2 instanceof TypeDefParam.Named n2) || n1.optional() != n2.optional()) {
                                canMerge = false;
                                break;
                            }
                        } else if (tp1 instanceof TypeDefParam.Constrained c1) {
                            TypeDefParam tp2 = otherGeneric.findConstrained(c1.keyword(), c1.value());
                            if (!(tp2 instanceof TypeDefParam.Constrained c2) || c1.optional() != c2.optional()) {
                                canMerge = false;
                                break;
                            }
                        }
                    }
                }

                if (canMerge) {
                    List<TypeDefParam> mergedParams = new java.util.ArrayList<>();
                    for (TypeDefParam tp1 : this.parameters) {
                        if (tp1 instanceof TypeDefParam.Named n1) {
                            TypeDefParam.Named n2 = (TypeDefParam.Named) otherGeneric.findNamed(n1.name());
                            mergedParams.add(new TypeDefParam.Named(
                                    system.unionDef(n1.type(), n2.type()),
                                    n1.name(), n1.optional()));
                        } else if (tp1 instanceof TypeDefParam.Constrained c1) {
                            TypeDefParam.Constrained c2 = (TypeDefParam.Constrained) otherGeneric.findConstrained(c1.keyword(), c1.value());
                            mergedParams.add(new TypeDefParam.Constrained(
                                    system.unionDef(c1.type(), c2.type()),
                                    c1.keyword(), c1.value(), c1.optional()));
                        } else if (tp1 instanceof TypeDefParam.Spread s1) {
                            TypeDefParam tp2Spread = otherGeneric.parameters().stream()
                                    .filter(tp -> tp instanceof TypeDefParam.Spread)
                                    .findFirst()
                                    .get();
                            mergedParams.add(new TypeDefParam.Spread(
                                    system.unionDef(s1.type(), tp2Spread.type())));
                        }
                    }
                    return Set.of(new GenericTypeDef(template, pattern, mergedParams));
                }
            }
        }

        return null;
    }

    @Override
    public Collection<TypeDef> invert(AbstractTypeSystem system) {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || o
                                instanceof
                                GenericTypeDef(
                                        TemplateType otherTemplate,
                                        Pattern otherPattern,
                                        List<TypeDefParam> otherParameters)
                        && pattern == otherPattern
                        && Objects.equals(template, otherTemplate)
                        && Objects.equals(parameters, otherParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(template, pattern, parameters);
    }
}
