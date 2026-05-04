package dev.akre.covenant.jsonpath;

import dev.akre.covenant.api.Parameter;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.GenericTypeDef;
import dev.akre.covenant.types.OwnedTypeDef;
import dev.akre.covenant.types.TypeDef;
import dev.akre.covenant.types.TypeDefParam;
import dev.akre.covenant.types.UnionType;

import java.util.ArrayList;
import java.util.List;

public class JsonPathEvaluatorVisitor extends JsonPathBaseVisitor<Type> {
    private final AbstractTypeSystem typeSystem;
    private final Type rootContext;
    private Type currentContext;

    public JsonPathEvaluatorVisitor(AbstractTypeSystem typeSystem, Type rootContext) {
        this.typeSystem = typeSystem;
        this.rootContext = rootContext;
        this.currentContext = rootContext;
    }

    @Override
    public Type visitJsonpath(JsonPathParser.JsonpathContext ctx) {
        Type result = visit(ctx.root_identifier());
        for (JsonPathParser.SegmentContext segment : ctx.segment()) {
            result = visit(segment);
            this.currentContext = result;
        }
        return result;
    }

    @Override
    public Type visitRoot_identifier(JsonPathParser.Root_identifierContext ctx) {
        this.currentContext = rootContext;
        return rootContext;
    }

    @Override
    public Type visitCurrent_node_identifier(JsonPathParser.Current_node_identifierContext ctx) {
        return this.currentContext;
    }

    @Override
    public Type visitDot_specifier(JsonPathParser.Dot_specifierContext ctx) {
        if (ctx.name_selector() != null) {
            String propName = extractString(ctx.name_selector());
            return extractProperty(currentContext, propName);
        } else if (ctx.wildcard_selector() != null) {
            return extractAllProperties(currentContext);
        }
        return typeSystem.bottom();
    }

    @Override
    public Type visitBracket_specifier(JsonPathParser.Bracket_specifierContext ctx) {
        List<Type> memberTypes = new ArrayList<>();
        for (JsonPathParser.SelectorContext selector : ctx.selector()) {
            Type res = visit(selector);
            if (res != null) {
                memberTypes.add(res);
            }
        }
        if (memberTypes.isEmpty()) return typeSystem.bottom();
        Type unionType = memberTypes.get(0);
        for (int i = 1; i < memberTypes.size(); i++) {
            unionType = unionType.union(memberTypes.get(i));
        }
        return unionType;
    }

    @Override
    public Type visitName_selector(JsonPathParser.Name_selectorContext ctx) {
        String propName = extractString(ctx);
        return extractProperty(currentContext, propName);
    }

    @Override
    public Type visitWildcard_selector(JsonPathParser.Wildcard_selectorContext ctx) {
        return extractAllProperties(currentContext);
    }

    @Override
    public Type visitIndex_selector(JsonPathParser.Index_selectorContext ctx) {
        return extractArrayMemberType(currentContext);
    }

    @Override
    public Type visitSlice_selector(JsonPathParser.Slice_selectorContext ctx) {
        return extractArrayMemberType(currentContext);
    }

    @Override
    public Type visitDescendant_segment(JsonPathParser.Descendant_segmentContext ctx) {
        Type target = typeSystem.bottom();
        if (ctx.name_selector() != null) {
            String propName = extractString(ctx.name_selector());
            target = recursiveExtractProperty(currentContext, propName);
        } else if (ctx.wildcard_selector() != null) {
            target = recursiveExtractAllProperties(currentContext);
        } else if (ctx.bracket_specifier() != null) {
             Type allDescendants = recursiveExtractAllProperties(currentContext);
             Type originalContext = currentContext;
             currentContext = allDescendants;
             target = visit(ctx.bracket_specifier());
             currentContext = originalContext;
        }
        return target;
    }

    @Override
    public Type visitFilter_selector(JsonPathParser.Filter_selectorContext ctx) {
        Type memberType = extractArrayMemberType(currentContext);

        Type originalContext = currentContext;
        currentContext = memberType;
        Type constraint = visit(ctx.logical_expr());
        currentContext = originalContext;

        return memberType.intersect(constraint);
    }

    @Override
    public Type visitLogical_expr(JsonPathParser.Logical_exprContext ctx) {
        if (ctx.comparison_expr() != null) {
            return visit(ctx.comparison_expr());
        }
        return typeSystem.top();
    }

    @Override
    public Type visitComparison_expr(JsonPathParser.Comparison_exprContext ctx) {
        String op = ctx.cmp_op().getText();
        Type leftConstraint = evaluateComparableToConstraint(ctx.comparable(0), ctx.comparable(1), op);
        Type rightConstraint = evaluateComparableToConstraint(ctx.comparable(1), ctx.comparable(0), getInverseOp(op));
        return leftConstraint.intersect(rightConstraint);
    }

    private Type evaluateComparableToConstraint(JsonPathParser.ComparableContext self, JsonPathParser.ComparableContext other, String op) {
        if (self.singular_query() != null) {
            List<String> pathSegments = extractSingularQueryPathSegments(self.singular_query());

            // Build nested Object constraints if the path has multiple segments
            // e.g. @.address.zipcode -> Object<address: Object<zipcode: Number, ...>, ...>
            Type constraint = buildNestedConstraint(pathSegments, other, op);
            return constraint;
        }
        return typeSystem.top();
    }

    private Type buildNestedConstraint(List<String> pathSegments, JsonPathParser.ComparableContext other, String op) {
        if (pathSegments.isEmpty()) return typeSystem.top();

        Type otherType = evaluateComparableToType(other);

        String propName = pathSegments.get(pathSegments.size() - 1);
        Type currentConstraint;

        if (op.equals("==")) {
             currentConstraint = typeSystem.expression("Object<" + propName + ": " + otherType.repr() + ", ...>");
        } else if (op.equals("!=")) {
             // For != we use intersection with negated bounds.
             currentConstraint = typeSystem.expression("Object<" + propName + ": ~" + otherType.repr() + ", ...>");
        } else {
             // Extract mathematical operation constraint. `lt 10`, etc.
             String mappedOp = switch(op) {
                 case "<" -> "lt";
                 case "<=" -> "lte";
                 case ">" -> "gt";
                 case ">=" -> "gte";
                 default -> throw new IllegalStateException();
             };

             // If we are comparing against a specific numeric value, inline it into the bound constraint
             if (other.literal() != null && other.literal().NUMBER() != null) {
                 String val = other.literal().NUMBER().getText();
                 currentConstraint = typeSystem.expression("Object<" + propName + ": Number & " + mappedOp + " " + val + ", ...>");
             } else {
                 currentConstraint = typeSystem.expression("Object<" + propName + ": Number, ...>");
             }
        }

        for (int i = pathSegments.size() - 2; i >= 0; i--) {
            currentConstraint = typeSystem.expression("Object<" + pathSegments.get(i) + ": " + currentConstraint.repr() + ", ...>");
        }

        return currentConstraint;
    }

    private Type evaluateComparableToType(JsonPathParser.ComparableContext ctx) {
        if (ctx.literal() != null) {
            if (ctx.literal().NUMBER() != null) return typeSystem.expression("Number");
            if (ctx.literal().STRING_LITERAL() != null) {
                String val = ctx.literal().STRING_LITERAL().getText();
                val = val.substring(1, val.length() - 1);
                return typeSystem.expression("String").intersect(typeSystem.expression("'" + val + "'"));
            }
            if (ctx.literal().getText().equals("true") || ctx.literal().getText().equals("false")) return typeSystem.expression("Bool");
            if (ctx.literal().getText().equals("null")) return typeSystem.expression("Null");
        }
        return typeSystem.top();
    }

    private List<String> extractSingularQueryPathSegments(JsonPathParser.Singular_queryContext ctx) {
        List<String> segments = new ArrayList<>();
        for (JsonPathParser.Singular_segmentContext seg : ctx.singular_segment()) {
             if (seg.name_selector() != null) segments.add(extractString(seg.name_selector()));
        }
        return segments;
    }

    private String getInverseOp(String op) {
        switch(op) {
            case "<": return ">=";
            case "<=": return ">";
            case ">": return "<=";
            case ">=": return "<";
            case "==": return "==";
            case "!=": return "!=";
            default: return op;
        }
    }

    private String extractString(JsonPathParser.Name_selectorContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            return text.substring(1, text.length() - 1);
        }
        return ctx.IDENTIFIER().getText();
    }

    private Type extractProperty(Type objType, String propertyName) {
        TypeDef rawObj = ((OwnedTypeDef) objType).def();

        if (rawObj instanceof GenericTypeDef gen) {
            for (TypeDefParam param : gen.parameters()) {
                if (param.parameter() instanceof Parameter.Named named && named.name().equals(propertyName)) {
                    return typeSystem.wrap(param.type());
                }
            }
            if (gen.spreadParam() != null) {
                return typeSystem.wrap(gen.spreadParam());
            }
        } else if (rawObj instanceof UnionType union) {
            List<Type> extracted = new ArrayList<>();
            for (TypeDef member : union.members()) {
                Type extractedFromMember = extractProperty(typeSystem.wrap(member), propertyName);
                if (!extractedFromMember.equals(typeSystem.bottom())) {
                    extracted.add(extractedFromMember);
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
        return typeSystem.bottom();
    }

    private Type extractArrayMemberType(Type arrayType) {
        if (typeSystem.expression("Array").isAssignableFrom(arrayType)) {
            TypeDef rawArray = ((OwnedTypeDef) arrayType).def();
            if (rawArray instanceof GenericTypeDef gen) {
                for (TypeDefParam param : gen.parameters()) {
                    if (param.parameter() instanceof Parameter.Positional pos && pos.index() == 0) {
                        return typeSystem.wrap(param.type());
                    }
                }
                if (gen.spreadParam() != null) {
                    return typeSystem.wrap(gen.spreadParam());
                }
            } else if (rawArray instanceof UnionType union) {
                List<Type> extracted = new ArrayList<>();
                for (TypeDef member : union.members()) {
                    Type extractedFromMember = extractArrayMemberType(typeSystem.wrap(member));
                    if (!extractedFromMember.equals(typeSystem.top())) {
                        extracted.add(extractedFromMember);
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
        }
        return typeSystem.top();
    }

    private Type extractAllProperties(Type objType) {
        TypeDef rawObj = ((OwnedTypeDef) objType).def();
        if (rawObj instanceof GenericTypeDef gen) {
            if (gen.template().name().equals("Object")) {
                Type unionType = typeSystem.bottom();
                for (TypeDefParam param : gen.parameters()) {
                    unionType = unionType.union(typeSystem.wrap(param.type()));
                }
                if (gen.spreadParam() != null) {
                    unionType = unionType.union(typeSystem.wrap(gen.spreadParam()));
                }
                return unionType;
            } else if (gen.template().name().equals("Array") || gen.template().name().equals("Nodelist")) {
                return extractArrayMemberType(objType);
            }
        } else if (rawObj instanceof UnionType union) {
            Type unionType = typeSystem.bottom();
            for (TypeDef member : union.members()) {
                unionType = unionType.union(extractAllProperties(typeSystem.wrap(member)));
            }
            return unionType;
        }
        return typeSystem.bottom();
    }

    private Type recursiveExtractProperty(Type objType, String propertyName) {
        Type result = extractProperty(objType, propertyName);

        TypeDef rawObj = ((OwnedTypeDef) objType).def();
        if (rawObj instanceof GenericTypeDef gen) {
             if (gen.template().name().equals("Object")) {
                 for (TypeDefParam param : gen.parameters()) {
                     Type childType = typeSystem.wrap(param.type());
                     result = result.union(recursiveExtractProperty(childType, propertyName));
                 }
             } else if (gen.template().name().equals("Array") || gen.template().name().equals("Nodelist")) {
                 Type childType = extractArrayMemberType(objType);
                 result = result.union(recursiveExtractProperty(childType, propertyName));
             }
        } else if (rawObj instanceof UnionType union) {
            for (TypeDef member : union.members()) {
                result = result.union(recursiveExtractProperty(typeSystem.wrap(member), propertyName));
            }
        }

        return result;
    }

    private Type recursiveExtractAllProperties(Type objType) {
        Type result = extractAllProperties(objType);

        TypeDef rawObj = ((OwnedTypeDef) objType).def();
        if (rawObj instanceof GenericTypeDef gen) {
             if (gen.template().name().equals("Object")) {
                 for (TypeDefParam param : gen.parameters()) {
                     Type childType = typeSystem.wrap(param.type());
                     result = result.union(recursiveExtractAllProperties(childType));
                 }
             } else if (gen.template().name().equals("Array") || gen.template().name().equals("Nodelist")) {
                 Type childType = extractArrayMemberType(objType);
                 result = result.union(recursiveExtractAllProperties(childType));
             }
        } else if (rawObj instanceof UnionType union) {
            for (TypeDef member : union.members()) {
                result = result.union(recursiveExtractAllProperties(typeSystem.wrap(member)));
            }
        }

        return result;
    }
}
