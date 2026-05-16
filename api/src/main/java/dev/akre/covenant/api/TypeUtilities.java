package dev.akre.covenant.api;

import java.util.ArrayList;
import java.util.List;

public class TypeUtilities {

    public static Type concatGenericTypes(Type.GenericType self, Type.GenericType other) {
        if (self.isArray() && other.isArray()) {
            return concatArrayTypes(self, other);
        } else if (self.isObject() && other.isObject()) {
            return concatObjectTypes(self, other);
        } else {
            return self.system().bottom();
        }
    }

    public static Type concatArrayTypes(Type.GenericType self, Type.GenericType other) {
        if (!self.isArray() || !other.isArray()) {
            return self.system().bottom();
        }

        List<TypeParameter> mergedParams = new ArrayList<>();
        int variadicIndex = -1;
        List<TypeParameter> selfParams = self.genericParameters();

        for (int i = 0; i < selfParams.size(); i++) {
            if (selfParams.get(i) instanceof TypeParameter.Positional pos && pos.variadic()) {
                variadicIndex = i;
                break;
            }
        }

        if (variadicIndex == -1) {
            // Case A: Left Side is Fixed
            for (TypeParameter p1 : selfParams) {
                if (p1 instanceof TypeParameter.Positional) {
                    mergedParams.add(p1);
                }
            }
            for (TypeParameter p2 : other.genericParameters()) {
                if (p2 instanceof TypeParameter.Positional) {
                    mergedParams.add(p2);
                }
            }
        } else {
            // Case B: Left Side is Variadic (Has a Spread)
            for (int i = 0; i < variadicIndex; i++) {
                TypeParameter p1 = selfParams.get(i);
                if (p1 instanceof TypeParameter.Positional) {
                    mergedParams.add(p1);
                }
            }

            TypeParameter v1 = selfParams.get(variadicIndex);
            Type mergedVariadicType = v1.type();

            // Absorb remaining left elements
            for (int i = variadicIndex + 1; i < selfParams.size(); i++) {
                TypeParameter p1 = selfParams.get(i);
                if (p1 instanceof TypeParameter.Positional) {
                    mergedVariadicType = mergedVariadicType.union(p1.type());
                }
            }

            // Absorb all right elements
            for (TypeParameter p2 : other.genericParameters()) {
                if (p2 instanceof TypeParameter.Positional) {
                    mergedVariadicType = mergedVariadicType.union(p2.type());
                }
            }

            mergedParams.add(new TypeParameter.Positional(mergedVariadicType, 0, true));
        }

        return self.template().construct(mergedParams);
    }

    public static Type concatObjectTypes(Type.GenericType self, Type.GenericType other) {
        if (!self.isObject() || !other.isObject()) {
            return self.system().bottom();
        }

        List<TypeParameter> selfParams = self.genericParameters();
        List<TypeParameter> otherParams = other.genericParameters();

        TypeParameter.Spread otherSpread = (TypeParameter.Spread) otherParams.stream()
                .filter(p -> p instanceof TypeParameter.Spread)
                .findFirst()
                .orElse(null);

        boolean otherIsOpen = otherSpread != null;
        Type otherSpreadType = otherIsOpen ? otherSpread.type() : null;

        List<TypeParameter> mergedParams = new ArrayList<>();
        java.util.Set<String> processedRightNamed = new java.util.HashSet<>();
        java.util.Set<String> processedRightConstrained = new java.util.HashSet<>();

        // 1. Process Left Properties (self)
        for (TypeParameter tp1 : selfParams) {
            if (tp1 instanceof TypeParameter.Named n1) {
                TypeParameter tp2 = findNamed(otherParams, n1.name());
                if (tp2 instanceof TypeParameter.Named n2) {
                    if (n2.optional()) {
                        mergedParams.add(new TypeParameter.Named(
                                tp1.type().union(tp2.type()), n1.name(), n1.optional()));
                    } else {
                        mergedParams.add(tp2);
                    }
                    processedRightNamed.add(n1.name());
                } else if (otherIsOpen) {
                    if (otherSpreadType != null
                            && !otherSpreadType.repr().equals("Any")
                            && !otherSpreadType.repr().equals("top")) {
                        mergedParams.add(new TypeParameter.Named(tp1.type().union(otherSpreadType), n1.name(), n1.optional()));
                    }
                } else {
                    mergedParams.add(tp1);
                }
            } else if (tp1 instanceof TypeParameter.Constrained c1) {
                String key = c1.keyword() + ":" + c1.value();
                TypeParameter tp2 = findConstrained(otherParams, c1.keyword(), c1.value());
                if (tp2 instanceof TypeParameter.Constrained c2) {
                    if (c2.optional()) {
                        mergedParams.add(new TypeParameter.Constrained(
                                tp1.type().union(tp2.type()),
                                c1.keyword(), c1.value(), c1.optional()));
                    } else {
                        mergedParams.add(tp2);
                    }
                    processedRightConstrained.add(key);
                } else if (otherIsOpen) {
                    if (otherSpreadType != null
                            && !otherSpreadType.repr().equals("Any")
                            && !otherSpreadType.repr().equals("top")) {
                        mergedParams.add(new TypeParameter.Constrained(tp1.type().union(otherSpreadType), c1.keyword(), c1.value(), c1.optional()));
                    }
                } else {
                    mergedParams.add(tp1);
                }
            }
        }

        // 2. Append remaining Right Properties
        for (TypeParameter tp2 : otherParams) {
            if (tp2 instanceof TypeParameter.Named n2) {
                if (processedRightNamed.contains(n2.name())) continue;
                mergedParams.add(tp2);
            } else if (tp2 instanceof TypeParameter.Constrained c2) {
                if (processedRightConstrained.contains(c2.keyword() + ":" + c2.value())) continue;
                mergedParams.add(tp2);
            }
        }

        // 3. Process Spread
        if (otherIsOpen) {
            mergedParams.add(new TypeParameter.Spread(otherSpreadType));
        } else {
            TypeParameter.Spread selfSpread = (TypeParameter.Spread) selfParams.stream()
                    .filter(p -> p instanceof TypeParameter.Spread)
                    .findFirst()
                    .orElse(null);
            if (selfSpread != null) {
                mergedParams.add(new TypeParameter.Spread(selfSpread.type()));
            }
        }

        return self.template().construct(mergedParams);
    }

    private static TypeParameter findNamed(List<TypeParameter> params, String name) {
        for (TypeParameter tp : params) {
            if (tp instanceof TypeParameter.Named n && n.name().equals(name)) {
                return tp;
            }
        }
        return null;
    }

    private static TypeParameter findConstrained(List<TypeParameter> params, String k, String v) {
        for (TypeParameter tp : params) {
            if (tp instanceof TypeParameter.Constrained c
                    && c.keyword().equals(k)
                    && c.value().equals(v)) {
                return tp;
            }
        }
        return null;
    }

    public static Type at(Type.GenericType array, int idx) {
        return array.termAt(String.valueOf(idx));
    }
}
