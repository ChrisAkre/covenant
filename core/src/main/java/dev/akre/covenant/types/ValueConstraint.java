package dev.akre.covenant.types;

import com.google.re2j.Pattern;
import java.util.Objects;

public non-sealed interface ValueConstraint extends TypeDef {
    enum Operator {
        EQ("eq"),
        NEQ("neq"),
        GT("gt"),
        GTE("gte"),
        LT("lt"),
        LTE("lte"),
        MATCHES("matches"),
        NOT_MATCHES("nmatches");

        public final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * Determines if this operator with its value satisfies the other operator with its value.
         */
        public boolean satisfies(Operator other, Object thisVal, Object otherVal) {
            return satisfiesInternal(other, thisVal, otherVal);
        }

        private boolean satisfiesInternal(Operator other, Object thisVal, Object otherVal) {
            if (this == other && Objects.equals(thisVal, otherVal)) return true;

            // Regex vs Regex: No general satisfaction except equality (handled above)
            if (this == MATCHES || this == NOT_MATCHES || other == MATCHES || other == NOT_MATCHES) {
                if (this == EQ) {
                    if (other == MATCHES)
                        return Pattern.compile(otherVal.toString())
                                .matcher(thisVal.toString())
                                .matches();
                    if (other == NOT_MATCHES)
                        return !Pattern.compile(otherVal.toString())
                                .matcher(thisVal.toString())
                                .matches();
                }
                if (this == MATCHES && other == NEQ) {
                    // matches regex satisfies neq val if regex DOES NOT match val
                    return !Pattern.compile(thisVal.toString())
                            .matcher(otherVal.toString())
                            .matches();
                }
                if (this == NEQ && other == NOT_MATCHES) {
                    // neq val satisfies nmatches regex if regex matches ONLY val (hard to prove, so false)
                    return false;
                }
                return false;
            }

            int cmp;
            if (thisVal instanceof Comparable c1 && otherVal instanceof Comparable c2) {
                cmp = c1.compareTo(c2);
            } else {
                cmp = Objects.equals(thisVal, otherVal) ? 0 : -1;
            }

            return switch (this) {
                case GT ->
                    switch (other) {
                        case GT, GTE, NEQ -> cmp >= 0;
                        default -> false;
                    };
                case GTE ->
                    switch (other) {
                        case GTE -> cmp >= 0;
                        case GT -> cmp > 0;
                        default -> false;
                    };
                case LT ->
                    switch (other) {
                        case LT, LTE, NEQ -> cmp <= 0;
                        default -> false;
                    };
                case LTE ->
                    switch (other) {
                        case LTE -> cmp <= 0;
                        case LT -> cmp < 0;
                        default -> false;
                    };
                case EQ ->
                    switch (other) {
                        case EQ -> cmp == 0;
                        case GT -> cmp > 0;
                        case GTE -> cmp >= 0;
                        case LT -> cmp < 0;
                        case LTE -> cmp <= 0;
                        case NEQ -> cmp != 0;
                        default -> false;
                    };
                case NEQ -> false;
                default -> false;
            };
        }

        /**
         * Determines if this operator with its value is structurally disjoint from the other.
         */
        public boolean isDisjoint(Operator other, Object thisVal, Object otherVal) {
            return isDisjointInternal(other, thisVal, otherVal);
        }

        private boolean isDisjointInternal(Operator other, Object thisVal, Object otherVal) {
            if (this == MATCHES) {
                if (other == EQ)
                    return !Pattern.compile(thisVal.toString())
                            .matcher(otherVal.toString())
                            .matches();
                if (other == NOT_MATCHES && Objects.equals(thisVal, otherVal)) return true;
                return false;
            }
            if (this == NOT_MATCHES) {
                if (other == EQ)
                    return Pattern.compile(thisVal.toString())
                            .matcher(otherVal.toString())
                            .matches();
                if (other == MATCHES && Objects.equals(thisVal, otherVal)) return true;
                return false;
            }
            if (other == MATCHES || other == NOT_MATCHES) {
                return other.isDisjoint(this, otherVal, thisVal);
            }

            if (this == EQ) {
                return !satisfies(other, thisVal, otherVal);
            }
            if (other == EQ) {
                return !other.satisfies(this, otherVal, thisVal);
            }

            int cmp;
            if (thisVal instanceof Comparable c1 && otherVal instanceof Comparable c2) {
                cmp = c1.compareTo(c2);
            } else {
                cmp = Objects.equals(thisVal, otherVal) ? 0 : -1;
            }

            return switch (this) {
                case GT, GTE ->
                    switch (other) {
                        case LT -> cmp >= 0;
                        case LTE -> cmp > 0 || (cmp == 0 && this == GT);
                        default -> false;
                    };
                case LT, LTE ->
                    switch (other) {
                        case GT -> cmp <= 0;
                        case GTE -> cmp < 0 || (cmp == 0 && this == LT);
                        default -> false;
                    };
                default -> false;
            };
        }

        public Pair with(Operator other) {
            return new Pair(this, other);
        }

        public record Pair(Operator self, Operator other) {}
    }
}
