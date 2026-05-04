package dev.akre.covenant.types.parser;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface Parser<T> {
    Result<T> parse(InputState input);

    default Parser<T> or(Parser<T> other) {
        return input -> {
            Result<T> result = parse(input);
            if (result.matched()) {
                return result;
            }
            return other.parse(input);
        };
    }

    static Parser<Token> ofToken(TokenType type) {
        return input -> {
            Token current = input.head();
            if (current.type() == type) {
                return new Success<>(current, input.tail());
            }
            return new Failure<>("Expected " + type + " but found " + current.type());
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
            InputState state = result.remaining();
            while (true) {
                Result<?> sepResult = sep.parse(state);
                if (!sepResult.matched()) {
                    break;
                }
                Result<T> nextResult = parser.parse(sepResult.remaining());
                if (!nextResult.matched()) {
                    break;
                }
                results.add(nextResult.value());
                state = nextResult.remaining();
            }
            return new Success<>(results, state);
        };
    }

    interface InputState {
        Token head();

        InputState tail();

        default boolean isEndOfInput() {
            return head().type() == TokenType.EOF;
        }
    }

    record Token(TokenType type, String value, int position) {
        @Override
        public @NonNull String toString() {
            return type + "(" + value + ")@" + position;
        }
    }

    sealed interface Result<T> permits Success, Failure {
        boolean matched();
        InputState remaining();
        T value();

    }

    record Failure<T>(String message) implements Result<T> {
        @Override
        public boolean matched() {
            return false;
        }
        @Override
        public InputState remaining() {
            throw new IllegalStateException("Did not consumer tokens from a Failure result: " + message);
        }

        @Override
        public T value() {
            throw new IllegalStateException("Cannot get value from a Failure result: " + message);
        }
    }

    record Success<T>(T value, InputState remaining) implements Result<T> {
    @Override
        public boolean matched() {
            return true;
        }
    }

    enum TokenType {
        IDENTIFIER,
        STRING_LITERAL,
        SYMBOL_LITERAL,
        INT_LITERAL,
        FLOAT_LITERAL,

        TILDE,      // ~
        PIPE,       // |
        AMPERSAND,  // &
        QUESTION,   // ?
        COLON,      // :
        L_PAREN,    // (
        R_PAREN,    // )
        L_ANGLE,    // <
        R_ANGLE,    // >
        COMMA,      // ,
        ARROW,      // ->
        ELLIPSIS,   // ...

        UNKNOWN, EOF
    }
}
