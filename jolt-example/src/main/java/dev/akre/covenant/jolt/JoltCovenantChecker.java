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
        traverse(inputSchema, spec, new ArrayList<>(), typeStack, pathPlacements);
        
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
        
        return typeSystem.intersect(finalPlacements.toArray(new Type[0]));
    }

    private Type buildArrayOf(List<Type> types) {
        Type union = typeSystem.union(types.toArray(new Type[0]));
        return typeSystem.template("Array").construct(List.of(new TypeParameter.Spread(union)));
    }

    public boolean verify(Type inputSchema, JsonNode spec, Type expectedSchema) {
        Type inferred = infer(inputSchema, spec);
        return typeSystem.isAssignableTo(inferred, expectedSchema);
    }

    private void traverse(Type currentType, JsonNode specNode, List<String> matchedKeys, List<Type> typeStack, Map<String, List<Type>> pathPlacements) {
        if (specNode.isTextual()) {
            String specValue = specNode.asText();
            Type sourceValue = currentType;
            
            if (specValue.startsWith("@")) {
                sourceValue = lookupTranspose(specValue, typeStack, matchedKeys);
            }
            
            String[] targetPaths = specValue.split(",");
            for (String p : targetPaths) {
                String path = substitute(p.trim(), matchedKeys, typeStack);
                pathPlacements.computeIfAbsent(path, k -> new ArrayList<>()).add(sourceValue);
            }
        } else if (specNode.isArray()) {
            for (JsonNode element : specNode) {
                traverse(currentType, element, matchedKeys, typeStack, pathPlacements);
            }
        } else if (specNode.isObject()) {
            if (specNode.has("@")) {
                traverse(currentType, specNode.get("@"), matchedKeys, typeStack, pathPlacements);
            }

            Map<String, JsonNode> explicit = new LinkedHashMap<>();
            List<Map.Entry<String, JsonNode>> wildcards = new ArrayList<>();
            List<Map.Entry<String, JsonNode>> special = new ArrayList<>();
            List<Map.Entry<String, JsonNode>> transposes = new ArrayList<>();

            for (Map.Entry<String, JsonNode> entry : specNode.properties()) {
                String k = entry.getKey();
                if (k.equals("@")) continue;
                if (k.startsWith("@")) {
                    transposes.add(entry);
                } else if (k.equals("*")) {
                    wildcards.add(entry);
                } else if (k.startsWith("$")) {
                    special.add(entry);
                } else if (k.contains("|") && !isEscaped(k, k.indexOf("|"))) {
                    String[] parts = k.split("\\|");
                    for (String part : parts) {
                        explicit.put(unescape(part), entry.getValue());
                    }
                } else {
                    explicit.put(unescape(k), entry.getValue());
                }
            }

            for (Map.Entry<String, JsonNode> entry : transposes) {
                Type lookupValue = lookupTranspose(entry.getKey(), typeStack, matchedKeys);
                List<String> nextKeys = new ArrayList<>(matchedKeys);
                String repr = lookupValue.repr();
                if (repr.startsWith("'") && repr.endsWith("'")) {
                    nextKeys.add(repr.substring(1, repr.length() - 1));
                } else {
                    nextKeys.add(repr);
                }
                traverse(currentType, entry.getValue(), nextKeys, typeStack, pathPlacements);
            }

            for (Map.Entry<String, JsonNode> entry : explicit.entrySet()) {
                String key = entry.getKey();
                Type childType = term(currentType, key);
                if (childType != null && !isBottom(childType)) {
                    List<String> nextKeys = new ArrayList<>(matchedKeys);
                    nextKeys.add(key);
                    List<Type> nextStack = new ArrayList<>(typeStack);
                    nextStack.add(childType);
                    traverse(childType, entry.getValue(), nextKeys, nextStack, pathPlacements);
                }
            }

            for (Map.Entry<String, JsonNode> entry : wildcards) {
                Set<String> allKeys = extractKeys(currentType);
                for (String key : allKeys) {
                    if (!explicit.containsKey(key)) {
                        Type childType = term(currentType, key);
                        if (childType != null && !isBottom(childType)) {
                            List<String> nextKeys = new ArrayList<>(matchedKeys);
                            nextKeys.add(key);
                            List<Type> nextStack = new ArrayList<>(typeStack);
                            nextStack.add(childType);
                            traverse(childType, entry.getValue(), nextKeys, nextStack, pathPlacements);
                        }
                    }
                }
            }

            for (Map.Entry<String, JsonNode> entry : special) {
                List<String> nextKeys = new ArrayList<>(matchedKeys);
                String currentMatchedKey = matchedKeys.isEmpty() ? "root" : matchedKeys.get(matchedKeys.size() - 1);
                nextKeys.add(currentMatchedKey);
                traverse(typeSystem.type("String"), entry.getValue(), nextKeys, typeStack, pathPlacements);
            }
        }
    }

    private Type lookupTranspose(String op, List<Type> typeStack, List<String> matchedKeys) {
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
        
        if (parts.length > 0) {
            try {
                level = Integer.parseInt(parts[0]);
                if (parts.length > 1) {
                    path = parts[1];
                }
            } catch (NumberFormatException e) {
                // It's a path, level 0 assumed
                path = parts[0];
            }
        }
        
        int stackIndex = typeStack.size() - 1 - level;
        if (stackIndex < 0) return typeSystem.top();
        
        Type root = typeStack.get(stackIndex);
        if (path.isEmpty()) return root;
        
        // Path might contain & substitutions!
        path = substitute(path, matchedKeys, typeStack);
        
        Type current = root;
        String[] segments = path.split("(?<!\\\\)\\.");
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

    private String substitute(String path, List<String> matchedKeys, List<Type> typeStack) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' && i + 1 < path.length()) {
                sb.append(path.charAt(i + 1));
                i++;
            } else if (c == '&') {
                int start = i + 1;
                int end = start;
                while (end < path.length() && Character.isDigit(path.charAt(end))) {
                    end++;
                }
                int level = 0;
                if (end > start) {
                    level = Integer.parseInt(path.substring(start, end));
                    i = end - 1;
                }
                int index = matchedKeys.size() - 1 - level;
                if (index >= 0 && index < matchedKeys.size()) {
                    sb.append(matchedKeys.get(index));
                }
            } else if (c == '@' && i + 1 < path.length() && path.charAt(i+1) == '(') {
                int end = path.indexOf(')', i);
                if (end != -1) {
                    Type val = lookupTranspose(path.substring(i, end + 1), typeStack, matchedKeys);
                    String repr = val.repr();
                    if (repr.startsWith("'") && repr.endsWith("'")) {
                        sb.append(repr.substring(1, repr.length() - 1));
                    } else {
                        sb.append(repr);
                    }
                    i = end;
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Type buildNestedObjectFromPath(String path, Type leafType) {
        Type current = leafType;
        String[] segments = path.split("(?<!\\\\)\\.");
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = unescape(segments[i]);
            if (segment.isEmpty()) continue;
            
            if (segment.startsWith("[") && segment.endsWith("]")) {
                current = typeSystem.template("Array").construct(List.of(new TypeParameter.Spread(current)));
            } else {
                current = typeSystem.template("Object").construct(List.of(
                    new TypeParameter.Named(current, segment, false),
                    new TypeParameter.Spread(typeSystem.top())
                ));
            }
        }
        return current;
    }

    private Type term(Type type, String key) {
        TypeDef subject = typeSystem.unwrap((dev.akre.covenant.api.Type) type);
        TypeDef segment = typeSystem.unwrap((dev.akre.covenant.api.Type) typeSystem.expression("'" + key.replace("'", "''") + "'"));
        TypeDef result = TypeSystemUtils.termAt(typeSystem, subject, segment);
        return typeSystem.wrap(result);
    }

    private boolean isBottom(Type type) {
        return typeSystem.unwrap((dev.akre.covenant.api.Type) type) instanceof BottomType;
    }

    private Set<String> extractKeys(Type type) {
        Set<String> keys = new HashSet<>();
        TypeDef def = typeSystem.unwrap((dev.akre.covenant.api.Type) type);
        collectKeys(def, keys);
        return keys;
    }

    private void collectKeys(TypeDef def, Set<String> keys) {
        if (def instanceof UnionType u) {
            for (TypeDef member : u.members()) {
                collectKeys(member, keys);
            }
        } else if (def instanceof GenericTypeDef g && g.pattern() == AbstractTypeSystemBuilder.PatternConstructor.Pattern.OBJECT) {
            for (TypeDefParam tp : g.parameters()) {
                if (tp instanceof TypeDefParam.Named n) {
                    keys.add(n.name());
                }
            }
        }
    }
}
