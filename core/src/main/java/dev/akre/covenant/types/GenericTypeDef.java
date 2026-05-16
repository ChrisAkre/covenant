package dev.akre.covenant.types;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A type created by applying parameters to a TemplateType.
 */
public record GenericTypeDef(
        TemplateType template,
        AbstractTypeSystemBuilder.PatternConstructor.Pattern pattern,
        List<TypeDefParam> parameters)
        implements TypeDef {
    public GenericTypeDef(
            TemplateType template,
            AbstractTypeSystemBuilder.PatternConstructor.Pattern pattern,
            List<TypeDefParam> parameters) {
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

            if (pattern == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
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
        boolean otherIsOpen = !(otherSpreadType instanceof BottomType);

        // 1. Check all explicit and constrained parameters in 'this'
        for (TypeDefParam tp1 : this.parameters) {
            if (tp1 instanceof TypeDefParam.Named n1) {
                TypeDef requiredType = TypeSystemUtils.termAt(system, other, n1.name());
                if (!system.satisfies(n1.type(), requiredType)) return false;
            } else if (tp1 instanceof TypeDefParam.Constrained c1) {
                // Find all CONSTRAINTS in 'other' that overlap with c1
                List<TypeDef> overlappingConstraints = new java.util.ArrayList<>();
                for (TypeDefParam tp2 : other.parameters) {
                    if (tp2 instanceof TypeDefParam.Constrained c2 && c1.keyword().equals(c2.keyword())) {
                        if (TypeSystemUtils.doRegexesOverlap(c1, c2)) {
                            overlappingConstraints.add(c2.type());
                        }
                    }
                }
                
                // c1 must satisfy the intersection of all overlapping constraints in 'other'
                if (!overlappingConstraints.isEmpty()) {
                    TypeDef requiredType = overlappingConstraints.size() == 1 ? overlappingConstraints.get(0) : system.intersectDef(overlappingConstraints.toArray(new TypeDef[0]));
                    if (!system.satisfies(c1.type(), requiredType)) return false;
                }
                
                // If c1 matched no constraints in 'other', it must satisfy other's spread
                // NOTE: We don't check against other's Named properties here because 
                // Loop 2 handles ensuring those specific keys are strict enough.
                if (overlappingConstraints.isEmpty()) {
                    if (!otherIsOpen || !system.satisfies(c1.type(), otherSpreadType)) return false;
                }
            } else if (tp1 instanceof TypeDefParam.Spread s1) {
                // Subtype spread must satisfy supertype spread
                if (!otherIsOpen || !system.satisfies(s1.type(), otherSpreadType)) return false;
                
                // ALSO, any property allowed by supertype but not explicitly in subtype 
                // must be allowed by subtype spread (unless it matches a dynamic constraint).
                for (TypeDefParam tp2 : other.parameters) {
                    if (tp2 instanceof TypeDefParam.Named n2 && findNamed(n2.name()) == null) {
                        boolean governedByConstraint = false;
                        for (TypeDefParam subTp : this.parameters) {
                            if (subTp instanceof TypeDefParam.Constrained c && TypeSystemUtils.matches(c, n2.name())) {
                                governedByConstraint = true;
                                break;
                            }
                        }
                        if (!governedByConstraint) {
                            if (!system.satisfies(s1.type(), n2.type())) return false;
                        }
                    } else if (tp2 instanceof TypeDefParam.Constrained c2 && findConstrained(c2.keyword(), c2.value()) == null) {
                        if (!system.satisfies(s1.type(), c2.type())) return false;
                    }
                }
            }
        }

        // 2. Every REQUIRED property that 'other' MUST have must be present in 'this'
        for (TypeDefParam tp2 : other.parameters) {
            if (tp2 instanceof TypeDefParam.Named n2 && !n2.optional()) {
                TypeDef thisType = TypeSystemUtils.termAt(system, this, n2.name());
                // A forbidden property (bottom) cannot satisfy a required property (unless it's also bottom).
                if ((system.wrap(thisType).isBottom() && !system.wrap(n2.type()).isBottom()) || !system.satisfies(thisType, n2.type())) {
                    TypeDef nullType = system.nilDef();
                    if (nullType == null || !system.satisfies(nullType, n2.type())) {
                        return false;
                    }
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
                        if (name.contains(" ") || com.google.re2j.Pattern.matches("\\d+", name)) {
                            name = "'" + name.replace("'", "''") + "'";
                        }
                        return name
                                + (named.optional() ? "?: " : ": ")
                                + tp.type().repr();
                    }
                    if (tp instanceof TypeDefParam.Constrained constrained) {
                        String name = constrained.value();
                        if (name.contains(" ") || com.google.re2j.Pattern.matches("\\d+", name)) {
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
            if (pattern == AbstractTypeSystemBuilder.PatternConstructor.Pattern.POSITIONAL && otherGeneric.pattern == AbstractTypeSystemBuilder.PatternConstructor.Pattern.POSITIONAL) {
                if (!this.satisfiesOther(system, otherGeneric) && !otherGeneric.satisfiesOther(system, this)) {
                    // Positional types are generally disjoint if they don't satisfy each other
                    return Set.of();
                }
            } else if (pattern == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT && otherGeneric.pattern == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
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
                        if (tp2 instanceof TypeDefParam.Named n2) {
                            TypeDef t2 = n2.type();
                            TypeDef intersected = system.intersectDef(t1, t2);
                            if (system.wrap(intersected).isBottom()) return Set.of();
                            mergedParams.add(new TypeDefParam.Named(
                                    intersected,
                                    n1.name(),
                                    n1.optional() && n2.optional()));
                        } else {
                            TypeDef constraintType = null;
                            for (TypeDefParam tp : otherGeneric.parameters) {
                                if (tp instanceof TypeDefParam.Constrained c && TypeSystemUtils.matches(c, n1.name())) {
                                    constraintType = tp.type();
                                    break;
                                }
                            }
                            TypeDef t2 = constraintType != null ? constraintType : t2Spread;
                            TypeDef intersected = system.intersectDef(t1, t2);
                            if (system.wrap(intersected).isBottom() && !n1.optional()) return Set.of();
                            if (!system.wrap(intersected).isBottom()) {
                                mergedParams.add(new TypeDefParam.Named(intersected, n1.name(), n1.optional()));
                            }
                        }
                        processedNamed.add(n1.name());
                    } else if (tp1 instanceof TypeDefParam.Constrained c1) {
                        TypeDefParam tp2 = otherGeneric.findConstrained(c1.keyword(), c1.value());
                        TypeDef t1 = c1.type();
                        String key = c1.keyword() + ":" + c1.value();
                        if (tp2 instanceof TypeDefParam.Constrained c2) {
                            TypeDef t2 = c2.type();
                            TypeDef intersected = system.intersectDef(t1, t2);
                            if (system.wrap(intersected).isBottom()) return Set.of();
                            mergedParams.add(new TypeDefParam.Constrained(
                                    intersected,
                                    c1.keyword(),
                                    c1.value(),
                                    c1.optional() && c2.optional()));
                        } else {
                            TypeDef intersected = system.intersectDef(t1, t2Spread);
                            if (system.wrap(intersected).isBottom() && !c1.optional()) return Set.of();
                            if (!system.wrap(intersected).isBottom()) {
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
                        TypeDef constraintType = null;
                        for (TypeDefParam tp : this.parameters) {
                            if (tp instanceof TypeDefParam.Constrained c && TypeSystemUtils.matches(c, n2.name())) {
                                constraintType = tp.type();
                                break;
                            }
                        }
                        TypeDef t1 = constraintType != null ? constraintType : t1Spread;
                        TypeDef intersected = system.intersectDef(t2, t1);
                        if (system.wrap(intersected).isBottom() && !n2.optional()) return Set.of();
                        if (!system.wrap(intersected).isBottom()) {
                            mergedParams.add(new TypeDefParam.Named(intersected, n2.name(), n2.optional()));
                        }
                    } else if (tp2 instanceof TypeDefParam.Constrained c2) {
                        String key = c2.keyword() + ":" + c2.value();
                        if (processedConstrained.contains(key)) continue;
                        TypeDef t2 = c2.type();
                        TypeDef intersected = system.intersectDef(t2, t1Spread);
                        if (system.wrap(intersected).isBottom() && !c2.optional()) return Set.of();
                        if (!system.wrap(intersected).isBottom()) {
                            mergedParams.add(new TypeDefParam.Constrained(
                                    intersected, c2.keyword(), c2.value(), c2.optional()));
                        }
                    }
                }

                if (thisOpen && otherOpen) {
                    TypeDef intersected = system.intersectDef(t1Spread, t2Spread);
                    if (!system.wrap(intersected).isBottom()) {
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
                && pattern == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT
                && otherGeneric.pattern == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
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
                || o instanceof GenericTypeDef other
                        && pattern == other.pattern
                        && Objects.equals(template, other.template)
                        && Objects.equals(parameters, other.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(template, pattern, parameters);
    }
}
