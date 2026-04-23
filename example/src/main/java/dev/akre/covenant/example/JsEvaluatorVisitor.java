package dev.akre.covenant.example;

import dev.akre.covenant.api.Parameter;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.GenericTypeDef;
import dev.akre.covenant.types.StringConstraint;
import dev.akre.covenant.types.NumberConstraint;
import dev.akre.covenant.types.BooleanConstraint;
import dev.akre.covenant.types.TypeDef;
import dev.akre.covenant.types.TypeDefParam;
import dev.akre.covenant.types.UnionType;
import dev.akre.covenant.types.ValueConstraint.Operator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsEvaluatorVisitor extends JSBaseVisitor<Type> {
    private final AbstractTypeSystem system;
    private Environment currentEnv;
    private final List<Type> reachableReturns = new ArrayList<>();

    public JsEvaluatorVisitor(AbstractTypeSystem system, Environment rootEnv) {
        this.system = system;
        this.currentEnv = rootEnv;
    }

    public Type getFinalType() {
        if (reachableReturns.isEmpty()) {
            return system.type("Null"); // Fallback
        }
        Type result = reachableReturns.getFirst();
        for (int i = 1; i < reachableReturns.size(); i++) {
            result = result.union(reachableReturns.get(i));
        }
        return result;
    }

    @Override
    public Type visitProgram(JSParser.ProgramContext ctx) {
        visit(ctx.arrowFunction());
        return null;
    }

    @Override
    public Type visitArrowFunction(JSParser.ArrowFunctionContext ctx) {
        // Assume parameters are pre-bound in the root environment.
        visit(ctx.block());
        return null;
    }

    @Override
    public Type visitBlock(JSParser.BlockContext ctx) {
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
    public Type visitAssignment(JSParser.AssignmentContext ctx) {
        String varName = ctx.identifier().getText();
        Type type = visit(ctx.expression());
        currentEnv.shadow(varName, type);
        return type;
    }

    @Override
    public Type visitVariableDeclaration(JSParser.VariableDeclarationContext ctx) {
        String name = ctx.identifier().getText();
        Type type = visit(ctx.expression());
        currentEnv.declare(name, type);
        return type;
    }

    @Override
    public Type visitIfStatement(JSParser.IfStatementContext ctx) {
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

                    Type mergedType = thenVar.type.union(altVar.type);
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

            JSParser.ExpressionContext leftCtx = (JSParser.ExpressionContext) exprCtx.getChild(0);
            JSParser.ExpressionContext rightCtx = (JSParser.ExpressionContext) exprCtx.getChild(2);

            Type rightType = visit(rightCtx);

            // Determine if we are intersecting with the type, or its negation
            Type targetConstraint = isTruthy == isEq ? rightType : rightType.negate();

            applyPathConstraint(leftCtx, targetConstraint);
        }
    }

    private void applyPathConstraint(JSParser.ExpressionContext pathCtx, Type constraint) {
        // Base Case 1: It's a direct identifier (e.g., `status === "active"`)
        if (pathCtx.getChildCount() == 1) {
            String varName = pathCtx.getText();
            if (currentEnv.has(varName)) {
                Type currentType = currentEnv.get(varName);
                currentEnv.shadow(varName, currentType.intersect(constraint));
            }
            return;
        }

        // Recursive Case: It's a property access (e.g., `user.profile.status`)
        if (pathCtx.getChildCount() == 3 && pathCtx.getChild(1).getText().equals(".")) {
            JSParser.ExpressionContext nextPath = (JSParser.ExpressionContext) pathCtx.getChild(0);
            String propName = pathCtx.getChild(2).getText();

            // Wrap the constraint in a new Object bound: Object<propName: constraint, ...Spread>
            Type parentConstraint = system.wrap(
                    system.constructDef("Object",
                            List.of(system.unwrap(constraint)),
                            List.of(
                                    new Parameter.Named(propName, 0, false),
                                    new Parameter.Spread()
                            )
                    )
            );

            // Recurse up the AST tree
            applyPathConstraint(nextPath, parentConstraint);
        }
    }

    @Override
    public Type visitReturnStatement(JSParser.ReturnStatementContext ctx) {
        if (ctx.expression() != null) {
            Type retType = visit(ctx.expression());
            reachableReturns.add(retType);
            return retType;
        }
        return null;
    }

    @Override
    public Type visitExpressionStatement(JSParser.ExpressionStatementContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Type visitExpression(JSParser.ExpressionContext ctx) {
        if (ctx.getChildCount() == 1) {
            return visit(ctx.getChild(0)); // literal or identifier
        }

        if (ctx.getChildCount() == 3 && ctx.getChild(0).getText().equals("(")) {
            return visit(ctx.getChild(1)); // ( expression )
        }

        if (ctx.getChildCount() == 3 && ctx.getChild(1).getText().equals(".")) {
            // identifier . identifier
            Type objType = visit(ctx.getChild(0));
            String propName = ctx.getChild(2).getText();

            TypeDef rawObj = system.unwrap(objType);
            if (rawObj instanceof GenericTypeDef gen) {
                for (TypeDefParam param : gen.parameters()) {
                    if (param.parameter() instanceof Parameter.Named named && named.name().equals(propName)) {
                        return system.wrap(param.type());
                    }
                }
                if (gen.spreadParam() != null) {
                    return system.wrap(gen.spreadParam());
                }
            }
            // In a more robust implementation, we would recursively check parents or unions of GenericTypeDef.
            // For schemas parsed as unions, we distribute the extraction.
            if (rawObj instanceof UnionType union) {
                List<Type> extracted = new ArrayList<>();
                for (TypeDef member : union.members()) {
                    if (member instanceof GenericTypeDef gen) {
                        for (TypeDefParam param : gen.parameters()) {
                            if (param.parameter() instanceof Parameter.Named named && named.name().equals(propName)) {
                                extracted.add(system.wrap(param.type()));
                            }
                        }
                    }
                }
                if (!extracted.isEmpty()) {
                    Type res = extracted.getFirst();
                    for (int i = 1; i < extracted.size(); i++) {
                        res = res.union(extracted.get(i));
                    }
                    return res;
                }
            }
            return system.type("Any");
        }

        if (ctx.getChildCount() == 3) {
            String op = ctx.getChild(1).getText();
            Type left = visit(ctx.getChild(0));
            Type right = visit(ctx.getChild(2));

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
                Type res = system.evaluate(funcName, left, right);
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

            Type[] args = argsCtx.expression().stream().map(this::visit).toArray(Type[]::new);

            // Check if function is available in current environment
            if (currentEnv.has(funcName)) {
                Type funcType = currentEnv.get(funcName);
                if (funcType instanceof Type.TypeFunction applicable) {
                    return applicable.evaluate(args);
                }
            } else {
                return system.typeFunction(funcName).evaluate(args);
            }
        }

        return system.wrap(system.bottomDef());
    }

    @Override
    public Type visitIdentifier(JSParser.IdentifierContext ctx) {
        return currentEnv.get(ctx.getText());
    }

    @Override
    public Type visitLiteral(JSParser.LiteralContext ctx) {
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
            return system.type("Null");
        }
        return system.wrap(system.bottomDef());
    }

    // Helper Environment Class for SSA & Scoping
    public static class Environment {
        static class Var {
            String name;
            int version;
            Type type;

            Var(String name, int version, Type type) {
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

        public void declare(String name, Type type) {
            variables.put(name, new Var(name, 0, type));
        }

        public void shadow(String name, Type type) {
            Var existing = getVar(name);
            int newVersion = existing != null ? existing.version + 1 : 0;
            variables.put(name, new Var(name, newVersion, type));
        }

        public boolean has(String name) {
            return getVar(name) != null;
        }

        public Type get(String name) {
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
