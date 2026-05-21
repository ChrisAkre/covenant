package dev.akre.covenant.types;

import dev.akre.covenant.types.parser.Parser;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Set;

public record BooleanConstraint(Operator operator, boolean value) implements ValueConstraint {

    @Override
    public String keywordString() {
        return operator.symbol;
    }

    @Override
    public String valueString() {
        return String.valueOf(value);
    }

    public static Parser<TypeExpr> parser() {
        return input -> {
            if (input.head().type() == Parser.TokenType.IDENTIFIER) {
                Operator op = Operator.fromSymbol(input.head().value());
                if (op == Operator.EQ || op == Operator.NEQ) {
                    Parser.InputState tail = input.tail();
                    if (tail.head().type() == Parser.TokenType.IDENTIFIER) {
                        String val = tail.head().value();
                        if (val.equals("true") || val.equals("false")) {
                            return new Parser.Success<>(new TypeExpr.ConstraintExpr(new BooleanConstraint(op, Boolean.parseBoolean(val))), tail.tail());
                        }
                    }
                }
            }
            return new Parser.Failure<>("Not a boolean constraint", input);
        };
    }

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef def) {
        if (!(def instanceof BooleanConstraint other)) {
            return null;
        } else if (this.equals(other)) {
            return Set.of(this);
        } else if (this.satisfiesOther(system, other)) {
            return Set.of(this);
        } else if (this.operator.isDisjoint(other.operator, this.value, other.value)) {
            return Set.of();
        } else {
            return null;
        }
    }

    @Override
    public Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef def) {
        if (!(def instanceof BooleanConstraint other)) {
            return null;
        } else if (this.equals(other)) {
            return Set.of(this);
        } else  if (this.satisfiesOther(system, other)) {
            return Set.of(other);
        } else {
            return null;
        }
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
    public  @NonNull String toString() {
        return repr();
    }
}
