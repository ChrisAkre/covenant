package dev.akre.covenant.types;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dk.brics.automaton.Automaton;

/**
 * The purely syntactic, unevaluated Abstract Syntax Tree for Covenant types.
 * * This tree performs NO mathematical canonicalization or constraint validation.
 * It merely holds the shape of the type expression until it is resolved with
 * concrete bindings at call-time, acting as the strict boundary between the
 * parser and the DNF physics engine.
 */
public sealed interface TypeExpr
        permits TypeExpr.ApplyExpr,
                TypeExpr.ConstraintExpr,
                TypeExpr.ParamExpr,
                TypeExpr.FloatExpr,
                TypeExpr.IntExpr,
                TypeExpr.IntersectionExpr,
                TypeExpr.NegationExpr,
                TypeExpr.NullExpr,
                TypeExpr.PathExpr,
                TypeExpr.RefExpr,
                TypeExpr.SignatureExpr,
                TypeExpr.SpreadExpr,
                TypeExpr.StringExpr,
                TypeExpr.SymbolExpr,
                TypeExpr.TupleExpr,
                TypeExpr.UnionExpr {

    record TupleExpr(List<TypeExpr> members) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return "(" + members.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
        }
    }

    final class ConstraintExpr implements TypeExpr {
        private final ValueConstraint constraint;

        public ConstraintExpr(ValueConstraint constraint) {
            this.constraint = Objects.requireNonNull(constraint);
        }

        public ValueConstraint constraint() {
            return constraint;
        }

        @Override
        public @NonNull String toString() {
            return constraint.repr();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConstraintExpr that)) return false;
            return Objects.equals(constraint, that.constraint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(constraint);
        }
    }

    record UnionExpr(List<TypeExpr> members) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return members.stream().map(Object::toString).collect(Collectors.joining(" | "));
        }
    }

    record IntersectionExpr(List<TypeExpr> members) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return members.stream().map(Object::toString).collect(Collectors.joining(" & "));
        }
    }

    record NegationExpr(TypeExpr inner) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return "~" + inner;
        }
    }

    record RefExpr(String name) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return name;
        }
    }

    record StringExpr(String value) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
    }

    record IntExpr(BigDecimal value) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return value.toString();
        }
    }

    record FloatExpr(Double value) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return value.toString();
        }
    }

    record SymbolExpr(String symbol) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return "'" + symbol.replace("'", "''") + "'";
        }
    }

    record SpreadExpr() implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return "...";
        }
    }

    record NullExpr() implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return "Null"; // Or whatever is standard
        }
    }

    record PathExpr(TypeExpr target, String segment) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return target + ":" + segment;
        }
    }

    sealed interface ParamExpr extends TypeExpr {
        TypeExpr type();

        record Positional(TypeExpr type, Integer index, boolean variadic) implements ParamExpr {
            @Override
            public @NonNull String toString() {
                return type + (variadic ? "..." : "");
            }
        }

        record Named(TypeExpr type, String name, boolean optional) implements ParamExpr {
            @Override
            public @NonNull String toString() {
                return (name.contains(" ") || name.isEmpty()
                                ? "'" + name.replace("'", "''") + "'"
                                : name)
                        + (optional ? "?: " : ": ")
                        + type;
            }
        }

        final class Constrained implements ParamExpr {
            private final TypeExpr type;
            private final ValueConstraint constraint;
            private final boolean optional;

            public Constrained(TypeExpr type, ValueConstraint constraint, boolean optional) {
                this.type = Objects.requireNonNull(type);
                this.constraint = Objects.requireNonNull(constraint);
                this.optional = optional;
            }

            @Override
            public TypeExpr type() {
                return type;
            }

            public ValueConstraint constraint() {
                return constraint;
            }

            public boolean optional() {
                return optional;
            }

            @Override
            public @NonNull String toString() {
                return "[" + constraint.repr() + "]" + (optional ? "?: " : ": ") + type;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Constrained that)) return false;
                return optional == that.optional && Objects.equals(type, that.type) && Objects.equals(constraint, that.constraint);
            }

            @Override
            public int hashCode() {
                return Objects.hash(type, constraint, optional);
            }
        }

        record Spread(TypeExpr type) implements ParamExpr {
            @Override
            public @NonNull String toString() {
                return "...";
            }
        }
    }

    record ApplyExpr(TypeExpr target, List<ParamExpr> arguments) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return target + "<"
                    + arguments.stream().map(Object::toString).collect(Collectors.joining(", ")) + ">";
        }
    }

    record VarExpr(String name, TypeExpr upperBound) {
        @Override
        public @NonNull String toString() {
            return name
                    + (upperBound instanceof RefExpr(String refName) && refName.equals("top") ? "" : ": " + upperBound);
        }
    }

    record SignatureExpr(List<VarExpr> typeVars, List<TypeExpr> typeParams, TypeExpr returnType) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            StringBuilder sb = new StringBuilder();
            if (!typeVars.isEmpty()) {
                sb.append("<")
                        .append(typeVars.stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(", ")))
                        .append(">");
            }
            sb.append("(");
            sb.append(typeParams.stream().map(Object::toString).collect(Collectors.joining(", ")));
            sb.append(") -> ");
            sb.append(returnType);
            return sb.toString();
        }
    }
}
