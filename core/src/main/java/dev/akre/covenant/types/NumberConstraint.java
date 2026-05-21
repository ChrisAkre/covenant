package dev.akre.covenant.types;

import dev.akre.covenant.types.parser.Parser;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public record NumberConstraint(Operator operator, BigDecimal value) implements ValueConstraint {

    @Override
    public String keywordString() {
        return operator.symbol;
    }

    @Override
    public String valueString() {
        return value.toPlainString();
    }

    public static Parser<TypeExpr> parser() {
        return input -> {
            if (input.head().type() == Parser.TokenType.IDENTIFIER) {
                Operator op = Operator.fromSymbol(input.head().value());
                if (op != null && op != Operator.MATCHES && op != Operator.NOT_MATCHES) {
                    Parser.InputState tail = input.tail();
                    if (tail.head().type() == Parser.TokenType.INT_LITERAL || tail.head().type() == Parser.TokenType.FLOAT_LITERAL) {
                        return new Parser.Success<>(new TypeExpr.ConstraintExpr(new NumberConstraint(op, new BigDecimal(tail.head().value()))), tail.tail());
                    }
                }
            }
            return new Parser.Failure<>("Not a number constraint", input);
        };
    }

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef def) {
        if (!(def instanceof NumberConstraint other)) return null;
        if (this.equals(other)) return Set.of(this);

        if (this.satisfiesOther(system, other)) return Set.of(this);

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
