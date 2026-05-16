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
            
            Type valueType;
            if (types.size() > 1) {
                valueType = buildArrayOf(types);
            } else {
                valueType = types.get(0);
            }
            
            finalPlacements.add(buildNestedObjectFromPath(path, valueType));
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
        if (def instanceof GenericTypeDef g && g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
            List<TypeDefParam> params = new ArrayList<>();
            for (TypeDefParam tp : g.parameters()) {
                if (tp instanceof TypeDefParam.Spread) continue;
                if (tp instanceof TypeDefParam.Named n) {
                    params.add(new TypeDefParam.Named(typeSystem.unwrap(close(typeSystem.wrap(n.type()))), n.name(), n.optional()));
                } else if (tp instanceof TypeDefParam.Constrained c) {
                    params.add(new TypeDefParam.Constrained(typeSystem.unwrap(close(typeSystem.wrap(c.type()))), c.keyword(), c.value(), c.optional()));
                }
            }
            return typeSystem.wrap(new GenericTypeDef(g.template(), g.pattern(), params));
        } else if (def instanceof UnionType u) {
            return typeSystem.union(u.members().stream().map(m -> close(typeSystem.wrap(m))).toArray(Type[]::new));
        }
        return type;
    }

    private Type buildArrayOf(List<Type> types) {
        Type union = typeSystem.union(types.toArray(new Type[0]));
        return typeSystem.template("Array").construct(List.of(new TypeParameter.Spread(union)));
    }

    public boolean verify(Type inputSchema, JsonNode spec, Type expectedSchema) {
        Type inferred = infer(inputSchema, spec);
        return typeSystem.isAssignableTo(inferred, expectedSchema);
    }

    private void traverse(Type currentType, JsonNode specNode, List<String[]> matchedGroups, List<Type> typeStack, Map<String, List<Type>> pathPlacements) {
        if (specNode.isTextual()) {
            String specValue = specNode.asText();
            String[] targetPaths = specValue.split(",");
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
                traverse(currentType, specNode.get("@"), matchedGroups, typeStack, pathPlacements);
            }

            Map<String, JsonNode> explicit = new LinkedHashMap<>();
            List<Map.Entry<String, JsonNode>> wildcards = new ArrayList<>();
            List<Map.Entry<String, JsonNode>> special = new ArrayList<>();
            List<Map.Entry<String, JsonNode>> transposes = new ArrayList<>();
            List<Map.Entry<String, JsonNode>> globs = new ArrayList<>();

            for (Map.Entry<String, JsonNode> entry : specNode.properties()) {
                String k = entry.getKey();
                if (k.equals("@")) continue;
                if (k.startsWith("@") && !k.contains("*") && !k.contains("|") && !k.contains("&") && !k.contains("$")) {
                    transposes.add(entry);
                } else if (k.equals("*")) {
                    wildcards.add(entry);
                } else if (k.startsWith("$")) {
                    special.add(entry);
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
                processMatch(currentType, entry.getValue(), valStr, new String[]{valStr}, matchedGroups, typeStack, pathPlacements, false);
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
                
                if (!matchedAny && isAtom(currentType)) {
                     String[] groups = new String[p.matcher("").groupCount() + 1];
                     Arrays.fill(groups, "match"); 
                     groups[0] = glob;
                     processMatch(currentType, entry.getValue(), glob, groups, matchedGroups, typeStack, pathPlacements, false);
                }
            }

            for (Map.Entry<String, JsonNode> entry : wildcards) {
                boolean matchedAny = false;
                for (String key : allInputKeys) {
                    if (!matchedInThisObject.contains(key)) {
                        Type childType = term(currentType, key);
                        if (childType != null && !childType.isBottom()) {
                            matchedAny = true;
                            processMatch(childType, entry.getValue(), key, new String[]{key}, matchedGroups, typeStack, pathPlacements, true);
                        }
                    }
                }
                if (!matchedAny && isAtom(currentType)) {
                     processMatch(currentType, entry.getValue(), "*", new String[]{"*"}, matchedGroups, typeStack, pathPlacements, false);
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
        if (repr.matches("\\d+")) return repr;
        return "{{ " + repr + " }}";
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
            current = term(current, unescape(seg));
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
        return s.replace("\\", "");
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
                    int close = path.indexOf(')', end);
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
                    current = typeSystem.template("Array").construct(List.of(new TypeParameter.Spread(current)));
                }
                
                if (sub.startsWith("[") && sub.endsWith("]")) {
                    current = typeSystem.template("Array").construct(List.of(new TypeParameter.Spread(current)));
                } else if (sub.startsWith("{{") && sub.endsWith("}}")) {
                    current = typeSystem.template("Object").construct(List.of(
                        new TypeParameter.Constrained(current, "matches", ".*", false),
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
                for (TypeDefParam tp : g.parameters()) {
                    if (tp instanceof TypeDefParam.Named n) {
                        keys.add(n.name());
                    }
                }
            } else if (g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.ARRAY) {
                keys.add("0");
            }
        }
    }
}
