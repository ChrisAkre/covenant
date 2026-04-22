package dev.akre.covenant.types;

import java.util.Collection;
import java.util.Set;

public record StringConstraint(Operator operator, String value) implements ValueConstraint {

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef def) {
        if (!(def instanceof StringConstraint other)) return null;
        if (this.equals(other)) return Set.of(this);

        if (this.satisfiesOther(system, other)) return Set.of(this);
        if (other.satisfiesOther(system, this)) return Set.of(other);

        if (this.operator.isDisjoint(other.operator, this.value, other.value)) {
            return Set.of();
        }
        return null;
    }

    @Override
    public Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef def) {
        if (!(def instanceof StringConstraint other)) return null;
        if (this.equals(other)) return Set.of(this);

        if (this.satisfiesOther(system, other)) return Set.of(other);
        if (other.satisfiesOther(system, this)) return Set.of(this);

        return null;
    }

    @Override
    public Collection<TypeDef> invert(AbstractTypeSystem system) {
        Operator invOp =
                switch (operator) {
                    case EQ -> Operator.NEQ;
                    case NEQ -> Operator.EQ;
                    case GT -> Operator.LTE;
                    case GTE -> Operator.LT;
                    case LT -> Operator.GTE;
                    case LTE -> Operator.GT;
                    case MATCHES -> Operator.NOT_MATCHES;
                    case NOT_MATCHES -> Operator.MATCHES;
                };
        return Set.of(new StringConstraint(invOp, value));
    }

    @Override
    public boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
        if (!(other instanceof StringConstraint(Operator otherOperator, String otherValue))) {
            return system.find("String")
                    .map(base -> system.satisfies(((OwnedTypeDef) base).def(), other))
                    .orElse(false);
        }
        return this.operator.satisfies(otherOperator, this.value, otherValue);
    }

    @Override
    public String repr() {
        String quoted = "\"" + value.replace("\"", "\"\"") + "\"";
        if (operator == Operator.EQ) {
            return quoted;
        }
        return operator.symbol + " " + quoted;
    }

    @Override
    public String toString() {
        return repr();
    }
}
