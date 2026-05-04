package dev.akre.covenant.types;

import dev.akre.covenant.api.Parameter;
import java.math.BigDecimal;
import java.util.List;

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
                TypeExpr.UnionExpr {

    record ConstraintExpr(String keyword, String value) implements TypeExpr {
        @Override
        public String toString() {
            return keyword + " " + value;
        }
    }

    record UnionExpr(List<TypeExpr> members) implements TypeExpr {
        @Override
        public String toString() {
            return members.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" | "));
        }
    }

    record IntersectionExpr(List<TypeExpr> members) implements TypeExpr {
        @Override
        public String toString() {
            return members.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" & "));
        }
    }

    record NegationExpr(TypeExpr inner) implements TypeExpr {
        @Override
        public String toString() {
            return "~" + inner;
        }
    }

    record RefExpr(String name) implements TypeExpr {
        @Override
        public String toString() {
            return name;
        }
    }

    record StringExpr(String value) implements TypeExpr {
        @Override
        public String toString() {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
    }

    record IntExpr(BigDecimal value) implements TypeExpr {
        @Override
        public String toString() {
            return value.toString();
        }
    }

    record FloatExpr(Double value) implements TypeExpr {
        @Override
        public String toString() {
            return value.toString();
        }
    }

    record SymbolExpr(String symbol) implements TypeExpr {
        @Override
        public String toString() {
            return "'" + symbol.replace("'", "''") + "'";
        }
    }

    record SpreadExpr() implements TypeExpr {
        @Override
        public String toString() {
            return "...";
        }
    }

    record NullExpr() implements TypeExpr {
        @Override
        public String toString() {
            return "Null"; // Or whatever is standard
        }
    }

    record PathExpr(TypeExpr target, String segment) implements TypeExpr {
        @Override
        public String toString() {
            return target + ":" + segment;
        }
    }

    record ParamExpr(TypeExpr type, Parameter parameter) implements TypeExpr {
        @Override
        public String toString() {
            return switch (parameter) {
                case Parameter.Named n ->
                    (n.name().contains(" ") || n.name().isEmpty()
                                    ? "'" + n.name().replace("'", "''") + "'"
                                    : n.name())
                            + (n.optional() ? "?: " : ": ")
                            + type;
                case Parameter.Positional p -> type + (p.variadic() ? "..." : "");
                case Parameter.Constrained c ->
                    "[" + c.keyword() + " " + c.value() + "]" + (c.optional() ? "?: " : ": ") + type;
                case Parameter.Spread __ -> "...";
            };
        }
    }

    record ApplyExpr(TypeExpr target, List<ParamExpr> arguments) implements TypeExpr {
        @Override
        public String toString() {
            return target + "<"
                    + arguments.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ")) + ">";
        }
    }

    record VarExpr(String name, TypeExpr upperBound) {
        @Override
        public String toString() {
            return name
                    + (upperBound instanceof RefExpr(String refName) && refName.equals("top") ? "" : ": " + upperBound);
        }
    }

    record SignatureExpr(List<VarExpr> typeVars, List<TypeExpr> typeParams, TypeExpr returnType) implements TypeExpr {
        @Override
        public String toString() {
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
