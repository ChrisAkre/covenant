package dev.akre.covenant.jsonpath;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

public class JsonPathCovenantChecker {
    private final AbstractTypeSystem typeSystem;
    private final boolean wrapDefinitePathsInNodelist;

    public JsonPathCovenantChecker(boolean wrapDefinitePathsInNodelist) {
        this.typeSystem = JsonPathTypeSystem.INSTANCE;
        this.wrapDefinitePathsInNodelist = wrapDefinitePathsInNodelist;
    }

    public Type infer(Type inputSchema, String jsonPathExpr) {
        JsonPathLexer lexer = new JsonPathLexer(CharStreams.fromString(jsonPathExpr));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JsonPathParser parser = new JsonPathParser(tokens);

        JsonPathParser.JsonpathContext tree = parser.jsonpath();

        JsonPathEvaluatorVisitor visitor = new JsonPathEvaluatorVisitor(typeSystem, inputSchema);
        Type inferredType = visitor.visit(tree);

        // Wrapping rules for Nodelists based on definite/indefinite paths
        if (isIndefinitePath(tree) || wrapDefinitePathsInNodelist) {
             // Just wrap in a Nodelist. Even if it's already an array, Nodelist is distinct.
             if (!inferredType.isAssignableFrom(typeSystem.expression("Nodelist"))) {
                 inferredType = typeSystem.expression("Nodelist<" + inferredType.repr() + ">");
             }
        }

        return inferredType;
    }

    public boolean verify(Type inputSchema, String jsonPathExpr, Type expectedSchema) {
        Type inferredSchema = infer(inputSchema, jsonPathExpr);
        return expectedSchema.isAssignableFrom(inferredSchema);
    }

    private boolean isIndefinitePath(JsonPathParser.JsonpathContext tree) {
        return checkIndefinite(tree);
    }

    private boolean checkIndefinite(ParseTree tree) {
        if (tree instanceof JsonPathParser.Descendant_segmentContext ||
            tree instanceof JsonPathParser.Wildcard_selectorContext ||
            tree instanceof JsonPathParser.Slice_selectorContext ||
            tree instanceof JsonPathParser.Filter_selectorContext) {
            return true;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (checkIndefinite(tree.getChild(i))) {
                return true;
            }
        }
        return false;
    }
}
