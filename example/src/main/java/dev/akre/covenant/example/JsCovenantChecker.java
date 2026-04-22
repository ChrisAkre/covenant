package dev.akre.covenant.example;

import tools.jackson.databind.JsonNode;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.JsonSchemaParser;
import dev.akre.covenant.types.OwnedTypeDef;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

public class JsCovenantChecker {

    public boolean verify(String sourceJs, JsonNode inputSchema, JsonNode outputSchema) {
        // 1. Setup system and parsers
        AbstractTypeSystem system = JavaSubscriptTypeSystem.INSTANCE;
        JsonSchemaParser schemaParser = new JsonSchemaParser(system);

        // 2. Parse Schemas
        OwnedTypeDef inputType = system.wrap(schemaParser.parse(inputSchema));
        OwnedTypeDef expectedOutputType = system.wrap(schemaParser.parse(outputSchema));

        // 3. Setup AST Parsing
        JSLexer lexer = new JSLexer(CharStreams.fromString(sourceJs));
        JSParser parser = new JSParser(new CommonTokenStream(lexer));
        JSParser.ProgramContext tree = parser.program();

        // Extract parameter name from arrow function
        String paramName = "user"; // Fallback
        if (tree.arrowFunction() != null && tree.arrowFunction().parameterList() != null) {
            paramName = tree.arrowFunction().parameterList().identifier(0).getText();
        }

        // 4. Setup Root Environment
        JsEvaluatorVisitor.Environment rootEnv = new JsEvaluatorVisitor.Environment(null);
        rootEnv.declare(paramName, inputType);

        // 5. Evaluate
        JsEvaluatorVisitor visitor = new JsEvaluatorVisitor(system, rootEnv);
        visitor.visit(tree);

        OwnedTypeDef finalType = visitor.getFinalType();

        // 6. Final Verification
        return system.isAssignableTo(finalType, expectedOutputType);
    }
}
