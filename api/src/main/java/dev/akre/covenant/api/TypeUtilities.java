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
            return self.intersect(self.negate());
        }
    }

    public static Type concatArrayTypes(Type.GenericType self, Type.GenericType other) {
        if (!self.isArray() || !other.isArray()) {
            return self.intersect(self.negate());
        }

        List<TypeParameter> mergedParams = new ArrayList<>();
        int variadicIndex = -1;
        List<TypeParameter> selfParams = self.genericParameters();

        for (int i = 0; i < selfParams.size(); i++) {
            if (selfParams.get(i).parameter() instanceof Parameter.Positional pos && pos.variadic()) {
                variadicIndex = i;
                break;
            }
        }

        if (variadicIndex == -1) {
            // Case A: Left Side is Fixed
            for (TypeParameter p1 : selfParams) {
                if (p1.parameter() instanceof Parameter.Positional) {
                    mergedParams.add(p1);
                }
            }
            for (TypeParameter p2 : other.genericParameters()) {
                if (p2.parameter() instanceof Parameter.Positional) {
                    mergedParams.add(p2);
                }
            }
        } else {
            // Case B: Left Side is Variadic (Has a Spread)
            for (int i = 0; i < variadicIndex; i++) {
                TypeParameter p1 = selfParams.get(i);
                if (p1.parameter() instanceof Parameter.Positional) {
                    mergedParams.add(p1);
                }
            }

            TypeParameter v1 = selfParams.get(variadicIndex);
            Type mergedVariadicType = v1.type();

            // Absorb remaining left elements
            for (int i = variadicIndex + 1; i < selfParams.size(); i++) {
                TypeParameter p1 = selfParams.get(i);
                if (p1.parameter() instanceof Parameter.Positional) {
                    mergedVariadicType = mergedVariadicType.union(p1.type());
                }
            }

            // Absorb all right elements
            for (TypeParameter p2 : other.genericParameters()) {
                if (p2.parameter() instanceof Parameter.Positional) {
                    mergedVariadicType = mergedVariadicType.union(p2.type());
                }
            }

            mergedParams.add(new TypeParameter(mergedVariadicType, new Parameter.Positional(0, true)));
        }

        return self.template().construct(mergedParams);
    }

    public static Type concatObjectTypes(Type.GenericType self, Type.GenericType other) {
        if (!self.isObject() || !other.isObject()) {
            return self.intersect(self.negate());
        }

        List<TypeParameter> selfParams = self.genericParameters();
        List<TypeParameter> otherParams = other.genericParameters();

        Parameter.Spread otherSpread = (Parameter.Spread) otherParams.stream()
                .map(TypeParameter::parameter)
                .filter(p -> p instanceof Parameter.Spread)
                .findFirst()
                .orElse(null);

        boolean otherIsOpen = otherSpread != null;
        Type otherSpreadType = null;
        if (otherIsOpen) {
            otherSpreadType = otherParams.stream()
                    .filter(tp -> tp.parameter() == otherSpread)
                    .findFirst()
                    .map(TypeParameter::type)
                    .orElse(null);
        }

        List<TypeParameter> mergedParams = new ArrayList<>();
        java.util.Set<String> processedRightNamed = new java.util.HashSet<>();
        java.util.Set<String> processedRightConstrained = new java.util.HashSet<>();

        // 1. Process Left Properties (self)
        for (TypeParameter tp1 : selfParams) {
            Parameter p1 = tp1.parameter();
            if (p1 instanceof Parameter.Named n1) {
                TypeParameter tp2 = findNamed(otherParams, n1.name());
                if (tp2 != null) {
                    Parameter.Named n2 = (Parameter.Named) tp2.parameter();
                    if (n2.optional()) {
                        mergedParams.add(new TypeParameter(
                                tp1.type().union(tp2.type()), new Parameter.Named(n1.name(), 0, n1.optional())));
                    } else {
                        mergedParams.add(tp2);
                    }
                    processedRightNamed.add(n1.name());
                } else if (otherIsOpen) {
                    if (otherSpreadType != null
                            && !otherSpreadType.repr().equals("Any")
                            && !otherSpreadType.repr().equals("top")) {
                        mergedParams.add(new TypeParameter(tp1.type().union(otherSpreadType), p1));
                    }
                } else {
                    mergedParams.add(tp1);
                }
            } else if (p1 instanceof Parameter.Constrained c1) {
                String key = c1.keyword() + ":" + c1.value();
                TypeParameter tp2 = findConstrained(otherParams, c1.keyword(), c1.value());
                if (tp2 != null) {
                    Parameter.Constrained c2 = (Parameter.Constrained) tp2.parameter();
                    if (c2.optional()) {
                        mergedParams.add(new TypeParameter(
                                tp1.type().union(tp2.type()),
                                new Parameter.Constrained(c1.keyword(), c1.value(), 0, c1.optional())));
                    } else {
                        mergedParams.add(tp2);
                    }
                    processedRightConstrained.add(key);
                } else if (otherIsOpen) {
                    if (otherSpreadType != null
                            && !otherSpreadType.repr().equals("Any")
                            && !otherSpreadType.repr().equals("top")) {
                        mergedParams.add(new TypeParameter(tp1.type().union(otherSpreadType), p1));
                    }
                } else {
                    mergedParams.add(tp1);
                }
            }
        }

        // 2. Append remaining Right Properties
        for (TypeParameter tp2 : otherParams) {
            Parameter p2 = tp2.parameter();
            if (p2 instanceof Parameter.Named n2) {
                if (processedRightNamed.contains(n2.name())) continue;
                mergedParams.add(tp2);
            } else if (p2 instanceof Parameter.Constrained c2) {
                if (processedRightConstrained.contains(c2.keyword() + ":" + c2.value())) continue;
                mergedParams.add(tp2);
            }
        }

        // 3. Process Spread
        if (otherIsOpen) {
            mergedParams.add(
                    new TypeParameter(otherSpreadType, new Parameter.Spread(otherSpreadType == null ? null : 0)));
        } else {
            Parameter.Spread selfSpread = (Parameter.Spread) selfParams.stream()
                    .map(TypeParameter::parameter)
                    .filter(p -> p instanceof Parameter.Spread)
                    .findFirst()
                    .orElse(null);
            if (selfSpread != null) {
                Type selfSpreadType = selfParams.stream()
                        .filter(tp -> tp.parameter() == selfSpread)
                        .findFirst()
                        .map(TypeParameter::type)
                        .orElse(null);
                mergedParams.add(
                        new TypeParameter(selfSpreadType, new Parameter.Spread(selfSpreadType == null ? null : 0)));
            }
        }

        return self.template().construct(mergedParams);
    }

    private static TypeParameter findNamed(List<TypeParameter> params, String name) {
        for (TypeParameter tp : params) {
            if (tp.parameter() instanceof Parameter.Named n && n.name().equals(name)) {
                return tp;
            }
        }
        return null;
    }

    private static TypeParameter findConstrained(List<TypeParameter> params, String k, String v) {
        for (TypeParameter tp : params) {
            if (tp.parameter() instanceof Parameter.Constrained c
                    && c.keyword().equals(k)
                    && c.value().equals(v)) {
                return tp;
            }
        }
        return null;
    }
}
