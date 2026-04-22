package dev.akre.covenant.types;

import dev.akre.covenant.api.Parameter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public final class TypeExprVisitor extends TypesBaseVisitor<TypeExpr> {

    public TypeExprVisitor() {}

    public static TypeExpr parse(String expression) {
        TypesLexer lexer = new TypesLexer(CharStreams.fromString(expression));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TypesParser parser = new TypesParser(tokens);
        TypeExprVisitor visitor = new TypeExprVisitor();
        return visitor.visitTypeExpression(parser.typeExpression());
    }

    public TypeDef parseDef(AbstractTypeSystem system, String expression) {
        TypeExpr expr = parse(expression);
        return new Bindings(system).resolve(expr);
    }

    @Override
    public TypeExpr visitTypeExpression(TypesParser.TypeExpressionContext ctx) {
        return visit(ctx.typeDef());
    }

    @Override
    public TypeExpr visitUnionDef(TypesParser.UnionDefContext ctx) {
        if (ctx.intersectionDef().size() == 1) {
            return visit(ctx.intersectionDef(0));
        }
        List<TypeExpr> members = new ArrayList<>();
        for (TypesParser.IntersectionDefContext iCtx : ctx.intersectionDef()) {
            members.add(visit(iCtx));
        }
        return new TypeExpr.UnionExpr(members);
    }

    @Override
    public TypeExpr visitIntersectionDef(TypesParser.IntersectionDefContext ctx) {
        if (ctx.functionDef().size() == 1) {
            return visit(ctx.functionDef(0));
        }
        List<TypeExpr> members = new ArrayList<>();
        for (TypesParser.FunctionDefContext fCtx : ctx.functionDef()) {
            members.add(visit(fCtx));
        }
        return new TypeExpr.IntersectionExpr(members);
    }

    @Override
    public TypeExpr visitSignatureDef(TypesParser.SignatureDefContext ctx) {
        List<TypeExpr.VarExpr> typeParams = new ArrayList<>();
        if (ctx.genericParams() != null) {
            for (TypesParser.GenericParamContext gp : ctx.genericParams().genericParam()) {
                String name = gp.IDENTIFIER().getText();
                TypeExpr constraint = gp.typeDef() != null ? visit(gp.typeDef()) : new TypeExpr.RefExpr("top");
                typeParams.add(new TypeExpr.VarExpr(name, constraint));
            }
        }

        ArrayList<TypeExpr> args = new ArrayList<>();
        if (ctx.params != null) {
            for (TypesParser.TypeDefContext td : ctx.params) {
                args.add(visit(td));
            }
        }
        TypeExpr ret = visit(ctx.ret);
        return new TypeExpr.SignatureExpr(typeParams, args, ret);
    }

    @Override
    public TypeExpr visitOptionalDef(TypesParser.OptionalDefContext ctx) {
        TypeExpr inner = visit(ctx.primaryDef());
        return new TypeExpr.UnionExpr(List.of(inner, new TypeExpr.NullExpr()));
    }

    @Override
    public TypeExpr visitNegationDef(TypesParser.NegationDefContext ctx) {
        return new TypeExpr.NegationExpr(visit(ctx.primaryDef()));
    }

    @Override
    public TypeExpr visitPathDef(TypesParser.PathDefContext ctx) {
        TypeExpr inner = visit(ctx.primaryDef());
        String segment = ctx.IDENTIFIER() != null
                ? ctx.IDENTIFIER().getText()
                : ctx.INT_LITERAL().getText();
        return new TypeExpr.PathExpr(inner, segment);
    }

    @Override
    public TypeExpr visitEvaluationDef(TypesParser.EvaluationDefContext ctx) {
        TypeExpr func = visit(ctx.primaryDef());
        List<TypeExpr.ParamExpr> args = new ArrayList<>();
        if (ctx.typeDef() != null) {
            int index = 0;
            for (TypesParser.TypeDefContext td : ctx.typeDef()) {
                TypeExpr argExpr = visit(td);
                args.add(new TypeExpr.ParamExpr(argExpr, new Parameter.Positional(index++, false)));
            }
        }
        return new TypeExpr.ApplyExpr(func, args);
    }

    @Override
    public TypeExpr visitParameterizedDef(TypesParser.ParameterizedDefContext ctx) {
        TypesParser.ParameterizedTypeDefContext pCtx = ctx.parameterizedTypeDef();
        String name = pCtx.IDENTIFIER().getText();
        TypeExpr base = new TypeExpr.RefExpr(name);

        List<TypeExpr.ParamExpr> args = new ArrayList<>();
        if (pCtx.parameter() != null) {
            for (TypesParser.ParameterContext param : pCtx.parameter()) {
                args.add(visitParameter(param, args.size()));
            }
        }
        return new TypeExpr.ApplyExpr(base, args);
    }

    private TypeExpr.ParamExpr visitParameter(TypesParser.ParameterContext ctx, int index) {
        if (ctx instanceof TypesParser.NamedParamContext named) {
            TypeExpr type = visit(named.typeDef());
            boolean optional = false;
            for (int i = 0; i < named.getChildCount(); i++) {
                if (named.getChild(i).getText().equals("?")) {
                    optional = true;
                    break;
                }
            }

            Parameter param;
            if (named.IDENTIFIER() != null) {
                param = new Parameter.Named(named.IDENTIFIER().getText(), index, optional);
            } else if (named.SYMBOL_LITERAL() != null) {
                param = new Parameter.Named(stripQuotes(named.SYMBOL_LITERAL().getText()), index, optional);
            } else {
                TypesParser.ConstraintContext cCtx = named.constraint();
                String keyword = cCtx.KEYWORD().getText();
                String value = cCtx.literal() != null
                        ? stripQuotes(cCtx.literal().getText())
                        : (cCtx.IDENTIFIER() != null
                                ? cCtx.IDENTIFIER().getText()
                                : stripQuotes(cCtx.SYMBOL_LITERAL().getText()));
                param = new Parameter.Constrained(keyword, value, index, optional);
            }
            return new TypeExpr.ParamExpr(type, param);

        } else if (ctx instanceof TypesParser.PositionalParamContext positional) {
            TypeExpr type = visit(positional.typeDef());
            boolean variadic = positional.ELLIPSIS() != null;
            return new TypeExpr.ParamExpr(type, new Parameter.Positional(index, variadic));

        } else if (ctx instanceof TypesParser.SpreadParamContext) {
            return new TypeExpr.ParamExpr(new TypeExpr.SpreadExpr(), new Parameter.Spread());
        }
        throw new UnsupportedOperationException(
                "Unknown parameter type: " + ctx.getClass().getName());
    }

    @Override
    public TypeExpr visitAtomOrAliasDef(TypesParser.AtomOrAliasDefContext ctx) {
        String name = ctx.atomOrAlias().IDENTIFIER().getText().trim();
        return new TypeExpr.RefExpr(name);
    }

    @Override
    public TypeExpr visitGroupDef(TypesParser.GroupDefContext ctx) {
        return visit(ctx.typeDef());
    }

    @Override
    public TypeExpr visitLiteralDef(TypesParser.LiteralDefContext ctx) {
        TypesParser.LiteralContext lCtx = ctx.literal();
        if (lCtx.SYMBOL_LITERAL() != null) {
            String name = stripQuotes(lCtx.SYMBOL_LITERAL().getText());
            return new TypeExpr.SymbolExpr(name);
        } else if (lCtx.INT_LITERAL() != null) {
            return new TypeExpr.IntExpr(new BigDecimal(lCtx.INT_LITERAL().getText()));
        } else if (lCtx.FLOAT_LITERAL() != null) {
            return new TypeExpr.FloatExpr(Double.valueOf(lCtx.FLOAT_LITERAL().getText()));
        } else {
            return new TypeExpr.StringExpr(stripQuotes(lCtx.STRING_LITERAL().getText()));
        }
    }

    @Override
    public TypeExpr visitConstraint(TypesParser.ConstraintContext ctx) {
        String keyword = ctx.KEYWORD().getText();
        String value = ctx.literal() != null
                ? stripQuotes(ctx.literal().getText())
                : (ctx.IDENTIFIER() != null
                        ? ctx.IDENTIFIER().getText()
                        : stripQuotes(ctx.SYMBOL_LITERAL().getText()));
        return new TypeExpr.ConstraintExpr(keyword, value);
    }

    private String stripQuotes(String text) {
        if (text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length() - 1).replace("''", "'");
        }
        if (text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1).replace("\"\"", "\"");
        }
        return text;
    }
}
