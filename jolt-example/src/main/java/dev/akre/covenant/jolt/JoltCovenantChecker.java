package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeParameter;
import dev.akre.covenant.api.TypeAttribute;
import dev.akre.covenant.types.*;
import tools.jackson.databind.JsonNode;
import com.bazaarvoice.jolt.common.PathElementBuilder;
import com.bazaarvoice.jolt.common.pathelement.*;
import com.bazaarvoice.jolt.common.tree.*;

import java.util.*;
import java.util.stream.*;

public class JoltCovenantChecker {

    private final AbstractTypeSystem typeSystem;

    public JoltCovenantChecker(AbstractTypeSystem typeSystem) {
        this.typeSystem = typeSystem;
    }

    public static class EvaluationFrame {
        private final Type typeContext;
        private final String[] matchGroups;
        private final boolean isMulti;

        public EvaluationFrame(Type typeContext, String[] matchGroups) {
            this(typeContext, matchGroups, false);
        }

        public EvaluationFrame(Type typeContext, String[] matchGroups, boolean isMulti) {
            this.typeContext = typeContext;
            this.matchGroups = matchGroups;
            this.isMulti = isMulti;
        }

        public Type getTypeContext() { return typeContext; }
        public String[] getMatchGroups() { return matchGroups; }
        public boolean isMulti() { return isMulti; }
    }

    private class LenientMatchedElement extends MatchedElement {
        private final List<String> keys;
        LenientMatchedElement(String key, List<String> subKeys) {
            super(key, subKeys);
            List<String> k = new ArrayList<>();
            k.add(key);
            if (subKeys != null) k.addAll(subKeys);
            this.keys = Collections.unmodifiableList(k);
        }
        @Override public String getSubKeyRef(int index) {
            String res = (index < 0 || index >= keys.size()) ? "" : keys.get(index);
            return res;
        }
        @Override public int getSubKeyCount() { return keys.size(); }
    }

    private WalkedPath createWalkedPath(List<EvaluationFrame> frameStack) {
        return createWalkedPath(frameStack, -1, null);
    }

    private WalkedPath createWalkedPath(List<EvaluationFrame> frameStack, int overrideFrameIndex, String[] overrideGroups) {
        WalkedPath walkedPath = new WalkedPath();
        for (int i = 0; i < frameStack.size(); i++) {
            EvaluationFrame frame = frameStack.get(i);
            String[] groups = (i == overrideFrameIndex) ? overrideGroups : frame.getMatchGroups();
            List<String> subKeys = new ArrayList<>();
            for (int j = 1; j < groups.length; j++) {
                subKeys.add(groups[j]);
            }
            MatchedElement matchedElement = new LenientMatchedElement(groups[0], subKeys);
            walkedPath.add(wrapType(frame.getTypeContext()), matchedElement);
        }
        return walkedPath;
    }

    private Object wrapType(Type type) {
        TypeDef def = typeSystem.unwrap(type);
        if (def.attributes().contains(TypeAttribute.ARRAY)) {
            return new TypeList(type, typeSystem, this);
        }
        return new TypeMap(type, typeSystem, this);
    }

    public static class TypeMap extends AbstractMap<String, Object> {
        final Type type;
        final AbstractTypeSystem typeSystem;
        final JoltCovenantChecker checker;

        public TypeMap(Type type, AbstractTypeSystem typeSystem, JoltCovenantChecker checker) {
            this.type = type;
            this.typeSystem = typeSystem;
            this.checker = checker;
        }

        @Override public Object get(Object key) {
            Type t = checker.term(type, (String) key);
            if (t == null || t.isBottom()) return null;
            TypeDef def = typeSystem.unwrap(t);
            if (!def.attributes().contains(TypeAttribute.OBJECT) && !def.attributes().contains(TypeAttribute.ARRAY)) {
                return checker.getRepresentativeValue(t);
            }
            return checker.wrapType(t);
        }

        @Override public boolean containsKey(Object key) {
            Type t = checker.term(type, (String) key);
            boolean res = t != null && !t.isBottom();
            return res;
        }

        @Override public Set<Entry<String, Object>> entrySet() { return Collections.emptySet(); }
    }

    public static class TypeList extends AbstractList<Object> {
        final Type type;
        final AbstractTypeSystem typeSystem;
        final JoltCovenantChecker checker;

        public TypeList(Type type, AbstractTypeSystem typeSystem, JoltCovenantChecker checker) {
            this.type = type;
            this.typeSystem = typeSystem;
            this.checker = checker;
        }

        @Override public Object get(int index) {
            Type t = checker.term(type, String.valueOf(index));
            if (t == null || t.isBottom()) return null;
            TypeDef def = typeSystem.unwrap(t);
            if (!def.attributes().contains(TypeAttribute.OBJECT) && !def.attributes().contains(TypeAttribute.ARRAY)) {
                return checker.getRepresentativeValue(t);
            }
            return checker.wrapType(t);
        }

        @Override public int size() {
            return 1000;
        }
    }

    public Type infer(Type inputSchema, JsonNode spec) {
        Map<String, List<Type>> pathPlacements = new LinkedHashMap<>();
        List<EvaluationFrame> frameStack = new ArrayList<>();
        frameStack.add(new EvaluationFrame(inputSchema, new String[]{"root"}));
        
        traverse(inputSchema, spec, frameStack, pathPlacements);
        

        if (pathPlacements.isEmpty()) {
            return typeSystem.nil();
        }

        List<Type> finalPlacements = new ArrayList<>();
        for (Map.Entry<String, List<Type>> entry : pathPlacements.entrySet()) {
            String path = entry.getKey();
            List<Type> types = entry.getValue();
            
            Type combinedType = typeSystem.union(types.toArray(new Type[0]));
            if (types.size() > 1 && !path.contains("[]") && !path.contains(".[]") && isStaticPath(path) && !path.contains("[#")) {
                path = path.isEmpty() ? "[]" : path + ".[]";
            }
            
            finalPlacements.add(buildNestedObjectFromPath(path, combinedType));
        }
        
        try {
            if (finalPlacements.isEmpty()) {
                return typeSystem.nil();
            }
            return typeSystem.intersect(finalPlacements.toArray(new Type[0]));
        } catch (Exception e) {
            return typeSystem.nil();
        }
    }

    private Type close(Type type) {
        TypeDef def = typeSystem.unwrap(type);
        try {
            if (def instanceof GenericTypeDef g && g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
                List<TypeParameter> params = new ArrayList<>();
                for (TypeDefParam tp : g.parameters()) {
                    if (tp instanceof TypeDefParam.Spread) continue;
                    if (tp instanceof TypeDefParam.Named n) {
                        params.add(new TypeParameter.Named(close(typeSystem.wrap(n.type())), n.name(), n.optional()));
                    } else if (tp instanceof TypeDefParam.Constrained c) {
                        params.add(new TypeParameter.Constrained(close(typeSystem.wrap(c.type())), c.constraint().keywordString(), c.constraint().valueString(), c.optional()));
                    }
                }
                return typeSystem.template(g.template().name()).construct(params);
            } else if (def instanceof UnionType u) {
                return typeSystem.union(u.members().stream().map(m -> close(typeSystem.wrap(m))).toArray(Type[]::new));
            }
        } catch (Exception e) { /* ignoring exception to fallback to parentType */ }
        return type;
    }

    private Type buildArrayOf(List<Type> types) {
        Type union = typeSystem.union(types.toArray(new Type[0]));
        return typeSystem.template("Array").construct(List.of(new TypeParameter.Positional(union, 0, true)));
    }

    public boolean verify(Type inputSchema, JsonNode spec, Type expectedSchema) {
        Type inferred = infer(inputSchema, spec);
        return typeSystem.isAssignableTo(inferred, expectedSchema);
    }

    private void traverse(Type currentType, JsonNode specNode, List<EvaluationFrame> frameStack, Map<String, List<Type>> pathPlacements) {
        if (specNode.isTextual()) {
            processLeafProjection(currentType, specNode.asText(), frameStack, pathPlacements);
        } else if (specNode.isArray()) {
            for (JsonNode element : specNode) {
                traverse(currentType, element, frameStack, pathPlacements);
            }
        } else if (specNode.isObject()) {
            for (Map.Entry<String, JsonNode> field : specNode.properties()) {
                String rawSpecKey = field.getKey();
                JsonNode subSpec = field.getValue();

                String[] splitKeys = rawSpecKey.split("\\|");
                for (String specKey : splitKeys) {
                    MatchablePathElement pathElement = PathElementBuilder.buildMatchablePathElement(specKey);
                    
                    if (pathElement instanceof TransposePathElement transpose) {
                        processTransposeMatch(currentType, transpose, subSpec, frameStack, pathPlacements);
                    } else if (pathElement instanceof AtPathElement at) {
                        processAtMatch(currentType, at, subSpec, frameStack, pathPlacements);
                    } else if (pathElement instanceof LiteralPathElement literal) {
                        processLiteralMatch(currentType, literal, subSpec, frameStack, pathPlacements);
                    } else if (pathElement instanceof StarPathElement star) {
                        processStarMatch(currentType, star, subSpec, frameStack, pathPlacements);
                    } else if (pathElement instanceof AmpPathElement amp) {
                        processAmpMatch(currentType, amp, subSpec, frameStack, pathPlacements);
                    } else if (pathElement instanceof DollarPathElement dollar) {
                        processDollarMatch(currentType, dollar, subSpec, frameStack, pathPlacements);
                    } else if (pathElement instanceof HashPathElement hash) {
                        processHashMatch(currentType, hash, subSpec, frameStack, pathPlacements);
                    }
                }
            }
        }
    }

    private boolean isStaticPath(String path) {
        return !path.contains("{{") && !path.contains("*");
    }

    private void processLeafProjection(Type matchedType, String rhsString, List<EvaluationFrame> frameStack, Map<String, List<Type>> pathPlacements) {
        List<String> rhsParts = safeSplit(rhsString, ',');
        for (String rhs : rhsParts) {
            String trimmedRhs = rhs.trim();
            List<PathElement> outputPaths = PathElementBuilder.parseDotNotationRHS(trimmedRhs);
            
            // Dual evaluation for convergence check
            String path0 = evaluateRhs(outputPaths, frameStack, -1);
            if (path0 == null) continue;

            boolean convergent = true;
            for (int i = 0; i < frameStack.size(); i++) {
                if (frameStack.get(i).isMulti()) {
                    String path1 = evaluateRhs(outputPaths, frameStack, i);
                    if (path1 != null && !path1.equals(path0)) {
                        convergent = false;
                        break;
                    }
                }
            }

            String finalPath = path0;
            if (convergent) {
                // If inside any loop, and it is convergent, it accumulates into a list
                boolean insideLoop = false;
                for (EvaluationFrame frame : frameStack) {
                    if (frame.isMulti()) {
                        insideLoop = true;
                        break;
                    }
                }
                // Only wrap if it's a static path. Pattern paths from loops usually mean branching to different keys.
                if (insideLoop && !finalPath.endsWith("[]") && !finalPath.contains(".[]") && isStaticPath(finalPath)) {
                    finalPath = finalPath.isEmpty() ? "[]" : finalPath + ".[]";
                }
            }

            pathPlacements.computeIfAbsent(finalPath, k -> new ArrayList<>()).add(matchedType);
        }
    }

    private String evaluateRhs(List<PathElement> outputPaths, List<EvaluationFrame> frameStack, int loopFrameIndexToOverride) {
        WalkedPath walkedPath;
        if (loopFrameIndexToOverride >= 0) {
            String[] currentGroups = frameStack.get(loopFrameIndexToOverride).getMatchGroups();
            String[] nextGroups = currentGroups.clone();
            if (nextGroups.length > 0) {
                try {
                    int idx = Integer.parseInt(nextGroups[0]);
                    nextGroups[0] = String.valueOf(idx + 1);
                } catch (NumberFormatException e) {
                    nextGroups[0] = nextGroups[0] + "_next";
                }
            }
            walkedPath = createWalkedPath(frameStack, loopFrameIndexToOverride, nextGroups);
        } else {
            walkedPath = createWalkedPath(frameStack);
        }

        StringBuilder evaluatedPathBuilder = new StringBuilder();
        for (PathElement expression : outputPaths) {
            String segment = null;
            try {
                if (expression instanceof EvaluatablePathElement evaluatable) {
                    segment = evaluatable.evaluate(walkedPath);
                    if (segment == null && expression instanceof ArrayPathElement) {
                        segment = "*";
                    }
                    if (segment != null) {
                        if (expression instanceof ArrayPathElement) {
                            if (expression.getRawKey().contains("#")) segment = "[]";
                            else if (!segment.startsWith("[")) segment = "[" + segment + "]";
                        } else {
                            segment = segment.replace("\\", "\\\\").replace(".", "\\.");
                        }
                    }
                } else {
                    String raw = expression.getRawKey();
                    if (expression instanceof ArrayPathElement) {
                        if (raw.contains("#")) segment = "[]";
                        else if (!raw.startsWith("[")) segment = "[" + raw + "]";
                        else segment = raw;
                    } else {
                        segment = raw.replace("\\", "\\\\").replace(".", "\\.");
                    }
                }
            } catch (Exception e) {
                segment = null;
            }

            if (segment == null) return null;
            
            if (evaluatedPathBuilder.length() > 0) evaluatedPathBuilder.append(".");
            evaluatedPathBuilder.append(segment);
        }
        return evaluatedPathBuilder.toString();
    }


    private List<EvaluationFrame> refineStack(List<EvaluationFrame> stack) {
        List<EvaluationFrame> refined = new ArrayList<>(stack);
        for (int i = refined.size() - 2; i >= 0; i--) {
            EvaluationFrame parentFrame = refined.get(i);
            EvaluationFrame childFrame = refined.get(i + 1);
            String key = childFrame.getMatchGroups()[0];

            Type childType = childFrame.getTypeContext();
            Type parentType = parentFrame.getTypeContext();

            Type refinedParentType = parentType;

            try {
                if (key.equals("*")) {
                     TypeDef parentDef = typeSystem.unwrap(parentType);
                     if (parentDef.attributes().contains(dev.akre.covenant.api.TypeAttribute.ARRAY)) {
                          Type constraint = typeSystem.template("Array").construct(List.of(
                               new dev.akre.covenant.api.TypeParameter.Positional(childType, 0, true)
                          ));
                          refinedParentType = typeSystem.intersect(parentType, constraint);
                     }
                } else if (key.matches("\\d+")) {
                     TypeDef parentDef = typeSystem.unwrap(parentType);
                     if (parentDef.attributes().contains(dev.akre.covenant.api.TypeAttribute.ARRAY)) {
                          Type constraint = typeSystem.template("Array").construct(List.of(
                               new dev.akre.covenant.api.TypeParameter.Positional(childType, Integer.parseInt(key), true),
                               new dev.akre.covenant.api.TypeParameter.Spread(typeSystem.top())
                          ));
                          refinedParentType = typeSystem.intersect(parentType, constraint);
                     } else {
                          Type constraint = typeSystem.template("Object").construct(List.of(
                              new dev.akre.covenant.api.TypeParameter.Named(childType, key, false),
                              new dev.akre.covenant.api.TypeParameter.Spread(typeSystem.top())
                          ));
                          refinedParentType = typeSystem.intersect(parentType, constraint);
                     }
                } else {
                     Type constraint = typeSystem.template("Object").construct(List.of(
                         new dev.akre.covenant.api.TypeParameter.Named(childType, key, false),
                         new dev.akre.covenant.api.TypeParameter.Spread(typeSystem.top())
                     ));
                     refinedParentType = typeSystem.intersect(parentType, constraint);
                }
            } catch (Exception e) { /* ignoring exception to fallback to parentType */ }

            if (refinedParentType == null || refinedParentType.isBottom()) {
                 refinedParentType = parentType;
            }

            refined.set(i, new EvaluationFrame(refinedParentType, parentFrame.getMatchGroups(), parentFrame.isMulti()));
        }
        return refined;
    }
    private void processLiteralMatch(Type currentType, LiteralPathElement literal, JsonNode subSpec, List<EvaluationFrame> frameStack, Map<String, List<Type>> pathPlacements) {
        String key = literal.getRawKey();
        Type childType = narrowNonNull(term(currentType, key));
        if (childType != null && !childType.isBottom()) {
            List<EvaluationFrame> nextStack = new ArrayList<>(frameStack);
            nextStack.add(new EvaluationFrame(childType, new String[]{key}));
                nextStack = refineStack(nextStack);
            traverse(childType, subSpec, nextStack, pathPlacements);
        }
    }

    private void processStarMatch(Type currentType, StarPathElement star, JsonNode subSpec, List<EvaluationFrame> frameStack, Map<String, List<Type>> pathPlacements) {
        Set<String> allInputKeys = extractKeys(currentType);
        WalkedPath walkedPath = createWalkedPath(frameStack);
        boolean matchedAny = false;
        
        for (String key : allInputKeys) {
            MatchedElement match = star.match(key, walkedPath);
            if (match != null) {
                Type childType = narrowNonNull(term(currentType, key));
                if (childType != null && !childType.isBottom()) {
                    matchedAny = true;
                    List<EvaluationFrame> nextStack = new ArrayList<>(frameStack);
                    nextStack.add(new EvaluationFrame(childType, getGroups(match), true));
                nextStack = refineStack(nextStack);
                    traverse(childType, subSpec, nextStack, pathPlacements);
                }
            }
        }
        
        if (!matchedAny) {
             TypeDef def = typeSystem.unwrap(currentType);
             if (def.attributes().contains(TypeAttribute.ARRAY)) {
                  Type valueType = typeSystem.wrap(TypeSystemUtils.valueTypeOf(typeSystem, def));
                  if (!valueType.isBottom()) {
                       MatchedElement match = star.match("0", walkedPath);
                       if (match != null) {
                            List<EvaluationFrame> nextStack = new ArrayList<>(frameStack);
                            nextStack.add(new EvaluationFrame(valueType, getGroups(match), true));
                nextStack = refineStack(nextStack);
                            traverse(valueType, subSpec, nextStack, pathPlacements);
                       }
                  }
             } else if (TypeSystemUtils.isPrimitive(def)) {
                  String valStr = getRepresentativeValue(currentType);
                  MatchedElement match = star.match(valStr, walkedPath);
                  if (match == null) {
                      match = star.match(star.getRawKey(), walkedPath);
                  }
                  if (match != null) {
                      List<EvaluationFrame> nextStack = new ArrayList<>(frameStack);
                      nextStack.add(new EvaluationFrame(currentType, getGroups(match), true));
                      traverse(currentType, subSpec, nextStack, pathPlacements);
                  }
             }
        }
    }

    private void processAmpMatch(Type currentType, AmpPathElement amp, JsonNode subSpec, List<EvaluationFrame> frameStack, Map<String, List<Type>> pathPlacements) {
        WalkedPath walkedPath = createWalkedPath(frameStack);
        String key = null;
        try {
            key = amp.evaluate(walkedPath);
        } catch (Exception e) { /* ignoring exception to fallback to parentType */ }
        
        if (key != null) {
            Type childType = narrowNonNull(term(currentType, key));
            if (childType != null && !childType.isBottom()) {
                List<EvaluationFrame> nextStack = new ArrayList<>(frameStack);
                nextStack.add(new EvaluationFrame(childType, new String[]{key}));
                nextStack = refineStack(nextStack);
                traverse(childType, subSpec, nextStack, pathPlacements);
            }
        }
    }

    private void processTransposeMatch(Type currentType, TransposePathElement transpose, JsonNode subSpec, List<EvaluationFrame> frameStack, Map<String, List<Type>> pathPlacements) {
        WalkedPath walkedPath = createWalkedPath(frameStack);
        com.bazaarvoice.jolt.common.Optional<Object> optional = transpose.objectEvaluate(walkedPath);
        if (optional.isPresent()) {
            Object val = optional.get();
            Type lookupValue = narrowNonNull(unwrapType(val));
            if (lookupValue != null && !lookupValue.isBottom()) {
                List<EvaluationFrame> nextStack = new ArrayList<>(frameStack);
                String matchGroup = (val instanceof String s) ? s : getRepresentativeValue(lookupValue);
                nextStack.add(new EvaluationFrame(currentType, new String[]{matchGroup}));
                nextStack = refineStack(nextStack);
                traverse(lookupValue, subSpec, nextStack, pathPlacements);
            }
        }
    }

    private void processAtMatch(Type currentType, AtPathElement at, JsonNode subSpec, List<EvaluationFrame> frameStack, Map<String, List<Type>> pathPlacements) {
        Type narrowed = narrowNonNull(currentType);
        if (narrowed != null && !narrowed.isBottom()) {
            traverse(narrowed, subSpec, frameStack, pathPlacements);
        }
    }

    private void processDollarMatch(Type currentType, DollarPathElement dollar, JsonNode subSpec, List<EvaluationFrame> frameStack, Map<String, List<Type>> pathPlacements) {
        WalkedPath walkedPath = createWalkedPath(frameStack);
        MatchedElement match = null;
        try {
            match = dollar.match("", walkedPath);
        } catch (Exception e) { /* ignoring exception to fallback to parentType */ }
        
        if (match != null) {
            Type keyType = typeSystem.type("String");
            List<EvaluationFrame> nextStack = new ArrayList<>(frameStack);
            // Dollar match should push the matched key value into the frame
            nextStack.add(new EvaluationFrame(currentType, getGroups(match)));
                nextStack = refineStack(nextStack);
            traverse(keyType, subSpec, nextStack, pathPlacements);
        }
    }

    private void processHashMatch(Type currentType, HashPathElement hash, JsonNode subSpec, List<EvaluationFrame> frameStack, Map<String, List<Type>> pathPlacements) {
        WalkedPath walkedPath = createWalkedPath(frameStack);
        MatchedElement match = null;
        try {
            match = hash.match("", walkedPath);
        } catch (Exception e) { /* ignoring exception to fallback to parentType */ }
        
        if (match != null) {
            String val = match.getRawKey();
            Type valType = typeSystem.intersect(
                typeSystem.type("String"),
                (Type) typeSystem.expression("\"" + val.replace("\"", "\"\"") + "\"")
            );
            List<EvaluationFrame> nextStack = new ArrayList<>(frameStack);
            // Hash match should push the literal value into the frame
            nextStack.add(new EvaluationFrame(currentType, new String[]{val}));
                nextStack = refineStack(nextStack);
            traverse(valType, subSpec, nextStack, pathPlacements);
        }
    }

    private String[] getGroups(MatchedElement match) {
        String[] groups = new String[match.getSubKeyCount()];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = match.getSubKeyRef(i);
        }
        return groups;
    }

    private Type unwrapType(Object obj) {
        if (obj == null) return null;
        if (obj instanceof TypeMap tm) return tm.type;
        if (obj instanceof TypeList tl) return tl.type;
        if (obj instanceof Type t) return t;
        if (obj instanceof String s) {
             if (s.startsWith("{{TYPE:") && s.endsWith("}}")) {
                 String name = s.substring(7, s.length() - 2);
                 try { return typeSystem.type(name); } catch (Exception e) { /* ignoring exception to fallback to parentType */ }
                 return typeSystem.top();
             }
             if (s.startsWith("{{REGEX:") && s.endsWith("}}")) {
                 String regex = s.substring(8, s.length() - 2);
                 return (Type) typeSystem.expression("matches \"" + regex + "\"");
             }
             if (s.matches("-?\\d+(\\.\\d+)?") || s.equals("true") || s.equals("false")) {
                  return (Type) typeSystem.expression(s);
             }
             return (Type) typeSystem.expression("\"" + s.replace("\"", "\"\"") + "\"");
        }
        if (obj instanceof Boolean b) {
            return (Type) typeSystem.expression(b.toString());
        }
        if (obj instanceof Number n) {
            return (Type) typeSystem.expression(n.toString());
        }
        return null;
    }

    private List<String> safeSplit(String s, char target) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            
            if (c == target && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private boolean isAtom(Type type) {
        TypeDef def = typeSystem.unwrap(type);
        return def instanceof AtomType;
    }

    private Type narrowNonNull(Type type) {
        if (type == null) return null;
        TypeDef def = typeSystem.unwrap(type);
        TypeDef nilDef = typeSystem.nilDef();
        if (nilDef == null) return type;

        if (def instanceof UnionType u) {
            List<TypeDef> nonNullMembers = u.members().stream()
                    .filter(m -> !m.equals(nilDef))
                    .toList();
            if (nonNullMembers.isEmpty()) return type;
            if (nonNullMembers.size() < u.members().size()) {
                return typeSystem.wrap(typeSystem.unionDef(nonNullMembers.toArray(new TypeDef[0])));
            }
        } else if (def.equals(nilDef)) {
            return type;
        }
        return type;
    }

    private String getRepresentativeValue(Type type) {
        String repr = type.repr();
        if (repr.startsWith("'") && repr.endsWith("'")) {
            return repr.substring(1, repr.length() - 1);
        }
        if (repr.matches("-?\\d+(\\.\\d+)?") || repr.equals("true") || repr.equals("false")) return repr;

        Type narrowed = narrowNonNull(type);
        TypeDef def = typeSystem.unwrap(narrowed);
        String regex = extractRegex(def);
        if (regex != null) {
            return "{{REGEX:" + regex + "}}";
        }

        if (def instanceof NominalDef n) {
            String name = n.name();
            if (name.equals("String") || name.equals("Number") || name.equals("Bool") || name.equals("Int") || name.equals("Float") || name.equals("Null") || name.equals("Any") || name.equals("top")) {
                return "{{TYPE:" + name + "}}";
            }
        }
        
        return "{{TYPE:Any}}";
    }

    private String extractRegex(TypeDef def) {
        if (def instanceof RegexConstraint rc && rc.operator() == ValueConstraint.Operator.MATCHES) {
            return rc.value();
        }
        if (def instanceof StringConstraint sc && sc.operator() == ValueConstraint.Operator.MATCHES) {
            return sc.value();
        }
        if (def instanceof IntersectionType i) {
            for (TypeDef member : i.members()) {
                String r = extractRegex(member);
                if (r != null) return r;
            }
        }
        return null;
    }

    private Type buildNestedObjectFromPath(String path, Type leafType) {
        Type current = leafType;
        List<String> segments = splitLiteralPath(path);
        for (int i = segments.size() - 1; i >= 0; i--) {
            String seg = segments.get(i);
            if (seg.equals("[]") || (seg.startsWith("[") && seg.endsWith("]"))) {
                current = typeSystem.template("Array").construct(List.of(new TypeParameter.Positional(current, 0, true)));
            } else {
                String sub = unescape(seg);
                if (sub.equals("*") || sub.equals("{{TYPE:Any}}") || sub.equals("{{TYPE:top}}")) {
                    current = typeSystem.template("Object").construct(List.of(
                        new TypeParameter.Constrained(current, "matches", "/.*/", true),
                        new TypeParameter.Spread(typeSystem.top())
                    ));
                } else if (sub.startsWith("{{TYPE:")) {
                    String name = sub.substring(7, sub.length() - 2);
                    String pattern;
                    if (name.equals("String") || name.equals("Any") || name.equals("top")) {
                        pattern = "/.*/";
                    } else if (name.equals("Bool")) {
                        pattern = "/^(true|false)$/";
                    } else if (name.equals("Number") || name.equals("Int") || name.equals("Float")) {
                        pattern = "/^[0-9]+(\\.[0-9]+)?$/";
                    } else {
                        pattern = "'" + name.replace("'", "''") + "'";
                    }
                    current = typeSystem.template("Object").construct(List.of(
                        new TypeParameter.Constrained(current, "matches", pattern, true),
                        new TypeParameter.Spread(typeSystem.top())
                    ));
                } else if (sub.startsWith("{{REGEX:")) {
                    String regex = sub.substring(8, sub.length() - 2);
                    current = typeSystem.template("Object").construct(List.of(
                        new TypeParameter.Constrained(current, "matches", "/" + regex + "/", true),
                        new TypeParameter.Spread(typeSystem.top())
                    ));
                } else {
                    current = typeSystem.template("Object").construct(List.of(
                        new TypeParameter.Named(current, sub, false),
                        new TypeParameter.Spread(typeSystem.top())
                    ));
                }
            }
        }
        return current;
    }

    private Type term(Type type, String key) {
        if (type.isBottom()) return type;
        TypeDef subject = typeSystem.unwrap(type);

        if (key.equals("*")) {
            if (subject instanceof GenericTypeDef g) {
                List<TypeDef> types = new ArrayList<>();
                for (TypeDefParam tp : g.parameters()) {
                    if (tp instanceof TypeDefParam.Constrained) {
                        types.add(tp.type());
                    }
                }
                if (!types.isEmpty()) {
                    return typeSystem.wrap(typeSystem.unionDef(types.toArray(new TypeDef[0])));
                }
            }
            return typeSystem.wrap(TypeSystemUtils.valueTypeOf(typeSystem, subject));
        }

        if (subject instanceof NominalDef n && !n.name().equals("bottom")) {
             if (n.name().equals("top") || n.name().equals("Any") || (!key.equals("*") && !subject.attributes().contains(TypeAttribute.OBJECT) && !subject.attributes().contains(TypeAttribute.ARRAY))) {
                  return type;
             }
        }
        
        if (TypeSystemUtils.isPrimitive(subject)) {
            if (key.matches("-?\\d+(\\.\\d+)?")) {
                return typeSystem.intersect(type, typeSystem.expression("eq " + key));
            }
            if (key.equals("true") || key.equals("false")) {
                return typeSystem.intersect(type, typeSystem.expression("eq " + key));
            }
            return typeSystem.intersect(type, typeSystem.expression("eq '" + key.replace("'", "''") + "'"));
        }
        
        TypeDef segment = typeSystem.unwrap((dev.akre.covenant.api.Type) typeSystem.expression("'" + key.replace("'", "''") + "'"));
        TypeDef result = TypeSystemUtils.termAt(typeSystem, subject, segment);
        return typeSystem.wrap(result);
    }

    private Set<String> extractKeys(Type type) {
        Set<String> keys = new LinkedHashSet<>();
        TypeDef def = typeSystem.unwrap(type);
        collectKeys(def, keys);
        return keys;
    }

    private void collectKeys(TypeDef def, Set<String> keys) {
        if (def instanceof UnionType u) {
            for (TypeDef member : u.members()) {
                collectKeys(member, keys);
            }
        } else if (def instanceof GenericTypeDef g) {
            if (g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
                boolean hasWildcard = false;
                for (TypeDefParam tp : g.parameters()) {
                    if (tp instanceof TypeDefParam.Named n) {
                        keys.add(n.name());
                    } else {
                        hasWildcard = true;
                    }
                }
                if (hasWildcard) {
                    keys.add("*");
                }
            } else if (g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.ARRAY) {
                keys.add("0");
                keys.add("*");
            }
        }
    }

    private List<String> splitLiteralPath(String path) {
        if (path.isEmpty()) return List.of();
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' && !escaped) {
                escaped = true;
                current.append(c);
            } else if (c == '.' && !escaped) {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
                escaped = false;
            }
        }
        segments.add(current.toString());
        return segments;
    }

    private String unescape(String s) {
        return s.replace("\\.", ".").replace("\\\\", "\\");
    }
}
