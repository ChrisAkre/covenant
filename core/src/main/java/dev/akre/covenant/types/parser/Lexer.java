package dev.akre.covenant.types.parser;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Lexer {
    private static final Map<Parser.TokenType, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put(Parser.TokenType.ARROW, Pattern.compile("^->"));
        PATTERNS.put(Parser.TokenType.ELLIPSIS, Pattern.compile("^\\.\\.\\."));
        PATTERNS.put(Parser.TokenType.FLOAT_LITERAL, Pattern.compile("^-?[0-9]+\\.[0-9]+"));
        PATTERNS.put(Parser.TokenType.INT_LITERAL, Pattern.compile("^-?[0-9]+"));
        PATTERNS.put(Parser.TokenType.STRING_LITERAL, Pattern.compile("^\"([^\"]|\"\")*\""));
        PATTERNS.put(Parser.TokenType.SYMBOL_LITERAL, Pattern.compile("^'([^']|'')*'"));
        PATTERNS.put(Parser.TokenType.IDENTIFIER, Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]*"));

        PATTERNS.put(Parser.TokenType.TILDE, Pattern.compile("^~"));
        PATTERNS.put(Parser.TokenType.PIPE, Pattern.compile("^\\|"));
        PATTERNS.put(Parser.TokenType.AMPERSAND, Pattern.compile("^&"));
        PATTERNS.put(Parser.TokenType.QUESTION, Pattern.compile("^\\?"));
        PATTERNS.put(Parser.TokenType.COLON, Pattern.compile("^:"));
        PATTERNS.put(Parser.TokenType.L_PAREN, Pattern.compile("^\\("));
        PATTERNS.put(Parser.TokenType.R_PAREN, Pattern.compile("^\\)"));
        PATTERNS.put(Parser.TokenType.L_ANGLE, Pattern.compile("^<"));
        PATTERNS.put(Parser.TokenType.R_ANGLE, Pattern.compile("^>"));
        PATTERNS.put(Parser.TokenType.COMMA, Pattern.compile("^,"));
        PATTERNS.put(Parser.TokenType.UNKNOWN, Pattern.compile("^[^\\s]+"));
    }

    public static Parser.InputState tokenize(String input) {
        List<Parser.Token> tokens = new ArrayList<>();
        int pos = 0;
        while (pos < input.length()) {
            if (Character.isWhitespace(input.charAt(pos))) {
                pos++;
                continue;
            }

            boolean matched = false;
            String remaining = input.substring(pos);
            for (Map.Entry<Parser.TokenType, Pattern> entry : PATTERNS.entrySet()) {
                Matcher m = entry.getValue().matcher(remaining);
                if (m.find()) {
                    tokens.add(new Parser.Token(entry.getKey(), m.group(), pos));
                    pos += m.end();
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                throw new IllegalArgumentException("Unexpected character at position " + pos + ": " + input.charAt(pos));
            }
        }
        tokens.add(new Parser.Token(Parser.TokenType.EOF, "", pos));
        return inputState(tokens, 0);
    }

    static Parser.InputState inputState(List<Parser.Token> tokens, int index) {
        return new Parser.InputState() {
            @Override
            public Parser.Token head() {
                return tokens.get(index);
            }

            @Override
            public Parser.InputState tail() {
                return inputState(tokens, index+1);
            }
        };
    }
}
