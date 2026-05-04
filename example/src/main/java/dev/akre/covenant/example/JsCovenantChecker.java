package dev.akre.covenant.example;

import tools.jackson.databind.JsonNode;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.JsonSchemaParser;
import dev.akre.covenant.types.OwnedTypeDef;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import tools.jackson.databind.ObjectMapper;


public class JsCovenantChecker {

    public static final AbstractTypeSystem TYPE_SYSTEM = JavaSubscriptTypeSystem.INSTANCE;

    public boolean verify(String sourceJs, String inputSchema, String outputSchema) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode inputNode = mapper.readTree(inputSchema);
        JsonNode outputNode = mapper.readTree(outputSchema);

        return verify(sourceJs, inputNode, outputNode);
    }

    public boolean verify(String sourceJs, JsonNode inputSchema, JsonNode outputSchema) {
        JsonSchemaParser schemaParser = new JsonSchemaParser(JavaSubscriptTypeSystem.INSTANCE);

        // 2. Parse Schemas
        OwnedTypeDef inputType = TYPE_SYSTEM.wrap(schemaParser.parse(inputSchema));
        OwnedTypeDef expectedOutputType = JavaSubscriptTypeSystem.INSTANCE.wrap(schemaParser.parse(outputSchema));

        return verify(sourceJs, inputType, expectedOutputType);
    }

    public boolean verify(String sourceJs, Type inputType, Type expectedOutputType) {
        // 1. Setup system and parsers

        // 3. Setup AST Parsing
        JSLexer lexer = new JSLexer(CharStreams.fromString(sourceJs));
        JSParser parser = new JSParser(new CommonTokenStream(lexer));
        JSParser.ProgramContext tree = parser.program();

        // Extract parameter name from arrow function
        if (tree.arrowFunction() == null || tree.arrowFunction().parameterList() == null || tree.arrowFunction().parameterList().getChildCount() != 1) {
            throw new IllegalArgumentException("unsupported expression type");
        }
        String paramName = tree.arrowFunction().parameterList().identifier(0).getText();

        // 4. Setup Root Environment
        JsEvaluatorVisitor.Environment rootEnv = new JsEvaluatorVisitor.Environment(null);
        rootEnv.declare(paramName, inputType);

        // 5. Evaluate
        JsEvaluatorVisitor visitor = new JsEvaluatorVisitor(TYPE_SYSTEM, rootEnv);
        visitor.visit(tree);

        Type finalType = visitor.getFinalType();

        // 6. Final Verification
        return TYPE_SYSTEM.isAssignableTo(finalType, expectedOutputType);
    }


}
