package dev.akre.covenant.types;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.List;
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
            return "(" + members.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ")) + ")";
        }
    }

    final class ConstraintExpr implements TypeExpr {
        private final String keyword;
        private final String value;
        private volatile dk.brics.automaton.Automaton automaton;

        public ConstraintExpr(String keyword, String value) {
            this.keyword = keyword;
            this.value = value;
        }

        public String keyword() {
            return keyword;
        }

        public String value() {
            return value;
        }

        public dk.brics.automaton.Automaton automaton() {
            if (automaton == null) {
                synchronized (this) {
                    if (automaton == null) {
                        automaton = TypeSystemUtils.toAutomaton(value);
                    }
                }
            }
            return automaton;
        }

        @Override
        public @NonNull String toString() {
            return keyword + " " + value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConstraintExpr that)) return false;
            return java.util.Objects.equals(keyword, that.keyword) && java.util.Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(keyword, value);
        }
    }

    record UnionExpr(List<TypeExpr> members) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return members.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" | "));
        }
    }

    record IntersectionExpr(List<TypeExpr> members) implements TypeExpr {
        @Override
        public @NonNull String toString() {
            return members.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" & "));
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
            private final String keyword;
            private final String value;
            private final boolean optional;
            private volatile dk.brics.automaton.Automaton automaton;

            public Constrained(TypeExpr type, String keyword, String value, boolean optional) {
                this.type = java.util.Objects.requireNonNull(type);
                this.keyword = keyword;
                this.value = value;
                this.optional = optional;
            }

            @Override
            public TypeExpr type() {
                return type;
            }

            public String keyword() {
                return keyword;
            }

            public String value() {
                return value;
            }

            public boolean optional() {
                return optional;
            }

            public dk.brics.automaton.Automaton automaton() {
                if (automaton == null) {
                    synchronized (this) {
                        if (automaton == null) {
                            automaton = TypeSystemUtils.toAutomaton(value);
                        }
                    }
                }
                return automaton;
            }

            @Override
            public @NonNull String toString() {
                return "[" + keyword + " " + value + "]" + (optional ? "?: " : ": ") + type;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Constrained that)) return false;
                return optional == that.optional && java.util.Objects.equals(type, that.type) && java.util.Objects.equals(keyword, that.keyword) && java.util.Objects.equals(value, that.value);
            }

            @Override
            public int hashCode() {
                return java.util.Objects.hash(type, keyword, value, optional);
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
                    + arguments.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ")) + ">";
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
                                .collect(java.util.stream.Collectors.joining(", ")))
                        .append(">");
            }
            sb.append("(");
            sb.append(typeParams.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ")));
            sb.append(") -> ");
            sb.append(returnType);
            return sb.toString();
        }
    }
}
