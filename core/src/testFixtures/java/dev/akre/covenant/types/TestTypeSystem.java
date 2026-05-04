package dev.akre.covenant.types;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeSystem;
import dev.akre.covenant.api.TypeUtilities;
import dev.akre.test.LexicalAssert;
import dev.akre.test.Tokenizer;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * A implementation of AbstractTypeSystem designed for testing, providing fluent AssertJ-style assertions.
 */
public class TestTypeSystem implements AbstractTypeSystem {
    public static final Tokenizer COVENANT_TOKENIZER = Tokenizer.ofRegex(
            Pattern.compile("""
                    (?x)
                    "(?:\\\\\\\\\"|[^"])*"     # 1. Strings
                    |                     # OR
                    [a-zA-Z0-9_]+         # 2. Words (Integers, true, false, null)
                    |                     # OR
                    [^a-zA-Z0-9_\\s]       # 3. Symbols ({}, [], :, ,, ., -)""")
    );


    public static TestTypeSystem of(TypeSystem other) {
        if (other instanceof AbstractTypeSystem a) {
            return new TestTypeSystem(a);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private final Map<String, TypeDef> types;
    private final TypeParser parser;
    private final TypeDef top;
    private final TypeDef bottom;
    private final TypeDef nil;

    protected TestTypeSystem(AbstractTypeSystem other) {
        this(other.typesDef(), other.parser());
    }

    TestTypeSystem(Map<String, TypeDef> foreignTypes, TypeParser parser) {
        this.types = new HashMap<>(foreignTypes);
        this.parser = parser;
        this.top = types.values().stream().filter(TopType.class::isInstance).findFirst().orElseThrow();
        this.bottom = types.values().stream().filter(BottomType.class::isInstance).findFirst().orElseThrow();
        this.nil = types.values().stream().filter(t -> t.attributes().contains(dev.akre.covenant.api.TypeAttribute.NULL_SEMANTICS)).findFirst().orElse(null);
    }

    @Override
    public Map<String, TypeDef> typesDef() {
        return types;
    }

    @Override
    public TypeDef topDef() {
        return top;
    }

    @Override
    public TypeDef bottomDef() {
        return bottom;
    }

    @Override
    public TypeDef nilDef() {
        return nil;
    }

    @Override
    public TypeParser parser() {
        return parser;
    }

    public TypeAssertion assertThat(String expression) {
        return new TypeAssertion(typeExpressionDef(expression));
    }

    public TypeAssertion assertThat(OwnedTypeDef type) {
        return new TypeAssertion(unwrap(type));
    }

    public TypeAssertion assertThat(Type type) {
        return new TypeAssertion(unwrap(type));
    }

    public TypeAssertion assertThat(TypeDef def) {
        return new TypeAssertion(def);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractTypeSystem that)) return false;
        return Objects.equals(this.typesDef(), that.typesDef());
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.typesDef());
    }

    @SuppressWarnings({"UnusedReturnValue", "unused"})
    public class TypeAssertion {
        protected final TypeDef subject;

        TypeAssertion(TypeDef subject) {
            this.subject = subject;
        }

        public TypeAssertion satisfies(String otherExpression) {
            TypeDef other = typeExpressionDef(otherExpression);
            if (!TestTypeSystem.this.satisfies(subject, other)) {
                throw new AssertionError(String.format("Expected [%s] to satisfy [%s], but it did not.",
                        subject.repr(), other.repr()));
            }
            return this;
        }

        public TypeAssertion isAssignableTo(String otherExpression) {
            OwnedTypeDef other = wrap(typeExpressionDef(otherExpression));
            if (!TestTypeSystem.this.isAssignableTo(wrap(subject), other)) {
                throw new AssertionError(String.format("Expected [%s] to be assignable from [%s], but it was not.",
                        subject.repr(), other.repr()));
            }
            return this;
        }

        public TypeAssertion isNotAssignableFrom(String otherExpression) {
            OwnedTypeDef other = wrap(typeExpressionDef(otherExpression));
            if (TestTypeSystem.this.isAssignableTo(wrap(subject), other)) {
                throw new AssertionError(String.format("Expected [%s] NOT to be assignable from [%s], but it was.",
                        subject.repr(), other.repr()));
            }
            return this;
        }

        public TypeAssertion satisfiedBy(String otherExpression) {
            TypeDef other = typeExpressionDef(otherExpression);
            if (!TestTypeSystem.this.satisfies(other, subject)) {
                throw new AssertionError(String.format("Expected [%s] to be satisfied by [%s], but it was not.",
                        subject.repr(), other.repr()));
            }
            return this;
        }

        public TypeAssertion notSatisfies(String otherExpression) {
            TypeDef other = typeExpressionDef(otherExpression);
            if (TestTypeSystem.this.satisfies(subject, other)) {
                throw new AssertionError(String.format("Expected [%s] NOT to satisfy [%s], but it did.",
                        subject.repr(), other.repr()));
            }
            return this;
        }

        public TypeAssertion notSatisfiedBy(String otherExpression) {
            TypeDef other = typeExpressionDef(otherExpression);
            if (TestTypeSystem.this.satisfies(other, subject)) {
                throw new AssertionError(String.format("Expected [%s] NOT to be satisfied by [%s], but it was.",
                        subject.repr(), other.repr()));
            }
            return this;
        }

        public TypeAssertion isEquivalentTo(String otherExpression) {
            TypeDef other = typeExpressionDef(otherExpression);
            if (!TestTypeSystem.this.isEquivalentTo(wrap(subject), wrap(other))) {
                throw new AssertionError(String.format("\nExpected [%s] to be equivalent to [%s], but it was not.\nSubject satisfies other: %s\nOther satisfies subject: %s",
                        subject.repr(), other.repr(),
                        TestTypeSystem.this.satisfies(subject, other),
                        TestTypeSystem.this.satisfies(other, subject)));
            }
            return this;
        }

        public TypeAssertion isBottom() {
            if (!subject.equals(bottomDef())) {
                throw new AssertionError(String.format("Expected [%s] to be bottom, but it was not.", subject.repr()));
            }
            return this;
        }

        public TypeAssertion term(String segment) {
            return new TypeAssertion(TypeSystemUtils.termAt(TestTypeSystem.this, subject, segment));
        }

        public TypeAssertion term(int index) {
            return new TypeAssertion(TypeSystemUtils.termAt(TestTypeSystem.this, subject, String.valueOf(index)));
        }

        public ReturnTypeAssertion withArgs(String... args) {
            if (!(subject instanceof ApplicableDef func)) {
                throw new AssertionError("Subject is not a function: " + subject.repr());
            }
            List<TypeDef> argTypes = Arrays.stream(args)
                    .map(TestTypeSystem.this::typeExpressionDef)
                    .collect(Collectors.toList());
            return new ReturnTypeAssertion(
                    func.evaluate(TestTypeSystem.this, argTypes),
                    func.overloads(TestTypeSystem.this, argTypes));
        }

        public TypeAssertion evaluatesTo(String otherExpression) {
            return isEquivalentTo(otherExpression);
        }

        public TypeAssertion printsLike(String expected) {
            LexicalAssert.assertStructuralEquals(COVENANT_TOKENIZER, expected, subject.repr());
            return this;
        }

        public TypeAssertion intersect(String s) {
            return new TypeAssertion(TestTypeSystem.this.intersectDef(subject, typeExpressionDef(s)));
        }

        public TypeAssertion concat(String s) {
            return new TypeAssertion(unwrap(TypeUtilities.concatGenericTypes(wrap(subject), typeExpression(s))));
        }

        public TypeAssertion concat(Type.GenericType other) {
            return new TypeAssertion(unwrap(TypeUtilities.concatGenericTypes(wrap(subject), other)));
        }

    }

    public class ReturnTypeAssertion extends TypeAssertion {
        private final List<ApplicableDef.OverloadDef> overloads;

        ReturnTypeAssertion(TypeDef subject, List<ApplicableDef.OverloadDef> overloads) {
            super(subject);
            this.overloads = overloads;
        }

        public ReturnTypeAssertion overloadsTo(String... expectedSignatures) {
            List<String> actualSignatures = overloads.stream()
                    .map(o -> {
                        String params = o.parameters().stream().map(TypeDef::repr).collect(Collectors.joining(", "));
                        return "(" + params + ") -> " + o.returnType().repr();
                    })
                    .toList();

            List<String> expectedList = Arrays.asList(expectedSignatures);

            if (actualSignatures.size() != expectedList.size()) {
                throw new AssertionError(String.format("Expected %d overloads, but found %d.\nExpected: %s\nActual: %s",
                        expectedList.size(), actualSignatures.size(), expectedList, actualSignatures));
            }

            for (int i = 0; i < expectedList.size(); i++) {
                LexicalAssert.assertStructuralEquals(COVENANT_TOKENIZER, expectedList.get(i), actualSignatures.get(i));
            }

            return this;
        }
    }

//    private static void compareIgnoringWhitespace(String expected, String actual) {
//        compareIgnoringWhitespace(null, expected, actual);
//    }
//
//    private static void compareIgnoringWhitespace(String message, String expected, String actual) {
//        String expectedClean = expected.strip().replaceAll("\\s+", " ");
//        String actualClean = actual.strip().replaceAll("\\s+", " ");
//        if (!expectedClean.equals(actualClean)) {
//            String prefix = message == null ? "" : message + ": ";
//            throw new AssertionError(prefix + String.format("Expected [%s] to match [%s] (ignoring whitespace), but it did not.\nExpected (normalized): [%s]\nActual (normalized): [%s]",
//                    expected, actual, expectedClean, actualClean));
//        }
//    }
}
