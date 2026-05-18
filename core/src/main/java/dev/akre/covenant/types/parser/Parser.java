package dev.akre.covenant.types.parser;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@FunctionalInterface
public interface Parser<T> {
    Result<T> parse(InputState input);

    interface Result<T> {
        boolean matched();
        InputState remaining();
        T value();
    }

    record Success<T>(T value, InputState remaining) implements Result<T> {
        @Override
        public boolean matched() {
            return true;
        }
    }

    record Failure<T>(String message, InputState remaining) implements Result<T> {
        @Override
        public boolean matched() {
            return false;
        }

        @Override
        public T value() {
            throw new IllegalStateException("Cannot get value from a Failure result: " + message);
        }
    }

    static Parser<Token> ofToken(TokenType type) {
        return input -> {
            Token current = input.head();
            if (current.type() == type) {
                return new Success<>(current, input.tail());
            }
            return new Failure<>("Expected " + type + " but found " + current.type(), input);
        };
    }

    static <T> Parser<List<T>> ofSequence(Parser<T> parser, Parser<?> sep) {
        return input -> {
            List<T> results = new ArrayList<>();
            Result<T> result = parser.parse(input);
            if (!result.matched()) {
                return new Success<>(results, input);
            }
            results.add(result.value());

            while (true) {
                Result<?> sepResult = sep.parse(result.remaining());
                if (!sepResult.matched()) {
                    break;
                }
                Result<T> nextResult = parser.parse(sepResult.remaining());
                if (!nextResult.matched()) {
                    break;
                }
                results.add(nextResult.value());
                result = nextResult;
            }

            return new Success<>(results, result.remaining());
        };
    }

    interface InputState {
        Token head();
        InputState tail();
        boolean isEndOfInput();
    }

    record Token(@NonNull TokenType type, String value, int position) {
        public Token {
            Objects.requireNonNull(type);
        }
    }

    enum TokenType {
        TILDE,      // ~
        PIPE,       // |
        AMPERSAND,  // &
        COLON,      // :
        QUESTION,   // ?
        L_PAREN,    // (
        R_PAREN,    // )
        L_ANGLE,    // <
        R_ANGLE,    // >
        L_BRACKET,  // [
        R_BRACKET,  // ]
        COMMA,      // ,
        ARROW,      // ->
        ELLIPSIS,   // ...

        INT_LITERAL,
        FLOAT_LITERAL,
        STRING_LITERAL,
        REGEX_LITERAL,
        SYMBOL_LITERAL,
        IDENTIFIER,

        UNKNOWN, EOF
    }
}
