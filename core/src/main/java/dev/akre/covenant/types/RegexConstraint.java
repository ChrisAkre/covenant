package dev.akre.covenant.types;

import dev.akre.covenant.types.parser.Parser;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public record RegexConstraint(Operator operator, String value) implements ValueConstraint {

    private static final Map<String, dk.brics.automaton.Automaton> automatonCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public String keywordString() {
        return operator.symbol;
    }

    @Override
    public String valueString() {
        return value;
    }

    public dk.brics.automaton.Automaton automaton() {
        return automatonCache.computeIfAbsent(value, TypeSystemUtils::toAutomaton);
    }

    public static Parser<TypeExpr> parser() {
        return input -> {
            if (input.head().type() == Parser.TokenType.IDENTIFIER) {
                Operator op = Operator.fromSymbol(input.head().value());
                if (op == Operator.MATCHES || op == Operator.NOT_MATCHES) {
                    Parser.InputState tail = input.tail();
                    if (tail.head().type() == Parser.TokenType.STRING_LITERAL || tail.head().type() == Parser.TokenType.REGEX_LITERAL) {
                        String val = tail.head().value();
                        if (tail.head().type() == Parser.TokenType.STRING_LITERAL) {
                            val = stripQuotes(val, "\"");
                        } else if (tail.head().type() == Parser.TokenType.REGEX_LITERAL) {
                            val = stripQuotes(val, "/");
                        }
                        return new Parser.Success<>(new TypeExpr.ConstraintExpr(new RegexConstraint(op, val)), tail.tail());
                    }
                }
            }
            return new Parser.Failure<>("Not a regex constraint", input);
        };
    }

    private static String stripQuotes(String s, String quote) {
        if (s.startsWith(quote) && s.endsWith(quote)) {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace(quote + quote, quote);
        }
        return s;
    }

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef def) {
        if (def instanceof SymbolType s) {
            boolean ok = TypeSystemUtils.matches(this, s.value());
            if (operator == Operator.NOT_MATCHES) {
                // TypeSystemUtils.matches(RegexConstraint) only returns true for MATCHES op and match.
                // We need more precise logic here.
                boolean matches = TypeSystemUtils.toAutomaton(value).run(s.value());
                ok = !matches;
            }
            return ok ? Set.of(s) : Set.of();
        }
        if (def instanceof StringConstraint s) {
            boolean ok = TypeSystemUtils.matches(this, s.value());
            if (operator == Operator.NOT_MATCHES) {
                boolean matches = TypeSystemUtils.toAutomaton(value).run(s.value());
                ok = !matches;
            }
            return ok ? Set.of(s) : Set.of();
        }
        if (!(def instanceof RegexConstraint other)) return null;
        if (this.equals(other)) return Set.of(this);

        if (this.satisfiesOther(system, other)) return Set.of(this);

        if (this.operator.isDisjoint(other.operator, this.value, other.value)) {
            return Set.of();
        }
        return null;
    }

    @Override
    public Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef def) {
        if (!(def instanceof RegexConstraint other)) return null;
        if (this.equals(other)) return Set.of(this);

        if (this.satisfiesOther(system, other)) return Set.of(other);

        return null;
    }

    @Override
    public Collection<TypeDef> invert(AbstractTypeSystem system) {
        Operator invOp = (operator == Operator.MATCHES) ? Operator.NOT_MATCHES : Operator.MATCHES;
        return Set.of(new RegexConstraint(invOp, value));
    }

    @Override
    public boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
        if (!(other instanceof RegexConstraint(Operator otherOperator, String otherValue))) {
            return system.find("String")
                    .map(base -> system.satisfies(((OwnedTypeDef) base).def(), other))
                    .orElse(false);
        }
        return this.operator.satisfies(otherOperator, this.value, otherValue);
    }

    @Override
    public String repr() {
        String quoted = "/" + value.replace("/", "//") + "/";
        if (operator == Operator.MATCHES) {
            return "matches " + quoted;
        }
        return operator.symbol + " " + quoted;
    }

    @Override
    public String toString() {
        return repr();
    }
}
