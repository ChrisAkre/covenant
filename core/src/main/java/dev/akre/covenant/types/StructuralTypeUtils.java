package dev.akre.covenant.types;

import java.util.*;
import java.util.stream.Stream;

/**
 * Static utilities for deep structural comparison and algebraic merging of
 * positional (array/tuple) and named/constrained (object) parameters.
 */
public final class StructuralTypeUtils {

    private StructuralTypeUtils() {}

    // --- SUBTYPING (satisfies) ---

    public static boolean satisfiesPositional(AbstractTypeSystem system, GenericTypeDef self, GenericTypeDef other) {
        TypeDef t1Spread = self.spreadParam();
        TypeDef t2Spread = other.spreadParam();

        // If BOTH are standard arrays (only spreads, no positionals), use spread covariance
        if (self.parameters().stream().noneMatch(p -> p instanceof TypeDefParam.Positional) &&
            other.parameters().stream().noneMatch(p -> p instanceof TypeDefParam.Positional)) {
            return system.satisfies(t1Spread, t2Spread);
        }

        TypeDef nullType = system.nilDef();

        int i = 0;
        int j = 0;
        while (i < self.parameters().size() && j < other.parameters().size()) {
            TypeDefParam tp1 = self.parameters().get(i);
            TypeDefParam tp2 = other.parameters().get(j);

            if (tp1 instanceof TypeDefParam.Positional pos1
                    && tp2 instanceof TypeDefParam.Positional pos2) {

                TypeDef type1 = tp1.type();
                TypeDef type2 = tp2.type();

                if (pos1.variadic()) {
                    if (!pos2.variadic()) {
                        if (!system.satisfies(system.unionDef(type1, nullType), type2)) {
                            return false;
                        }
                        j++;
                        continue;
                    }
                    return system.satisfies(type1, type2);
                }

                if (pos2.variadic()) {
                    while (i < self.parameters().size()) {
                        TypeDefParam rem = self.parameters().get(i);
                        if (rem instanceof TypeDefParam.Positional remPos) {
                            TypeDef t = rem.type();
                            TypeDef sourceType = remPos.variadic() ? system.unionDef(t, nullType) : t;
                            if (!system.satisfies(sourceType, system.unionDef(type2, nullType))) return false;
                        }
                        i++;
                    }
                    return true;
                }

                if (!system.satisfies(type1, type2)) {
                    return false;
                }
                i++;
                j++;
            } else {
                i++;
                j++;
            }
        }

        while (j < other.parameters().size()
                && other.parameters().get(j) instanceof TypeDefParam.Positional p
                && p.variadic()) {
            j++;
        }

        return i == self.parameters().size() && j == other.parameters().size();
    }

    public static boolean satisfiesObject(AbstractTypeSystem system, GenericTypeDef self, GenericTypeDef other) {
        TypeDef otherSpreadType = other.spreadParam();
        boolean otherIsOpen = !(otherSpreadType instanceof BottomType);

        for (TypeDefParam tp1 : self.parameters()) {
            if (tp1 instanceof TypeDefParam.Named n1) {
                TypeDef requiredType = TypeSystemUtils.termAt(system, other, n1.name());
                if (!system.satisfies(n1.type(), requiredType)) return false;
            } else if (tp1 instanceof TypeDefParam.Constrained c1) {
                List<TypeDef> overlappingConstraints = new java.util.ArrayList<>();
                for (TypeDefParam tp2 : other.parameters()) {
                    if (tp2 instanceof TypeDefParam.Constrained c2 && c1.constraint().getClass() == c2.constraint().getClass()) {
                        if (TypeSystemUtils.doRegexesOverlap(c1, c2)) {
                            overlappingConstraints.add(c2.type());
                        }
                    }
                }
                
                if (!overlappingConstraints.isEmpty()) {
                    TypeDef requiredType = overlappingConstraints.size() == 1 ? overlappingConstraints.get(0) : system.intersectDef(overlappingConstraints.toArray(new TypeDef[0]));
                    if (!system.satisfies(c1.type(), requiredType)) return false;
                }
                
                if (overlappingConstraints.isEmpty()) {
                    if (!otherIsOpen || !system.satisfies(c1.type(), otherSpreadType)) return false;
                }
            } else if (tp1 instanceof TypeDefParam.Spread s1) {
                if (!otherIsOpen || !system.satisfies(s1.type(), otherSpreadType)) return false;
                for (TypeDefParam tp2 : other.parameters()) {
                    if (tp2 instanceof TypeDefParam.Named n2 && self.findNamed(n2.name()) == null) {
                        boolean governedByConstraint = false;
                        for (TypeDefParam subTp : self.parameters()) {
                            if (subTp instanceof TypeDefParam.Constrained c && TypeSystemUtils.matches(c, n2.name())) {
                                governedByConstraint = true;
                                break;
                            }
                        }
                        if (!governedByConstraint) {
                            if (!system.satisfies(s1.type(), n2.type())) return false;
                        }
                    } else if (tp2 instanceof TypeDefParam.Constrained c2 && self.findConstrained(c2.constraint()) == null) {
                        if (!system.satisfies(s1.type(), c2.type())) return false;
                    }
                }
            }
        }

        for (TypeDefParam tp2 : other.parameters()) {
            if (tp2 instanceof TypeDefParam.Named n2 && !n2.optional()) {
                TypeDef thisType = TypeSystemUtils.termAt(system, self, n2.name());
                if (system.wrap(thisType).isBottom() || !system.satisfies(thisType, n2.type())) {
                    TypeDef nullType = system.nilDef();
                    if (nullType == null || !system.satisfies(nullType, n2.type())) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    // --- INTERSECTION (prune) ---

    public static Collection<TypeDef> intersectPositional(AbstractTypeSystem system, GenericTypeDef self, GenericTypeDef other) {
        List<TypeDefParam> merged = new java.util.ArrayList<>();
        int i = 0;
        int j = 0;
        
        boolean selfOpen = !(self.spreadParam() instanceof BottomType);
        boolean otherOpen = !(other.spreadParam() instanceof BottomType);

        while (i < self.parameters().size() || j < other.parameters().size()) {
            TypeDefParam p1 = (i < self.parameters().size()) ? self.parameters().get(i) : null;
            TypeDefParam p2 = (j < other.parameters().size()) ? other.parameters().get(j) : null;

            if (p1 instanceof TypeDefParam.Positional pos1 && p2 instanceof TypeDefParam.Positional pos2) {
                TypeDef res = system.intersectDef(pos1.type(), pos2.type());
                if (system.wrap(res).isBottom()) return Set.of();
                merged.add(new TypeDefParam.Positional(res, pos1.index(), pos1.variadic() && pos2.variadic()));
                if (!pos1.variadic()) i++;
                if (!pos2.variadic()) j++;
                if (pos1.variadic() && pos2.variadic()) break;
            } else if (p1 instanceof TypeDefParam.Positional pos1 && pos1.variadic()) {
                TypeDef t2 = (p2 != null) ? p2.type() : other.spreadParam();
                if (p2 == null && system.wrap(t2).isBottom()) { i++; continue; }
                TypeDef res = system.intersectDef(pos1.type(), t2);
                if (system.wrap(res).isBottom()) return Set.of();
                merged.add(new TypeDefParam.Positional(res, pos1.index(), p2 == null && otherOpen));
                if (p2 != null) j++; else i++;
            } else if (p2 instanceof TypeDefParam.Positional pos2 && pos2.variadic()) {
                TypeDef t1 = (p1 != null) ? p1.type() : self.spreadParam();
                if (p1 == null && system.wrap(t1).isBottom()) { j++; continue; }
                TypeDef res = system.intersectDef(t1, pos2.type());
                if (system.wrap(res).isBottom()) return Set.of();
                merged.add(new TypeDefParam.Positional(res, pos2.index(), p1 == null && selfOpen));
                if (p1 != null) i++; else j++;
            } else if (p1 instanceof TypeDefParam.Spread s1 && p2 instanceof TypeDefParam.Spread s2) {
                TypeDef res = system.intersectDef(s1.type(), s2.type());
                if (!system.wrap(res).isBottom()) merged.add(new TypeDefParam.Spread(res));
                i++; j++;
            } else {
                return Set.of(); 
            }
        }
        return Set.of(new GenericTypeDef(self.template(), self.pattern(), merged));
    }

    public static Collection<TypeDef> intersectObject(AbstractTypeSystem system, GenericTypeDef self, GenericTypeDef other) {
        List<TypeDefParam> mergedParams = new java.util.ArrayList<>();

        TypeDef t1Spread = self.spreadParam();
        TypeDef t2Spread = other.spreadParam();
        boolean thisOpen = !(t1Spread instanceof BottomType);
        boolean otherOpen = !(t2Spread instanceof BottomType);

        java.util.Set<String> processedNamed = new java.util.HashSet<>();
        java.util.Set<ValueConstraint> processedConstrained = new java.util.HashSet<>();

        for (TypeDefParam tp1 : self.parameters()) {
            if (tp1 instanceof TypeDefParam.Named n1) {
                TypeDefParam tp2 = other.findNamed(n1.name());
                TypeDef t1 = n1.type();
                if (tp2 instanceof TypeDefParam.Named n2) {
                    TypeDef t2 = n2.type();
                    TypeDef intersected = system.intersectDef(t1, t2);
                    if (system.wrap(intersected).isBottom()) return Set.of();
                    mergedParams.add(new TypeDefParam.Named(
                            intersected,
                            n1.name(),
                            n1.optional() && n2.optional()));
                } else {
                    TypeDef constraintType = null;
                    for (TypeDefParam tp : other.parameters()) {
                        if (tp instanceof TypeDefParam.Constrained c && TypeSystemUtils.matches(c, n1.name())) {
                            constraintType = tp.type();
                            break;
                        }
                    }
                    TypeDef t2 = constraintType != null ? constraintType : t2Spread;
                    TypeDef intersected = system.intersectDef(t1, t2);
                    if (system.wrap(intersected).isBottom()) return Set.of();
                    mergedParams.add(new TypeDefParam.Named(intersected, n1.name(), n1.optional()));
                }
                processedNamed.add(n1.name());
            } else if (tp1 instanceof TypeDefParam.Constrained c1) {
                TypeDefParam tp2 = other.findConstrained(c1.constraint());
                TypeDef t1 = c1.type();
                if (tp2 instanceof TypeDefParam.Constrained c2) {
                    TypeDef t2 = c2.type();
                    TypeDef intersected = system.intersectDef(t1, t2);
                    if (system.wrap(intersected).isBottom()) return Set.of();
                    mergedParams.add(new TypeDefParam.Constrained(
                            intersected,
                            c1.constraint(),
                            c1.optional() && c2.optional()));
                } else {
                    TypeDef intersected = system.intersectDef(t1, t2Spread);
                    if (system.wrap(intersected).isBottom()) return Set.of();
                    mergedParams.add(new TypeDefParam.Constrained(
                            intersected, c1.constraint(), c1.optional()));
                }
                processedConstrained.add(c1.constraint());
            }
        }

        for (TypeDefParam tp2 : other.parameters()) {
            if (tp2 instanceof TypeDefParam.Named n2) {
                if (processedNamed.contains(n2.name())) continue;
                TypeDef t2 = n2.type();
                TypeDef constraintType = null;
                for (TypeDefParam tp : self.parameters()) {
                    if (tp instanceof TypeDefParam.Constrained c && TypeSystemUtils.matches(c, n2.name())) {
                        constraintType = tp.type();
                        break;
                    }
                }
                TypeDef t1 = constraintType != null ? constraintType : t1Spread;
                TypeDef intersected = system.intersectDef(t2, t1);
                if (system.wrap(intersected).isBottom()) return Set.of();
                mergedParams.add(new TypeDefParam.Named(intersected, n2.name(), n2.optional()));
            } else if (tp2 instanceof TypeDefParam.Constrained c2) {
                if (processedConstrained.contains(c2.constraint())) continue;
                TypeDef t2 = c2.type();
                TypeDef intersected = system.intersectDef(t2, t1Spread);
                if (system.wrap(intersected).isBottom()) return Set.of();
                mergedParams.add(new TypeDefParam.Constrained(
                            intersected, c2.constraint(), c2.optional()));
            }
        }

        if (thisOpen && otherOpen) {
            TypeDef intersected = system.intersectDef(t1Spread, t2Spread);
            if (!system.wrap(intersected).isBottom()) {
                mergedParams.add(new TypeDefParam.Spread(intersected));
            }
        }

        return Set.of(new GenericTypeDef(self.template(), self.pattern(), mergedParams));
    }

    // --- UNION (graft) ---

    public static Collection<TypeDef> unionPositional(AbstractTypeSystem system, GenericTypeDef self, GenericTypeDef other) {
        if (self.parameters().size() == other.parameters().size()) {
            boolean compatible = true;
            for (int i = 0; i < self.parameters().size(); i++) {
                TypeDefParam p1 = self.parameters().get(i);
                TypeDefParam p2 = other.parameters().get(i);
                if (p1.getClass() != p2.getClass()) { compatible = false; break; }
                if (p1 instanceof TypeDefParam.Positional pos1 && p2 instanceof TypeDefParam.Positional pos2) {
                    if (pos1.variadic() != pos2.variadic()) { compatible = false; break; }
                }
            }

            if (compatible) {
                List<TypeDefParam> merged = new java.util.ArrayList<>();
                for (int i = 0; i < self.parameters().size(); i++) {
                    TypeDefParam p1 = self.parameters().get(i);
                    TypeDefParam p2 = other.parameters().get(i);
                    if (p1 instanceof TypeDefParam.Positional pos1 && p2 instanceof TypeDefParam.Positional pos2) {
                        merged.add(new TypeDefParam.Positional(system.unionDef(pos1.type(), pos2.type()), pos1.index(), pos1.variadic()));
                    } else if (p1 instanceof TypeDefParam.Spread s1 && p2 instanceof TypeDefParam.Spread s2) {
                        merged.add(new TypeDefParam.Spread(system.unionDef(s1.type(), s2.type())));
                    }
                }
                return Set.of(new GenericTypeDef(self.template(), self.pattern(), merged));
            }
        }
        return null;
    }

    public static Collection<TypeDef> unionObject(AbstractTypeSystem system, GenericTypeDef self, GenericTypeDef other) {
        boolean thisOpen = !(self.spreadParam() instanceof BottomType);
        boolean otherOpen = !(other.spreadParam() instanceof BottomType);

        if (thisOpen == otherOpen) {
            boolean canMerge = true;
            long thisCount = self.parameters().stream()
                    .filter(tp -> tp instanceof TypeDefParam.Named
                            || tp instanceof TypeDefParam.Constrained)
                    .count();
            long otherCount = other.parameters().stream()
                    .filter(tp -> tp instanceof TypeDefParam.Named
                            || tp instanceof TypeDefParam.Constrained)
                    .count();

            if (thisCount != otherCount) {
                canMerge = false;
            } else {
                for (TypeDefParam tp1 : self.parameters()) {
                    if (tp1 instanceof TypeDefParam.Named n1) {
                        TypeDefParam tp2 = other.findNamed(n1.name());
                        if (!(tp2 instanceof TypeDefParam.Named n2) || n1.optional() != n2.optional()) {
                            canMerge = false;
                            break;
                        }
                    } else if (tp1 instanceof TypeDefParam.Constrained c1) {
                        TypeDefParam tp2 = other.findConstrained(c1.constraint());
                        if (!(tp2 instanceof TypeDefParam.Constrained c2) || c1.optional() != c2.optional()) {
                            canMerge = false;
                            break;
                        }
                    }
                }
            }

            if (canMerge) {
                List<TypeDefParam> mergedParams = new java.util.ArrayList<>();
                for (TypeDefParam tp1 : self.parameters()) {
                    if (tp1 instanceof TypeDefParam.Named n1) {
                        TypeDefParam.Named n2 = (TypeDefParam.Named) other.findNamed(n1.name());
                        mergedParams.add(new TypeDefParam.Named(
                                system.unionDef(n1.type(), n2.type()),
                                n1.name(), n1.optional()));
                    } else if (tp1 instanceof TypeDefParam.Constrained c1) {
                        TypeDefParam.Constrained c2 = (TypeDefParam.Constrained) other.findConstrained(c1.constraint());
                        mergedParams.add(new TypeDefParam.Constrained(
                                system.unionDef(c1.type(), c2.type()),
                                c1.constraint(), c1.optional()));
                    } else if (tp1 instanceof TypeDefParam.Spread s1) {
                        TypeDefParam tp2Spread = other.parameters().stream()
                                .filter(tp -> tp instanceof TypeDefParam.Spread)
                                .findFirst()
                                .get();
                        mergedParams.add(new TypeDefParam.Spread(
                                system.unionDef(s1.type(), tp2Spread.type())));
                    }
                }
                return Set.of(new GenericTypeDef(self.template(), self.pattern(), mergedParams));
            }
        }
        return null;
    }
}
