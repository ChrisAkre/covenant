package dev.akre.covenant.types;

import dev.akre.covenant.api.Parameter;
import dev.akre.covenant.types.parser.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.SequencedCollection;
import java.util.Set;

public final class TypeParser {
    private final List<Parser<TypeExpr>> customConstraints;
    private static final Set<String> KEYWORDS = Set.of("gt", "lt", "gte", "lte", "eq", "neq", "matches", "nmatches");

    public TypeParser(List<Parser<TypeExpr>> customConstraints) {
        this.customConstraints = List.copyOf(customConstraints);
    }


    public TypeExpr parse(String expression) {
            Parser.InputState state = Lexer.tokenize(expression);
            Parser.Result<TypeExpr> result = expression(0).parse(state);
            if (result instanceof Parser.Success<TypeExpr>(TypeExpr value, Parser.InputState remaining)) {
                if (!remaining.isEndOfInput()) {
                    Parser.Token t = remaining.head();
                    throw new IllegalArgumentException("Unexpected token at position " + t.position() + ": '" + t.value() + "'");
                }
                return value;
            }
            throw new IllegalArgumentException(((Parser.Failure<TypeExpr>) result).message() + " at position " + result.remaining().head().position());
    }

    private Parser<TypeExpr> expression(int minBindingPower) {
        return input -> {
            Parser.Result<TypeExpr> leftResult = nud(input);
            if (!leftResult.matched()) {
                return leftResult;
            }

            TypeExpr left = leftResult.value();
            Parser.InputState state = leftResult.remaining();

            while (true) {
                int bp = peekBindingPower(state);
                if (bp <= minBindingPower) {
                    break;
                }

                Parser.Result<TypeExpr> ledResult = led(left, state, bp);
                if (!ledResult.matched()) {
                    break;
                }

                left = ledResult.value();
                state = ledResult.remaining();
            }

            return new Parser.Success<>(left, state);
        };
    }

    private Parser.Result<TypeExpr> nud(Parser.InputState input) {
        if (input.isEndOfInput()) {
            return new Parser.Failure<>("Unexpected end of input");
        }

        Parser.Token token = input.head();

        // Custom Constraints
        for (Parser<TypeExpr> custom : customConstraints) {
            Parser.Result<TypeExpr> res = custom.parse(input);
            if (res.matched()) {
                return res;
            }
        }

        switch (token.type()) {
            case TILDE: {
                Parser.Result<TypeExpr> inner = expression(70).parse(input.tail());
                if (inner.matched()) {
                    return new Parser.Success<>(new TypeExpr.NegationExpr(inner.value()), inner.remaining());
                }
                return inner;
            }
            case L_PAREN: {
                Parser.Result<List<TypeExpr>> inner = Parser.ofSequence(expression(0), Parser.ofToken(Parser.TokenType.COMMA)).parse(input.tail());
                if (inner.matched()) {
                    Parser.InputState next = inner.remaining();
                    if (next.head().type() == Parser.TokenType.R_PAREN) {
                        List<TypeExpr> members = inner.value();
                        TypeExpr res;
                        if (members.isEmpty()) {
                            res = new TypeExpr.TupleExpr(List.of());
                        } else if (members.size() == 1) {
                            res = members.getFirst();
                        } else {
                            res = new TypeExpr.TupleExpr(members);
                        }
                        return new Parser.Success<>(res, next.tail());
                    }
                    return new Parser.Failure<>("Expected ')'");
                }
                return new Parser.Failure<>("Failed to parse expression in group");
            }
            case L_ANGLE: {
                Parser.Result<List<TypeExpr.VarExpr>> vars = genericParams().parse(input);
                if (vars.matched()) {
                    Parser.InputState next = vars.remaining();
                    if (next.head().type() == Parser.TokenType.L_PAREN) {
                        Parser.Result<List<TypeExpr>> params = Parser.ofSequence(expression(0), Parser.ofToken(Parser.TokenType.COMMA)).parse(next.tail());
                        if (params.matched()) {
                            Parser.InputState afterParams = params.remaining();
                            if (afterParams.head().type() == Parser.TokenType.R_PAREN) {
                                Parser.InputState afterParen = afterParams.tail();
                                if (afterParen.head().type() == Parser.TokenType.ARROW) {
                                    Parser.Result<TypeExpr> ret = expression(25).parse(afterParen.tail());
                                    if (ret.matched()) {
                                        return new Parser.Success<>(new TypeExpr.SignatureExpr(vars.value(), params.value(), ret.value()), ret.remaining());
                                    }
                                }
                            }
                        }
                    }
                }
                return new Parser.Failure<>("Failed to parse generic signature");
            }
            case INT_LITERAL:
                return new Parser.Success<>(new TypeExpr.IntExpr(new BigDecimal(token.value())), input.tail());
            case FLOAT_LITERAL:
                return new Parser.Success<>(new TypeExpr.FloatExpr(Double.valueOf(token.value())), input.tail());
            case STRING_LITERAL:
                return new Parser.Success<>(new TypeExpr.StringExpr(stripQuotes(token.value(), "\"")), input.tail());
            case SYMBOL_LITERAL:
                return new Parser.Success<>(new TypeExpr.SymbolExpr(stripQuotes(token.value(), "'")), input.tail());
            case IDENTIFIER: {
                if (KEYWORDS.contains(token.value())) {
                    Parser.Result<TypeExpr> kw = keywordConstraint().parse(input);
                    if (kw.matched()) {
                        return kw;
                    }
                }

                Parser.InputState next = input.tail();
                if (next.head().type() == Parser.TokenType.L_ANGLE) {
                    Parser.Result<List<TypeExpr.ParamExpr>> params = Parser.ofSequence(parameter(), Parser.ofToken(Parser.TokenType.COMMA)).parse(next.tail());
                    if (params.matched()) {
                        Parser.InputState afterParams = params.remaining();
                        if (afterParams.head().type() == Parser.TokenType.R_ANGLE) {
                            return new Parser.Success<>(new TypeExpr.ApplyExpr(new TypeExpr.RefExpr(token.value()), fixIndices(params.value())), afterParams.tail());
                        }
                    }
                }

                return new Parser.Success<>(new TypeExpr.RefExpr(token.value()), next);
            }
            default:
                return new Parser.Failure<>("Unexpected token: " + token.type() + " (" + token.value() + ")");
        }
    }

    @SuppressWarnings("unused")
    private Parser.Result<TypeExpr> led(TypeExpr left, Parser.InputState state, int bp) {
        Parser.Token token = state.head();
        switch (token.type()) {
            case PIPE: {
                Parser.Result<TypeExpr> right = expression(bp).parse(state.tail());
                if (right.matched()) {
                    List<TypeExpr> members = new ArrayList<>();
                    if (left instanceof TypeExpr.UnionExpr(List<TypeExpr> leftMembers)) {
                        members.addAll(leftMembers);
                    } else {
                        members.add(left);
                    }
                    if (right.value() instanceof TypeExpr.UnionExpr(List<TypeExpr> rightMembers)) {
                        members.addAll(rightMembers);
                    } else {
                        members.add(right.value());
                    }
                    return new Parser.Success<>(new TypeExpr.UnionExpr(members), right.remaining());
                }
                return right;
            }
            case AMPERSAND: {
                Parser.Result<TypeExpr> right = expression(bp).parse(state.tail());
                if (right.matched()) {
                    List<TypeExpr> members = new ArrayList<>();
                    if (left instanceof TypeExpr.IntersectionExpr(List<TypeExpr> leftMembers)) {
                        members.addAll(leftMembers);
                    } else {
                        members.add(left);
                    }
                    if (right.value() instanceof TypeExpr.IntersectionExpr(List<TypeExpr> rightMembers)) {
                        members.addAll(rightMembers);
                    } else {
                        members.add(right.value());
                    }
                    return new Parser.Success<>(new TypeExpr.IntersectionExpr(members), right.remaining());
                }
                return right;
            }
            case ARROW: {
                Parser.Result<TypeExpr> ret = expression(bp).parse(state.tail());
                if (ret.matched()) {
                    List<TypeExpr> params = new ArrayList<>();
                    if (left instanceof TypeExpr.TupleExpr(List<TypeExpr> members)) {
                        params.addAll(members);
                    } else {
                        params.add(left);
                    }
                    return new Parser.Success<>(new TypeExpr.SignatureExpr(List.of(), params, ret.value()), ret.remaining());
                }
                return ret;
            }
            case QUESTION:
                return new Parser.Success<>(new TypeExpr.UnionExpr(List.of(left, new TypeExpr.NullExpr())), state.tail());
            case COLON: {
                Parser.InputState afterColon = state.tail();
                Parser.Token next = afterColon.head();
                if (next.type() == Parser.TokenType.IDENTIFIER || next.type() == Parser.TokenType.INT_LITERAL || next.type() == Parser.TokenType.SYMBOL_LITERAL || next.type() == Parser.TokenType.STRING_LITERAL) {
                    String segment = stripQuotes(stripQuotes(next.value(), "'"), "\"");
                    return new Parser.Success<>(new TypeExpr.PathExpr(left, segment), afterColon.tail());
                }
                return new Parser.Failure<>("Expected segment after ':'");
            }
            case L_PAREN: {
                Parser.Result<List<TypeExpr.ParamExpr>> argsResult = Parser.ofSequence(parameter(), Parser.ofToken(Parser.TokenType.COMMA)).parse(state.tail());
                if (argsResult.matched()) {
                    Parser.InputState afterArgs = argsResult.remaining();
                    if (afterArgs.head().type() == Parser.TokenType.R_PAREN) {
                        return new Parser.Success<>(new TypeExpr.ApplyExpr(left, fixIndices(argsResult.value())), afterArgs.tail());
                    }
                    return new Parser.Failure<>("Expected ')'");
                }
                return new Parser.Failure<>("Failed to parse arguments");
            }
            default:
                return new Parser.Failure<>("Unexpected operator: " + token.type());
        }
    }

    private boolean isFunctionSignature(Parser.InputState state) {
        if (state.isEndOfInput()) return false;

        Parser.InputState current = state;

        // Skip optional generic params
        if (current.head().type() == Parser.TokenType.L_ANGLE) {
            current = skipBalanced(current, Parser.TokenType.L_ANGLE, Parser.TokenType.R_ANGLE);
            if (current == null || current.isEndOfInput()) return false;
        }

        // Must have parameters in parens
        if (current.head().type() != Parser.TokenType.L_PAREN) {
            return false;
        }
        current = skipBalanced(current, Parser.TokenType.L_PAREN, Parser.TokenType.R_PAREN);
        if (current == null || current.isEndOfInput()) return false;

        // Must be followed by arrow
        return current.head().type() == Parser.TokenType.ARROW;
    }

    private Parser.InputState skipBalanced(Parser.InputState state, Parser.TokenType open, Parser.TokenType close) {
        if (state.isEndOfInput() || state.head().type() != open) return null;
        int depth = 0;
        while (!state.isEndOfInput()) {
            Parser.TokenType type = state.head().type();
            if (type == open) {
                depth++;
            } else if (type == close) {
                depth--;
                if (depth == 0) {
                    return state.tail();
                }
            }
            state = state.tail();
        }
        return null;
    }

    private int peekBindingPower(Parser.InputState state) {
        if (state.isEndOfInput()) {
            return -1;
        }
        Parser.Token token = state.head();
        return switch (token.type()) {
            case ARROW -> 5;
            case PIPE -> isFunctionSignature(state.tail()) ? 4 : 10;
            case AMPERSAND -> isFunctionSignature(state.tail()) ? 4 : 20;
            case QUESTION -> 50;
            case L_PAREN -> 60;
            case COLON -> 65;
            default -> -1;
        };
    }

    private Parser<TypeExpr> keywordConstraint() {
        return input -> {
            Parser.Token token = input.head();
            if (!KEYWORDS.contains(token.value())) {
                return new Parser.Failure<>("Expected keyword");
            }

            Parser.InputState next = input.tail();
            Parser.Result<TypeExpr> val = nud(next);
            if (!val.matched()) {
                return new Parser.Failure<>("Expected value after keyword");
            }

            String valStr = switch(val.value()) {
                case TypeExpr.StringExpr s -> s.value();
                case TypeExpr.IntExpr i -> i.value().toString();
                case TypeExpr.FloatExpr f -> f.value().toString();
                case TypeExpr.SymbolExpr sy -> sy.symbol();
                case TypeExpr.RefExpr r -> r.name();
                default -> val.value().toString();
            };
            return new Parser.Success<>(new TypeExpr.ConstraintExpr(token.value(), valStr), val.remaining());
        };
    }

    private Parser<TypeExpr.ParamExpr> parameter() {
        return input -> {
            if (input.head().type() == Parser.TokenType.ELLIPSIS) {
                return new Parser.Success<>(new TypeExpr.ParamExpr(new TypeExpr.SpreadExpr(), new Parameter.Spread()), input.tail());
            }

            Parser.Token t = input.head();
            if (t.type() == Parser.TokenType.IDENTIFIER || t.type() == Parser.TokenType.SYMBOL_LITERAL || t.type() == Parser.TokenType.STRING_LITERAL) {
                Parser.InputState temp = input.tail();
                boolean optional = false;
                if (temp.head().type() == Parser.TokenType.QUESTION) {
                    optional = true;
                    temp = temp.tail();
                }
                if (temp.head().type() == Parser.TokenType.COLON) {
                    Parser.Result<TypeExpr> type = expression(0).parse(temp.tail());
                    if (type.matched()) {
                        String name = stripQuotes(stripQuotes(t.value(), "'"), "\"");
                        return new Parser.Success<>(new TypeExpr.ParamExpr(type.value(), new Parameter.Named(name, 0, optional)), type.remaining());
                    }
                }
            }

            Parser.Result<TypeExpr> type = expression(0).parse(input);
            if (type.matched()) {
                Parser.InputState after = type.remaining();
                boolean variadic = false;
                if (after.head().type() == Parser.TokenType.ELLIPSIS) {
                    variadic = true;
                    after = after.tail();
                }
                return new Parser.Success<>(new TypeExpr.ParamExpr(type.value(), new Parameter.Positional(0, variadic)), after);
            }
            return new Parser.Failure<>("Failed to parse parameter");
        };
    }

    private Parser<List<TypeExpr.VarExpr>> genericParams() {
        return input -> {
            if (input.head().type() != Parser.TokenType.L_ANGLE) {
                return new Parser.Failure<>("Expected '<'");
            }
            Parser.Result<List<TypeExpr.VarExpr>> vars = Parser.ofSequence(genericParam(), Parser.ofToken(Parser.TokenType.COMMA)).parse(input.tail());
            if (vars.matched()) {
                Parser.InputState after = vars.remaining();
                if (after.head().type() == Parser.TokenType.R_ANGLE) {
                    return new Parser.Success<>(vars.value(), after.tail());
                }
                return new Parser.Failure<>("Expected '>'");
            }
            return new Parser.Failure<>("Failed to parse generic parameters");
        };
    }

    private Parser<TypeExpr.VarExpr> genericParam() {
        return input -> {
            Parser.Token t = input.head();
            if (t.type() != Parser.TokenType.IDENTIFIER) {
                return new Parser.Failure<>("Expected identifier");
            }
            Parser.InputState next = input.tail();
            TypeExpr constraint = new TypeExpr.RefExpr("top");
            if (next.head().type() == Parser.TokenType.COLON) {
                Parser.Result<TypeExpr> res = expression(0).parse(next.tail());
                if (res.matched()) {
                    constraint = res.value();
                    next = res.remaining();
                }
            }
            return new Parser.Success<>(new TypeExpr.VarExpr(t.value(), constraint), next);
        };
    }

    private List<TypeExpr.ParamExpr> fixIndices(List<TypeExpr.ParamExpr> params) {
        List<TypeExpr.ParamExpr> fixed = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            TypeExpr.ParamExpr p = params.get(i);
            if (p.parameter() instanceof Parameter.Positional pos) {
                fixed.add(new TypeExpr.ParamExpr(p.type(), new Parameter.Positional(i, pos.variadic())));
            } else if (p.parameter() instanceof Parameter.Named n) {
                fixed.add(new TypeExpr.ParamExpr(p.type(), new Parameter.Named(n.name(), i, n.optional())));
            } else {
                fixed.add(p);
            }
        }
        return fixed;
    }

    private String stripQuotes(String s, String quote) {
        if (s.startsWith(quote) && s.endsWith(quote)) {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace(quote + quote, quote);
        }
        return s;
    }

    public TypeDef parseDef(AbstractTypeSystem system, String expression) {
        TypeExpr expr = parse(expression);
        return new Bindings(system).resolve(expr);
    }

    public SequencedCollection<Parser<TypeExpr>> constraintParsers() {
        return customConstraints;
    }
}
