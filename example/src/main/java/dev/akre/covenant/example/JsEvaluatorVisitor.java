package dev.akre.covenant.example;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.OwnedTypeDef;
import dev.akre.covenant.types.StringConstraint;
import dev.akre.covenant.types.NumberConstraint;
import dev.akre.covenant.types.BooleanConstraint;
import dev.akre.covenant.types.ValueConstraint.Operator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsEvaluatorVisitor extends JSBaseVisitor<OwnedTypeDef> {
    private final AbstractTypeSystem system;
    private Environment currentEnv;
    private final List<OwnedTypeDef> reachableReturns = new ArrayList<>();

    public JsEvaluatorVisitor(AbstractTypeSystem system, Environment rootEnv) {
        this.system = system;
        this.currentEnv = rootEnv;
    }

    public OwnedTypeDef getFinalType() {
        if (reachableReturns.isEmpty()) {
            return (OwnedTypeDef) system.type("Null"); // Fallback
        }
        OwnedTypeDef result = reachableReturns.get(0);
        for (int i = 1; i < reachableReturns.size(); i++) {
            result = result.union(reachableReturns.get(i));
        }
        return result;
    }

    @Override
    public OwnedTypeDef visitProgram(JSParser.ProgramContext ctx) {
        visit(ctx.arrowFunction());
        return null;
    }

    @Override
    public OwnedTypeDef visitArrowFunction(JSParser.ArrowFunctionContext ctx) {
        // Assume parameters are pre-bound in the root environment.
        visit(ctx.block());
        return null;
    }

    @Override
    public OwnedTypeDef visitBlock(JSParser.BlockContext ctx) {
        Environment prevEnv = currentEnv;
        currentEnv = new Environment(prevEnv);

        for (JSParser.StatementContext stmt : ctx.statement()) {
            visit(stmt);
        }

        Map<String, Environment.Var> phiMerged = new HashMap<>(currentEnv.variables);
        currentEnv = prevEnv;
        for (Map.Entry<String, Environment.Var> entry : phiMerged.entrySet()) {
            if (currentEnv.variables.containsKey(entry.getKey())) {
                currentEnv.variables.put(entry.getKey(), entry.getValue());
            }
        }

        return null;
    }

    @Override
    public OwnedTypeDef visitAssignment(JSParser.AssignmentContext ctx) {
        String varName = ctx.identifier().getText();
        OwnedTypeDef type = visit(ctx.expression());
        currentEnv.shadow(varName, type);
        return type;
    }

    @Override
    public OwnedTypeDef visitVariableDeclaration(JSParser.VariableDeclarationContext ctx) {
        String name = ctx.identifier().getText();
        OwnedTypeDef type = visit(ctx.expression());
        currentEnv.declare(name, type);
        return type;
    }

    @Override
    public OwnedTypeDef visitIfStatement(JSParser.IfStatementContext ctx) {
        OwnedTypeDef conditionType = visit(ctx.expression());

        Environment preEnv = currentEnv.cloneEnv();

        currentEnv = new Environment(preEnv);

        narrowEnvironment(ctx.expression(), true);

        visit(ctx.statement(0)); // Then block
        Environment thenEnv = currentEnv;

        Environment elseEnv = null;
        if (ctx.statement().size() > 1) {
            currentEnv = new Environment(preEnv);
            narrowEnvironment(ctx.expression(), false);
            visit(ctx.statement(1)); // Else block
            elseEnv = currentEnv;
        }

        currentEnv = preEnv;
        Environment altEnv = elseEnv != null ? elseEnv : preEnv;

        for (String key : thenEnv.variables.keySet()) {
            if (currentEnv.variables.containsKey(key)) {
                Environment.Var thenVar = thenEnv.variables.get(key);
                Environment.Var altVar = altEnv.variables.get(key);

                if (thenVar.version > currentEnv.variables.get(key).version ||
                    altVar.version > currentEnv.variables.get(key).version) {

                    OwnedTypeDef mergedType = thenVar.type.union(altVar.type);
                    currentEnv.shadow(key, mergedType);
                }
            }
        }

        return null;
    }

    private void narrowEnvironment(JSParser.ExpressionContext exprCtx, boolean isTruthy) {
        if (exprCtx.getChildCount() == 3 && (exprCtx.getChild(1).getText().equals("===") || exprCtx.getChild(1).getText().equals("!=="))) {
            String op = exprCtx.getChild(1).getText();
            boolean isEq = op.equals("===");
            boolean checkTrue = isTruthy ? isEq : !isEq;

            JSParser.ExpressionContext leftCtx = (JSParser.ExpressionContext) exprCtx.getChild(0);
            JSParser.ExpressionContext rightCtx = (JSParser.ExpressionContext) exprCtx.getChild(2);

            if (leftCtx.getChildCount() == 3 && leftCtx.getChild(1).getText().equals(".")) {
                String objName = leftCtx.getChild(0).getText();
                String propName = leftCtx.getChild(2).getText();

                OwnedTypeDef rightType = visit(rightCtx);

                if (currentEnv.has(objName)) {
                    OwnedTypeDef objType = currentEnv.get(objName);

                    OwnedTypeDef propConstraint = system.wrap(system.constructDef("Object", List.of(system.unwrap(rightType)), List.of(new dev.akre.covenant.api.Parameter.Named(propName, 0, false), new dev.akre.covenant.api.Parameter.Spread())));

                    OwnedTypeDef narrowedObjType;
                    if (checkTrue) {
                        narrowedObjType = objType.intersect(propConstraint);
                    } else {
                        narrowedObjType = objType.intersect(propConstraint.negate());
                    }

                    currentEnv.shadow(objName, narrowedObjType);
                }
            }
        }
    }

    @Override
    public OwnedTypeDef visitReturnStatement(JSParser.ReturnStatementContext ctx) {
        if (ctx.expression() != null) {
            OwnedTypeDef retType = visit(ctx.expression());
            reachableReturns.add(retType);
            return retType;
        }
        return null;
    }

    @Override
    public OwnedTypeDef visitExpressionStatement(JSParser.ExpressionStatementContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public OwnedTypeDef visitExpression(JSParser.ExpressionContext ctx) {
        if (ctx.getChildCount() == 1) {
            return visit(ctx.getChild(0)); // literal or identifier
        }

        if (ctx.getChildCount() == 3 && ctx.getChild(0).getText().equals("(")) {
            return visit(ctx.getChild(1)); // ( expression )
        }

        if (ctx.getChildCount() == 3 && ctx.getChild(1).getText().equals(".")) {
            // identifier . identifier
            OwnedTypeDef objType = visit(ctx.getChild(0));
            String propName = ctx.getChild(2).getText();

            dev.akre.covenant.types.TypeDef rawObj = system.unwrap(objType);
            if (rawObj instanceof dev.akre.covenant.types.GenericTypeDef gen) {
                for (dev.akre.covenant.types.TypeDefParam param : gen.parameters()) {
                    if (param.parameter() instanceof dev.akre.covenant.api.Parameter.Named named && named.name().equals(propName)) {
                        return system.wrap(param.type());
                    }
                }
                if (gen.spreadParam() != null) {
                    return system.wrap(gen.spreadParam());
                }
            }
            // In a more robust implementation, we would recursively check parents or unions of GenericTypeDef.
            // For schemas parsed as unions, we distribute the extraction.
            if (rawObj instanceof dev.akre.covenant.types.UnionType union) {
                List<OwnedTypeDef> extracted = new ArrayList<>();
                for (dev.akre.covenant.types.TypeDef member : union.members()) {
                    if (member instanceof dev.akre.covenant.types.GenericTypeDef gen) {
                        for (dev.akre.covenant.types.TypeDefParam param : gen.parameters()) {
                            if (param.parameter() instanceof dev.akre.covenant.api.Parameter.Named named && named.name().equals(propName)) {
                                extracted.add(system.wrap(param.type()));
                            }
                        }
                    }
                }
                if (!extracted.isEmpty()) {
                    OwnedTypeDef res = extracted.get(0);
                    for (int i = 1; i < extracted.size(); i++) {
                        res = res.union(extracted.get(i));
                    }
                    return res;
                }
            }
            return (OwnedTypeDef) system.type("Any");
        }

        if (ctx.getChildCount() == 3) {
            String op = ctx.getChild(1).getText();
            OwnedTypeDef left = visit(ctx.getChild(0));
            OwnedTypeDef right = visit(ctx.getChild(2));

            String funcName = switch (op) {
                case "+" -> "plus";
                case "-" -> "minus";
                case "*" -> "times";
                case "/" -> "divide";
                case "===" -> "strictEq";
                case "!==" -> "strictEq"; // we will negate it
                case ">" -> "gt";
                case ">=" -> "gte";
                case "<" -> "lt";
                case "<=" -> "lte";
                case "&&" -> "and";
                case "||" -> "or";
                default -> null;
            };

            if (funcName != null) {
                OwnedTypeDef res = system.evaluate(funcName, left, right);
                if (op.equals("!==")) {
                    res = system.evaluate("not", res);
                }
                return res;
            }
        }

        if (ctx.getChildCount() == 4 && ctx.getChild(1).getText().equals("(")) {
            // Function call identifier(args)
            String funcName = ctx.getChild(0).getText();
            JSParser.ArgumentListContext argsCtx = ctx.argumentList();

            List<dev.akre.covenant.api.Type> args = new ArrayList<>();
            if (argsCtx != null) {
                for (JSParser.ExpressionContext argExpr : argsCtx.expression()) {
                    args.add(visit(argExpr));
                }
            }

            // Check if function is available in current environment
            if (currentEnv.has(funcName)) {
                OwnedTypeDef funcType = currentEnv.get(funcName);
                if (funcType.def() instanceof dev.akre.covenant.types.ApplicableDef applicable) {
                    return system.wrap(applicable.evaluate(system, args.stream().map(t -> system.unwrap(t)).toList()));
                }
            }

            // Fallback to TypeSystem built-in functions
            try {
                return system.evaluate(funcName, args.toArray(new dev.akre.covenant.api.Type[0]));
            } catch (Exception e) {
                return (OwnedTypeDef) system.type("Any");
            }
        }

        return system.wrap(system.bottomDef());
    }

    @Override
    public OwnedTypeDef visitIdentifier(JSParser.IdentifierContext ctx) {
        return currentEnv.get(ctx.getText());
    }

    @Override
    public OwnedTypeDef visitLiteral(JSParser.LiteralContext ctx) {
        if (ctx.StringLiteral() != null) {
            String text = ctx.StringLiteral().getText();
            String val = text.substring(1, text.length() - 1); // remove quotes
            return system.wrap(system.intersectDef(system.unwrap(system.type("String")), new StringConstraint(Operator.EQ, val)));
        } else if (ctx.NumberLiteral() != null) {
            BigDecimal val = new BigDecimal(ctx.NumberLiteral().getText());
            return system.wrap(system.intersectDef(system.unwrap(system.type("Number")), new NumberConstraint(Operator.EQ, val)));
        } else if (ctx.BooleanLiteral() != null) {
            boolean val = Boolean.parseBoolean(ctx.BooleanLiteral().getText());
            return system.wrap(system.intersectDef(system.unwrap(system.type("Bool")), new BooleanConstraint(Operator.EQ, val)));
        } else if (ctx.NullLiteral() != null) {
            return (OwnedTypeDef) system.type("Null");
        }
        return system.wrap(system.bottomDef());
    }

    // Helper Environment Class for SSA & Scoping
    public static class Environment {
        static class Var {
            String name;
            int version;
            OwnedTypeDef type;

            Var(String name, int version, OwnedTypeDef type) {
                this.name = name;
                this.version = version;
                this.type = type;
            }
        }

        private final Environment parent;
        private final Map<String, Var> variables = new HashMap<>();

        public Environment(Environment parent) {
            this.parent = parent;
        }

        public void declare(String name, OwnedTypeDef type) {
            variables.put(name, new Var(name, 0, type));
        }

        public void shadow(String name, OwnedTypeDef type) {
            Var existing = getVar(name);
            int newVersion = existing != null ? existing.version + 1 : 0;
            variables.put(name, new Var(name, newVersion, type));
        }

        public boolean has(String name) {
            return getVar(name) != null;
        }

        public OwnedTypeDef get(String name) {
            Var v = getVar(name);
            if (v == null) {
                throw new RuntimeException("Undefined variable: " + name);
            }
            return v.type;
        }

        private Var getVar(String name) {
            if (variables.containsKey(name)) {
                return variables.get(name);
            }
            if (parent != null) {
                return parent.getVar(name);
            }
            return null;
        }

        public Environment cloneEnv() {
            Environment clone = new Environment(this.parent);
            for (Map.Entry<String, Var> entry : this.variables.entrySet()) {
                Var v = entry.getValue();
                clone.variables.put(entry.getKey(), new Var(v.name, v.version, v.type));
            }
            return clone;
        }
    }
}
