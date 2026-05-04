package dev.akre.covenant.types;

import dev.akre.covenant.types.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicConstraintTest {

    @Test
    public void testDynamicConstraintInjection() {
        Parser<TypeExpr> customParser = input -> {
            if (input.head().value().equals("#custom")) {
                return new Parser.Success<>(new TypeExpr.SymbolExpr("#custom"), input.tail());
            }
            return new Parser.Failure<>("Not #custom");
        };

        AbstractTypeSystem system = new TypeSystemBuilderImpl(JsonTypeSystem.INSTANCE)
                .registerConstraint(customParser)
                .build();

        // Verify that the parser used by the system now recognizes '#custom'
        TypeExpr expr = system.parser().parse("#custom");
        assertInstanceOf(TypeExpr.SymbolExpr.class, expr);
        assertEquals("#custom", ((TypeExpr.SymbolExpr) expr).symbol());

    }
}
