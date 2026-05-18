package dev.akre.covenant.types;

import dev.akre.covenant.types.parser.Parser;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public record LengthConstraint(Integer min, Integer max) implements ValueConstraint {

    @Override
    public String keywordString() {
        if (min.equals(max)) return "length";
        if (min == 0) return "maxlength";
        if (max == null) return "minlength";
        return "minlength"; // Default to min for ranges
    }

    @Override
    public String valueString() {
        if (min.equals(max)) return String.valueOf(min);
        if (min == 0) return String.valueOf(max);
        if (max == null) return String.valueOf(min);
        return String.valueOf(min); // Default to min for ranges
    }

    public LengthConstraint {
        if (min == null) min = 0;
        if (min < 0) min = 0;
        if (max != null && max < min) {
            throw new IllegalArgumentException("max cannot be less than min");
        }
    }

    @Override
    public Collection<TypeDef> prune(AbstractTypeSystem system, TypeDef def) {
        if (def instanceof GenericTypeDef g) {
            if (this.isDisjoint(g)) return Set.of();
            if (this.isSatisfiedBy(g)) return Set.of(g);
        }
        if (def instanceof SymbolType s) {
            int len = s.value().length();
            boolean ok = len >= min && (max == null || len <= max);
            return ok ? Set.of(s) : Set.of();
        }
        if (def instanceof StringConstraint s) {
            boolean ok = this.isSatisfiedBy(s);
            return ok ? Set.of(s) : Set.of();
        }
        if (def instanceof IntersectionType intersection) {
            for (TypeDef member : intersection.members()) {
                if (member instanceof GenericTypeDef g && this.isDisjoint(g)) {
                    return Set.of();
                }
            }
        }
        if (!(def instanceof LengthConstraint other)) return null;
        if (this.equals(other)) return Set.of(this);

        if (this.satisfiesOther(system, other)) return Set.of(this);

        if (this.isDisjoint(other)) {
            return Set.of();
        }
        return null;
    }

    @Override
    public Collection<TypeDef> graft(AbstractTypeSystem system, TypeDef def) {
        if (!(def instanceof LengthConstraint other)) return null;
        if (this.equals(other)) return Set.of(this);

        if (this.satisfiesOther(system, other)) return Set.of(other);

        return null;
    }

    @Override
    public Collection<TypeDef> invert(AbstractTypeSystem system) {
        if (min > 0 && max != null) {
            return Set.of(new LengthConstraint(0, min - 1), new LengthConstraint(max + 1, null));
        } else if (min == 0 && max != null) {
            return Set.of(new LengthConstraint(max + 1, null));
        } else if (min > 0 && max == null) {
            return Set.of(new LengthConstraint(0, min - 1));
        }
        return Set.of(); // Should never invert an unbounded 0..null constraint as it's equivalent to Top
    }

    public static Parser<TypeExpr> parser() {
        return input -> {
            if (input.head().type() == Parser.TokenType.IDENTIFIER) {
                String keyword = input.head().value();
                if (keyword.equals("length") || keyword.equals("minlength") || keyword.equals("maxlength") || keyword.equals("minitems") || keyword.equals("maxitems")) {
                    Parser.InputState tail = input.tail();
                    if (tail.head().type() == Parser.TokenType.INT_LITERAL) {
                        int val = Integer.parseInt(tail.head().value());
                        LengthConstraint constraint = switch (keyword) {
                            case "length" -> new LengthConstraint(val, val);
                            case "minlength", "minitems" -> new LengthConstraint(val, null);
                            case "maxlength", "maxitems" -> new LengthConstraint(0, val);
                            default -> throw new IllegalStateException();
                        };
                        return new Parser.Success<>(new TypeExpr.ConstraintExpr(constraint), tail.tail());
                    }
                    return new Parser.Failure<>("Expected integer after " + keyword, tail);
                }
            }
            return new Parser.Failure<>("Not a length constraint", input);
        };
    }

    @Override
    public boolean satisfiesOther(AbstractTypeSystem system, TypeDef other) {
        if (other instanceof LengthConstraint lc) {
            return this.min >= lc.min && (lc.max == null || (this.max != null && this.max <= lc.max));
        }

        if (other instanceof NominalDef n) {
            if (n.name().equals("String")) return true; // length constraints are subtypes of String or Array
            if (n.name().equals("Array")) return true;
        }

        return false;
    }

    public boolean isSatisfiedBy(GenericTypeDef arrayType) {
        if (arrayType.pattern() != AbstractTypeSystemBuilder.PatternConstructor.Pattern.ARRAY) {
            return false;
        }
        int arrayMin = 0;
        Integer arrayMax = 0;

        for (TypeDefParam p : arrayType.parameters()) {
            if (p instanceof TypeDefParam.Positional pos) {
                if (pos.variadic()) {
                    arrayMax = null;
                } else {
                    arrayMin++;
                    if (arrayMax != null) arrayMax++;
                }
            } else if (p instanceof TypeDefParam.Spread) {
                arrayMax = null;
            }
        }

        return arrayMin >= this.min && (this.max == null || (arrayMax != null && arrayMax <= this.max));
    }

    public boolean isSatisfiedBy(StringConstraint s) {
        int len = s.value().length();
        return len >= this.min && (this.max == null || len <= this.max);
    }

    public boolean isDisjoint(LengthConstraint other) {
        return (this.max != null && this.max < other.min) || (other.max != null && other.max < this.min);
    }

    public boolean isDisjoint(GenericTypeDef arrayType) {
        if (arrayType.pattern() != AbstractTypeSystemBuilder.PatternConstructor.Pattern.ARRAY) {
            return false;
        }
        int minLength = 0;
        Integer maxLength = 0;

        for (TypeDefParam p : arrayType.parameters()) {
            if (p instanceof TypeDefParam.Positional pos) {
                if (pos.variadic()) {
                    maxLength = null;
                } else {
                    // Non-variadic positional params are required. Wait...
                    // In Covenant, if `pos.type()` is `Union(X, Null)` where it's at the end, does it mean optional?
                    // Variadic is the only way to be optional in an array in Covenant.
                    minLength++;
                    if (maxLength != null) {
                        maxLength++;
                    }
                }
            } else if (p instanceof TypeDefParam.Spread) {
                maxLength = null;
            }
        }

        if (this.max != null && this.max < minLength) {
            return true;
        }
        if (maxLength != null && maxLength < this.min) {
            return true;
        }
        return false;
    }

    @Override
    public String repr() {
        if (min.equals(max)) {
            return "length " + min;
        } else if (min == 0 && max != null) {
            return "maxlength " + max;
        } else if (max == null) {
            return "minlength " + min;
        } else {
            return "minlength " + min + " & maxlength " + max;
        }
    }

    @Override
    public String toString() {
        return repr();
    }
}
