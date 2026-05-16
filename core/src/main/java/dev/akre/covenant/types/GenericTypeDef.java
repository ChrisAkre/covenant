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

    public TypeDefParam findNamed(String name) {
        for (TypeDefParam tp : parameters) {
            if (tp instanceof TypeDefParam.Named n && n.name().equals(name)) {
                return tp;
            }
        }
        return null;
    }

    public TypeDefParam findConstrained(String k, String v) {
        for (TypeDefParam tp : parameters) {
            if (tp instanceof TypeDefParam.Constrained c
                    && c.keyword().equals(k)
                    && c.value().equals(v)) {
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
                return StructuralTypeUtils.satisfiesObject(system, this, otherGeneric);
            } else {
                return StructuralTypeUtils.satisfiesPositional(system, this, otherGeneric);
            }
        }
        return false;
    }

    @Override
    public String repr() {
        String inner = parameters.stream()
                .map(TypeDefParam::repr)
                .collect(Collectors.joining(", "));

        return template.name() + "<" + inner + ">";
    }

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef other) {
        if (other instanceof LengthConstraint lc && lc.isDisjoint(this)) {
            return Set.of();
        }
        if (other instanceof IntersectionType intersection) {
            for (TypeDef member : intersection.members()) {
                if (member instanceof LengthConstraint lc && lc.isDisjoint(this)) {
                    return Set.of();
                }
                // If it's a GenericTypeDef in the intersection and disjoint, also prune it out
                if (member instanceof GenericTypeDef g && this.prune(system, g) != null && this.prune(system, g).isEmpty()) {
                    return Set.of();
                }
            }
        }
        if (this.satisfiesOther(system, other)) {
            if (!(other instanceof LengthConstraint)) {
                return Set.of(this);
            }
        } else if (other.satisfiesOther(system, this)) {
            if (!(other instanceof LengthConstraint)) {
                return Set.of(other);
            }
        }

        if (other instanceof IntersectionType intersection) {
            // Re-evaluate disjointness against members of intersection
            for (TypeDef member : intersection.members()) {
                if (this.prune(system, member) != null && this.prune(system, member).isEmpty()) {
                    return Set.of();
                }
            }
        }

        // Disjoint templates
        if (other instanceof GenericTypeDef g && !g.template().equals(template)) return Set.of();
        if (other instanceof TemplateType t && !t.equals(template)) return Set.of();
        if (other instanceof AtomType a && !system.satisfies(template, a)) return Set.of();

        if (other instanceof GenericTypeDef otherGeneric && template.equals(otherGeneric.template())) {
            if (pattern == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT && otherGeneric.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
                return StructuralTypeUtils.intersectObject(system, this, otherGeneric);
            } else if (pattern != AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT && otherGeneric.pattern() != AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
                return StructuralTypeUtils.intersectPositional(system, this, otherGeneric);
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

        if (other instanceof GenericTypeDef otherGeneric && template.equals(otherGeneric.template())) {
            if (pattern == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT && otherGeneric.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
                return StructuralTypeUtils.unionObject(system, this, otherGeneric);
            } else if (pattern != AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT && otherGeneric.pattern() != AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
                return StructuralTypeUtils.unionPositional(system, this, otherGeneric);
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
                        && pattern == other.pattern()
                        && Objects.equals(template, other.template())
                        && Objects.equals(parameters, other.parameters());
    }

    @Override
    public int hashCode() {
        return Objects.hash(template, pattern, parameters);
    }
}
