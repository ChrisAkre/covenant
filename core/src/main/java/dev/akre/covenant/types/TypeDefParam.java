package dev.akre.covenant.types;

import java.util.Objects;

public sealed interface TypeDefParam {
    TypeDef type();

    String repr();

    record Positional(TypeDef type, Integer index, boolean variadic) implements TypeDefParam {
        public Positional { Objects.requireNonNull(type); }

        @Override
        public String repr() {
            return type.repr() + (variadic ? "..." : "");
        }
    }

    record Named(TypeDef type, String name, boolean optional) implements TypeDefParam {
        public Named { Objects.requireNonNull(type); }

        @Override
        public String repr() {
            String qName = name;
            // Quote if not a valid identifier (alphanumeric and underscores only, cannot start with digit)
            if (!com.google.re2j.Pattern.matches("^[a-zA-Z_][a-zA-Z0-9_]*$", qName)) {
                qName = "'" + qName.replace("'", "''") + "'";
            }
            return qName + (optional ? "?: " : ": ") + type.repr();
        }
    }

    final class Constrained implements TypeDefParam {
        private final TypeDef type;
        private final String keyword;
        private final String value;
        private final boolean optional;
        private volatile dk.brics.automaton.Automaton automaton;

        public Constrained(TypeDef type, String keyword, String value, boolean optional) {
            this.type = Objects.requireNonNull(type);
            this.keyword = keyword;
            this.value = value;
            this.optional = optional;
        }

        @Override
        public TypeDef type() {
            return type;
        }

        @Override
        public String repr() {
            String qValue = value;
            if (qValue.contains(" ") || com.google.re2j.Pattern.matches("\\d+", qValue)) {
                qValue = "'" + qValue.replace("'", "''") + "'";
            }
            return "[" + keyword + " " + qValue + "]" + (optional ? "?: " : ": ") + type.repr();
        }

        public String keyword() {
            return keyword;
        }

        public String value() {
            return value;
        }

        public boolean optional() {
            return optional;
        }

        public dk.brics.automaton.Automaton automaton() {
            if (automaton == null) {
                synchronized (this) {
                    if (automaton == null) {
                        automaton = TypeSystemUtils.toAutomaton(value);
                    }
                }
            }
            return automaton;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Constrained that)) return false;
            return optional == that.optional && Objects.equals(type, that.type) && Objects.equals(keyword, that.keyword) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, keyword, value, optional);
        }

        @Override
        public String toString() {
            return "[" + keyword + " " + value + "]" + (optional ? "?: " : ": ") + type;
        }
    }

    record Spread(TypeDef type) implements TypeDefParam {
        public Spread { Objects.requireNonNull(type); }

        @Override
        public String repr() {
            return "...";
        }
    }
}
