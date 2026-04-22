package dev.akre.covenant.types;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public record NumberConstraint(Operator operator, BigDecimal value) implements ValueConstraint {

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef def) {
        if (!(def instanceof NumberConstraint other)) return null;
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
        if (!(def instanceof NumberConstraint other)) return null;
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
                    default -> throw new UnsupportedOperationException("Cannot invert " + operator);
                };
        return Set.of(new NumberConstraint(invOp, value));
    }

    @Override
    public boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
        if (!(other instanceof NumberConstraint nc)) {
            return system.find("Number")
                    .map(base -> system.satisfies(((OwnedTypeDef) base).def(), other))
                    .orElse(false);
        }
        return this.operator.satisfies(nc.operator(), this.value, nc.value());
    }

    @Override
    public String repr() {
        return operator.symbol + " " + value.toPlainString();
    }

    @Override
    public String toString() {
        return repr();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NumberConstraint that)) return false;
        return operator == that.operator && Objects.equals(value, that.value);
    }
}
