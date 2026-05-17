package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeParameter;
import dev.akre.covenant.types.*;
import tools.jackson.databind.JsonNode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JoltCovenantChecker {

    private final AbstractTypeSystem typeSystem;

    public JoltCovenantChecker(AbstractTypeSystem typeSystem) {
        this.typeSystem = typeSystem;
    }

    public Type infer(Type inputSchema, JsonNode spec) {
        Map<String, List<Type>> pathPlacements = new LinkedHashMap<>();
        List<Type> typeStack = new ArrayList<>();
        typeStack.add(inputSchema);
        List<String[]> matchedGroups = new ArrayList<>();
        traverse(inputSchema, spec, matchedGroups, typeStack, pathPlacements);
        
        if (pathPlacements.isEmpty()) {
            return typeSystem.bottom();
        }
        
        List<Type> finalPlacements = new ArrayList<>();
        for (Map.Entry<String, List<Type>> entry : pathPlacements.entrySet()) {
            String path = entry.getKey();
            List<Type> types = entry.getValue();
            
            Type combinedType = typeSystem.union(types.toArray(new Type[0]));
            if (types.size() > 1 && !path.contains("[]") && !path.contains("{{")) {
                combinedType = buildArrayOf(Collections.singletonList(combinedType));
            }
            
            finalPlacements.add(buildNestedObjectFromPath(path, combinedType));
        }
        
        try {
            Type result = typeSystem.intersect(finalPlacements.toArray(new Type[0]));
            return close(result);
        } catch (Exception e) {
            return typeSystem.bottom();
        }
    }

    private Type close(Type type) {
        TypeDef def = typeSystem.unwrap(type);
        try {
            if (def instanceof GenericTypeDef g && g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
                List<dev.akre.covenant.api.TypeParameter> params = new ArrayList<>();
                for (TypeDefParam tp : g.parameters()) {
                    if (tp instanceof TypeDefParam.Spread) continue;
                    if (tp instanceof TypeDefParam.Named n) {
                        params.add(new dev.akre.covenant.api.TypeParameter.Named(close(typeSystem.wrap(n.type())), n.name(), n.optional()));
                    } else if (tp instanceof TypeDefParam.Constrained c) {
                        params.add(new dev.akre.covenant.api.TypeParameter.Constrained(close(typeSystem.wrap(c.type())), c.keyword(), c.value(), c.optional()));
                    }
                }
                return typeSystem.template(g.template().name()).construct(params);
            } else if (def instanceof UnionType u) {
                return typeSystem.union(u.members().stream().map(m -> close(typeSystem.wrap(m))).toArray(Type[]::new));
            }
        } catch (Exception e) {
            // Silently fail close for complex intersections
        }
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

    private void traverse(Type currentType, JsonNode specNode, List<String[]> matchedGroups, List<Type> typeStack, Map<String, List<Type>> pathPlacements) {
        if (specNode.isTextual()) {
            String specValue = specNode.asText();
            List<String> targetPaths = safeSplit(specValue, ',');
            for (String p : targetPaths) {
                String trimmed = p.trim();
                Type sourceValue = currentType;
                if (trimmed.startsWith("@") && !trimmed.contains(".") && !trimmed.contains("[") && !trimmed.contains("(")) {
                     sourceValue = lookupTranspose(trimmed, typeStack, matchedGroups);
                }
                String path = substitute(trimmed, matchedGroups, typeStack);
                pathPlacements.computeIfAbsent(path, k -> new ArrayList<>()).add(sourceValue);
            }
        } else if (specNode.isArray()) {
            for (JsonNode element : specNode) {
                traverse(currentType, element, matchedGroups, typeStack, pathPlacements);
            }
        } else if (specNode.isObject()) {
            if (specNode.has("@")) {
                traverse(currentType, specNode.get("@"), matchedGroups, typeStack, pathPlacements, false);
            }

            Map<String, JsonNode> explicit = new LinkedHashMap<>();
            List<Map.Entry<String, JsonNode>> wildcards = new ArrayList<>();
            List<Map.Entry<String, JsonNode>> special = new ArrayList<>();
            List<Map.Entry<String, JsonNode>> transposes = new ArrayList<>();
            List<Map.Entry<String, JsonNode>> globs = new ArrayList<>();
            List<Map.Entry<String, JsonNode>> literals = new ArrayList<>();

            for (Map.Entry<String, JsonNode> entry : specNode.properties()) {
                String k = entry.getKey();
                if (k.equals("@")) continue;
                if (k.startsWith("@")) {
                    transposes.add(entry);
                } else if (k.equals("*")) {
                    wildcards.add(entry);
                } else if (k.startsWith("$")) {
                    special.add(entry);
                } else if (k.startsWith("#")) {
                    literals.add(entry);
                } else if (k.contains("*") && !isEscaped(k, k.indexOf("*"))) {
                    globs.add(entry);
                } else if (k.contains("|") && !isEscaped(k, k.indexOf("|"))) {
                    String[] parts = k.split("\\|");
                    for (String part : parts) {
                        explicit.put(unescape(part), entry.getValue());
                    }
                } else {
                    explicit.put(unescape(k), entry.getValue());
                }
            }

            Set<String> matchedInThisObject = new HashSet<>();
            Set<String> allInputKeys = extractKeys(currentType);

            for (Map.Entry<String, JsonNode> entry : transposes) {
                Type lookupValue = lookupTranspose(entry.getKey(), typeStack, matchedGroups);
                String valStr = getRepresentativeValue(lookupValue);
                processMatch(lookupValue, entry.getValue(), valStr, new String[]{valStr}, matchedGroups, typeStack, pathPlacements, true);
            }

            for (Map.Entry<String, JsonNode> entry : literals) {
                String literalValue = unescape(entry.getKey().substring(1));
                Type literalType = (dev.akre.covenant.api.Type) typeSystem.expression("'" + literalValue.replace("'", "''") + "'");
                processMatch(literalType, entry.getValue(), literalValue, new String[]{literalValue}, matchedGroups, typeStack, pathPlacements, true);
            }

            for (Map.Entry<String, JsonNode> entry : explicit.entrySet()) {
                String key = entry.getKey();
                Type childType = term(currentType, key);
                if (childType != null && !childType.isBottom()) {
                    matchedInThisObject.add(key);
                    processMatch(childType, entry.getValue(), key, new String[]{key}, matchedGroups, typeStack, pathPlacements, true);
                }
            }

            for (Map.Entry<String, JsonNode> entry : globs) {
                String glob = entry.getKey();
                Pattern p = Pattern.compile("^" + glob.replace(".", "\\.").replace("*", "(.*)") + "$");
                
                boolean matchedAny = false;
                for (String inputKey : allInputKeys) {
                    if (!matchedInThisObject.contains(inputKey)) {
                        Matcher m = p.matcher(inputKey);
                        if (m.matches()) {
                            Type childType = term(currentType, inputKey);
                            if (childType != null && !childType.isBottom()) {
                                matchedAny = true;
                                matchedInThisObject.add(inputKey);
                                String[] groups = new String[m.groupCount() + 1];
                                for (int i = 0; i <= m.groupCount(); i++) groups[i] = m.group(i);
                                processMatch(childType, entry.getValue(), inputKey, groups, matchedGroups, typeStack, pathPlacements, true);
                            }
                        }
                    }
                }
                
                if (!matchedAny && TypeSystemUtils.isPrimitive(typeSystem.unwrap(currentType))) {
                     String valStr = getRepresentativeValue(currentType);
                     String[] groups = new String[p.matcher("").groupCount() + 1];
                     Arrays.fill(groups, "match"); 
                     groups[0] = valStr;
                     processMatch(currentType, entry.getValue(), valStr, groups, matchedGroups, typeStack, pathPlacements, false);
                }
            }

            for (Map.Entry<String, JsonNode> entry : wildcards) {
                boolean matchedAny = false;
                TypeDef currentDef = typeSystem.unwrap(currentType);
                if (currentDef instanceof NominalDef n && n.attributes().contains(dev.akre.covenant.api.TypeAttribute.ARRAY)) {
                    Type valueType = typeSystem.wrap(TypeSystemUtils.valueTypeOf(typeSystem, currentDef));
                    if (!valueType.isBottom()) {
                        matchedAny = true;
                        processMatch(valueType, entry.getValue(), "*", new String[]{"match"}, matchedGroups, typeStack, pathPlacements, true);
                    }
                } else {
                    for (String key : allInputKeys) {
                        if (!matchedInThisObject.contains(key)) {
                            Type childType = term(currentType, key);
                            if (childType != null && !childType.isBottom()) {
                                matchedAny = true;
                                processMatch(childType, entry.getValue(), key, new String[]{key}, matchedGroups, typeStack, pathPlacements, true);
                            }
                        }
                    }
                }
                
                if (!matchedAny && TypeSystemUtils.isPrimitive(currentDef)) {
                     String valStr = getRepresentativeValue(currentType);
                     processMatch(currentType, entry.getValue(), valStr, new String[]{valStr}, matchedGroups, typeStack, pathPlacements, false);
                }
            }

            for (Map.Entry<String, JsonNode> entry : special) {
                String k = entry.getKey();
                String val = substitute(k, matchedGroups, typeStack);
                
                List<String[]> nextGroups = new ArrayList<>(matchedGroups);
                nextGroups.add(new String[]{val});
                traverse(typeSystem.type("String"), entry.getValue(), nextGroups, typeStack, pathPlacements);
            }
        }
    }

    private void traverse(Type currentType, JsonNode specNode, List<String[]> matchedGroups, List<Type> typeStack, Map<String, List<Type>> pathPlacements, boolean pushStack) {
        List<Type> nextStack = new ArrayList<>(typeStack);
        if (pushStack) nextStack.add(currentType);
        traverse(currentType, specNode, matchedGroups, nextStack, pathPlacements);
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

    private void processMatch(Type childType, JsonNode innerSpec, String key, String[] groups, List<String[]> matchedGroups, List<Type> typeStack, Map<String, List<Type>> pathPlacements, boolean pushStack) {
        List<String[]> nextGroups = new ArrayList<>(matchedGroups);
        nextGroups.add(groups);
        List<Type> nextStack = new ArrayList<>(typeStack);
        if (pushStack) nextStack.add(childType);
        traverse(childType, innerSpec, nextGroups, nextStack, pathPlacements);
    }

    private String getRepresentativeValue(Type type) {
        String repr = type.repr();
        if (repr.startsWith("'") && repr.endsWith("'")) {
            return repr.substring(1, repr.length() - 1);
        }
        if (repr.matches("-?\\d+(\\.\\d+)?")) return repr;

        TypeDef def = typeSystem.unwrap(type);
        String regex = extractRegex(def);
        if (regex != null) {
            return "{{REGEX:" + regex + "}}";
        }

        return "{{ " + repr + " }}";
    }

    private String extractRegex(TypeDef def) {
        if (def instanceof dev.akre.covenant.types.StringConstraint(dev.akre.covenant.types.ValueConstraint.Operator op, String val) && op == dev.akre.covenant.types.ValueConstraint.Operator.MATCHES) {
            if (val.startsWith("'") && val.endsWith("'")) return val.substring(1, val.length() - 1);
            if (val.startsWith("\"") && val.endsWith("\"")) return val.substring(1, val.length() - 1);
            return val;
        }
        if (def instanceof IntersectionType i) {
            for (TypeDef member : i.members()) {
                String r = extractRegex(member);
                if (r != null) return r;
            }
        }
        if (def instanceof UnionType u) {
            for (TypeDef member : u.members()) {
                String r = extractRegex(member);
                if (r != null) return r;
            }
        }
        return null;
    }

    private Type lookupTranspose(String op, List<Type> typeStack, List<String[]> matchedGroups) {
        if (op.equals("@")) return typeStack.get(typeStack.size() - 1);
        
        String content;
        if (op.startsWith("@(")) {
            content = op.substring(2, op.length() - 1);
        } else {
            content = op.substring(1);
        }
        
        if (content.isEmpty()) return typeStack.get(typeStack.size() - 1);
        
        String[] parts = content.split(",");
        int level = 0;
        String path = "";
        
        try {
            level = Integer.parseInt(parts[0].trim());
            if (parts.length > 1) {
                path = parts[1].trim();
            }
        } catch (NumberFormatException e) {
            path = parts[0].trim();
        }
        
        int stackIndex = typeStack.size() - 1 - level;
        if (stackIndex < 0) return typeSystem.top();
        
        Type root = typeStack.get(stackIndex);
        if (path.isEmpty()) return root;
        
        path = substitute(path, matchedGroups, typeStack);
        
        Type current = root;
        String[] segments = splitPath(path);
        for (String seg : segments) {
            List<String> subParts = decomposeSegment(seg);
            for (String part : subParts) {
                if (part.startsWith("[") && part.endsWith("]")) {
                    String inner = part.substring(1, part.length() - 1);
                    if (inner.startsWith("'") && inner.endsWith("'")) {
                        inner = inner.substring(1, inner.length() - 1);
                    }
                    if (inner.equals("*") || inner.equals("match")) {
                        current = typeSystem.wrap(TypeSystemUtils.valueTypeOf(typeSystem, typeSystem.unwrap(current)));
                    } else {
                        current = term(current, unescape(inner));
                    }
                } else {
                    current = term(current, unescape(part));
                }
            }
        }
        return current;
    }

    private boolean isEscaped(String s, int index) {
        int count = 0;
        for (int i = index - 1; i >= 0 && s.charAt(i) == '\\'; i--) {
            count++;
        }
        return count % 2 != 0;
    }

    private String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && !escaped) {
                escaped = true;
            } else {
                sb.append(c);
                escaped = false;
            }
        }
        return sb.toString();
    }

    private String substitute(String path, List<String[]> matchedGroups, List<Type> typeStack) {
        if (path.startsWith("#")) return unescape(path.substring(1));
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' && i + 1 < path.length()) {
                char next = path.charAt(i + 1);
                sb.append('\\').append(next);
                i++;
            } else if (c == '&' || c == '$') {
                int start = i + 1;
                int end = start;
                int level = 0;
                int groupIndex = 0;
                
                if (end < path.length() && path.charAt(end) == '(') {
                    int close = findClosingParen(path, end);
                    if (close != -1) {
                        String content = path.substring(end + 1, close);
                        String[] parts = content.split(",");
                        try {
                            level = Integer.parseInt(parts[0].trim());
                            if (parts.length > 1) groupIndex = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException e) {}
                        i = close;
                    }
                } else {
                    while (end < path.length() && Character.isDigit(path.charAt(end))) {
                        end++;
                    }
                    if (end > start) {
                        level = Integer.parseInt(path.substring(start, end));
                        i = end - 1;
                    }
                }
                
                int index = matchedGroups.size() - 1 - level;
                if (index >= 0 && index < matchedGroups.size()) {
                    String[] groups = matchedGroups.get(index);
                    if (groupIndex >= 0 && groupIndex < groups.length) {
                        sb.append(groups[groupIndex]);
                    }
                } else if (c == '$' && level == matchedGroups.size()) {
                    sb.append("root");
                }
            } else if (c == '@') {
                int end = i + 1;
                if (end < path.length() && path.charAt(end) == '(') {
                    int close = findClosingParen(path, end);
                    if (close != -1) {
                        Type val = lookupTranspose(path.substring(i, close + 1), typeStack, matchedGroups);
                        sb.append(getRepresentativeValue(val));
                        i = close;
                    } else {
                        sb.append(c);
                    }
                } else {
                    int startOfAt = i;
                    while (end < path.length() && (Character.isLetterOrDigit(path.charAt(end)) || path.charAt(end) == '_' || path.charAt(end) == '.')) {
                        end++;
                    }
                    if (end > i + 1) {
                        Type val = lookupTranspose(path.substring(i, end), typeStack, matchedGroups);
                        sb.append(getRepresentativeValue(val));
                        i = end - 1;
                    } else {
                        sb.append(c);
                    }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private int findClosingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private Type buildNestedObjectFromPath(String path, Type leafType) {
        Type current = leafType;
        String[] segments = splitPath(path);
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i];
            if (segment.isEmpty()) continue;
            
            List<String> subSegments = decomposeSegment(segment);
            for (int k = subSegments.size() - 1; k >= 0; k--) {
                String sub = subSegments.get(k);
                boolean isArrayAppend = false;
                if (sub.endsWith("[]") || sub.contains("[#")) {
                    isArrayAppend = true;
                    if (sub.endsWith("[]")) sub = sub.substring(0, sub.length() - 2);
                    else sub = sub.substring(0, sub.indexOf('['));
                }
                
                if (isArrayAppend) {
                    current = typeSystem.template("Array").construct(List.of(new TypeParameter.Positional(current, 0, true)));
                }
                
                if (sub.startsWith("[") && sub.endsWith("]")) {
                    current = typeSystem.template("Array").construct(List.of(new TypeParameter.Positional(current, 0, true)));
                } else if (sub.startsWith("{{REGEX:") && sub.endsWith("}}")) {
                    String regex = sub.substring(8, sub.length() - 2);
                    current = typeSystem.template("Object").construct(List.of(
                        new TypeParameter.Constrained(current, "matches", "\"" + regex + "\"", true),
                        new TypeParameter.Spread(typeSystem.top())
                    ));
                } else if (sub.startsWith("{{") && sub.endsWith("}}")) {
                    current = typeSystem.template("Object").construct(List.of(
                        new TypeParameter.Constrained(current, "matches", ".*", true),
                        new TypeParameter.Spread(typeSystem.top())
                    ));
                } else if (!sub.isEmpty()) {
                    current = typeSystem.template("Object").construct(List.of(
                        new TypeParameter.Named(current, unescape(sub), false),
                        new TypeParameter.Spread(typeSystem.top())
                    ));
                }
            }
        }
        return current;
    }

    private String[] splitPath(String path) {
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
        return segments.toArray(new String[0]);
    }

    private List<String> decomposeSegment(String segment) {
        List<String> parts = new ArrayList<>();
        int lastIndex = 0;
        for (int i = 0; i < segment.length(); i++) {
            if (segment.charAt(i) == '[' && (i == 0 || segment.charAt(i-1) != '\\')) {
                if (i > lastIndex) {
                    parts.add(segment.substring(lastIndex, i));
                }
                int close = segment.indexOf(']', i);
                if (close != -1) {
                    parts.add(segment.substring(i, close + 1));
                    i = close;
                    lastIndex = i + 1;
                }
            }
        }
        if (lastIndex < segment.length()) {
            parts.add(segment.substring(lastIndex));
        }
        return parts;
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
             if (n.name().equals("top") || n.name().equals("Any") || (!key.equals("*") && !subject.attributes().contains(dev.akre.covenant.api.TypeAttribute.OBJECT) && !subject.attributes().contains(dev.akre.covenant.api.TypeAttribute.ARRAY))) {
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
            }
        }
    }
}
