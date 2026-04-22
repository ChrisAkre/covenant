package dev.akre.covenant.types;

import java.util.Collection;
import java.util.Set;

public record BooleanConstraint(Operator operator, boolean value) implements ValueConstraint {

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef def) {
        if (!(def instanceof BooleanConstraint other)) {
            return null;
        } else if (this.equals(other)) {
            return Set.of(this);
        }

        if (this.satisfiesOther(system, other)) {
            return Set.of(this);
        } else if (other.satisfiesOther(system, this)) {
            return Set.of(other);
        }

        if (this.operator.isDisjoint(other.operator, this.value, other.value)) {
            return Set.of();
        }
        return null;
    }

    @Override
    public Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef def) {
        if (!(def instanceof BooleanConstraint other)) {
            return null;
        } else if (this.equals(other)) {
            return Set.of(this);
        }

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
                    default -> null;
                };
        return invOp != null ? Set.of(new BooleanConstraint(invOp, value)) : null;
    }

    @Override
    public boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
        if (!(other instanceof BooleanConstraint(Operator otherOperator, boolean otherValue))) {
            return false;
        }
        return this.operator.satisfies(otherOperator, this.value, otherValue);
    }

    @Override
    public String repr() {
        String val = String.valueOf(value);
        if (operator == Operator.EQ) {
            return val;
        }
        return operator.symbol + " " + val;
    }

    @Override
    public String toString() {
        return repr();
    }
}
