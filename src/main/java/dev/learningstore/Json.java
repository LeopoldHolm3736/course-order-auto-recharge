package dev.learningstore;

import java.util.LinkedHashMap;
import java.util.Map;

final class Json {
    private Json() {}

    static String object(Map<String, ?> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (var entry : values.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(quote(entry.getKey())).append(':').append(value(entry.getValue()));
        }
        return out.append('}').toString();
    }

    private static String value(Object value) {
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> strings = new LinkedHashMap<>();
            map.forEach((key, item) -> strings.put(key.toString(), item));
            return object(strings);
        }
        return quote(value.toString());
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.readValue();
        parser.skipSpace();
        if (!parser.atEnd()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }

    private static final class Parser {
        private final String source;
        private int cursor;

        Parser(String source) { this.source = source; }
        boolean atEnd() { return cursor == source.length(); }
        void skipSpace() { while (!atEnd() && Character.isWhitespace(source.charAt(cursor))) cursor++; }

        Object readValue() {
            skipSpace();
            if (atEnd()) throw new IllegalArgumentException("Expected JSON value");
            char next = source.charAt(cursor);
            if (next == '{') return readObject();
            if (next == '"') return readString();
            if (source.startsWith("true", cursor)) { cursor += 4; return true; }
            if (source.startsWith("false", cursor)) { cursor += 5; return false; }
            if (source.startsWith("null", cursor)) { cursor += 4; return null; }
            return readNumber();
        }

        Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            cursor++;
            skipSpace();
            if (!atEnd() && source.charAt(cursor) == '}') { cursor++; return result; }
            while (true) {
                skipSpace();
                String key = readString();
                skipSpace();
                expect(':');
                result.put(key, readValue());
                skipSpace();
                if (!atEnd() && source.charAt(cursor) == '}') { cursor++; return result; }
                expect(',');
            }
        }

        String readString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char ch = source.charAt(cursor++);
                if (ch == '"') return result.toString();
                if (ch == '\\') {
                    if (atEnd()) break;
                    char escaped = source.charAt(cursor++);
                    if (escaped == 'n') result.append('\n');
                    else if (escaped == 'r') result.append('\r');
                    else if (escaped == 't') result.append('\t');
                    else result.append(escaped);
                } else result.append(ch);
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        Number readNumber() {
            int start = cursor;
            while (!atEnd() && "-+.eE0123456789".indexOf(source.charAt(cursor)) >= 0) cursor++;
            String number = source.substring(start, cursor);
            return number.contains(".") || number.contains("e") || number.contains("E")
                    ? Double.parseDouble(number) : Long.parseLong(number);
        }

        void expect(char expected) {
            skipSpace();
            if (atEnd() || source.charAt(cursor++) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "'");
            }
        }
    }
}
