package io.github.openflowkernel.core.support;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FlowExpressionEvaluator {
    private FlowExpressionEvaluator() {
    }

    public static Map<String, String> evaluateMap(
        String expression,
        Map<String, String> flowData,
        Map<String, String> taskData,
        Map<String, String> valueData
    ) {
        if (expression == null || expression.isBlank()) {
            return Map.of();
        }
        Map<String, String> parsed = parseFlatObject(expression);
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parsed.entrySet()) {
            result.put(
                entry.getKey(),
                evaluateValue(entry.getValue(), flowData, taskData, valueData)
            );
        }
        return result;
    }

    private static String evaluateValue(
        String expression,
        Map<String, String> flowData,
        Map<String, String> taskData,
        Map<String, String> valueData
    ) {
        String result = expression;
        result = replacePrefix(result, "flow.", flowData);
        result = replacePrefix(result, "task.", taskData);
        result = replacePrefix(result, "value.", valueData);
        return result;
    }

    private static String replacePrefix(
        String expression,
        String prefix,
        Map<String, String> data
    ) {
        String result = expression;
        int start = result.indexOf("${" + prefix);
        while (start >= 0) {
            int end = result.indexOf('}', start);
            if (end < 0) {
                return result;
            }
            String key = result.substring(start + 2 + prefix.length(), end);
            String replacement = data.getOrDefault(key, "");
            result = result.substring(0, start) + replacement + result.substring(end + 1);
            start = result.indexOf("${" + prefix, start + replacement.length());
        }
        return result;
    }

    private static Map<String, String> parseFlatObject(String expression) {
        String body = expression.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }
        Map<String, String> result = new LinkedHashMap<>();
        int index = 0;
        while (index < body.length()) {
            index = skipWhitespaceAndComma(body, index);
            if (index >= body.length()) {
                break;
            }
            ParsedToken key = readToken(body, index);
            index = skipWhitespace(body, key.nextIndex());
            if (index >= body.length() || body.charAt(index) != ':') {
                throw new IllegalArgumentException("Invalid map expression: " + expression);
            }
            index = skipWhitespace(body, index + 1);
            ParsedToken value = readToken(body, index);
            result.put(key.value(), value.value());
            index = value.nextIndex();
        }
        return result;
    }

    private static int skipWhitespaceAndComma(String value, int index) {
        int current = index;
        while (current < value.length()) {
            char c = value.charAt(current);
            if (!Character.isWhitespace(c) && c != ',') {
                return current;
            }
            current++;
        }
        return current;
    }

    private static int skipWhitespace(String value, int index) {
        int current = index;
        while (current < value.length() && Character.isWhitespace(value.charAt(current))) {
            current++;
        }
        return current;
    }

    private static ParsedToken readToken(String value, int index) {
        if (index < value.length() && value.charAt(index) == '"') {
            return readQuoted(value, index + 1);
        }
        int current = index;
        while (current < value.length()
            && value.charAt(current) != ','
            && value.charAt(current) != ':') {
            current++;
        }
        return new ParsedToken(value.substring(index, current).trim(), current);
    }

    private static ParsedToken readQuoted(String value, int index) {
        StringBuilder result = new StringBuilder();
        int current = index;
        while (current < value.length()) {
            char c = value.charAt(current);
            if (c == '\\' && current + 1 < value.length()) {
                result.append(value.charAt(current + 1));
                current += 2;
                continue;
            }
            if (c == '"') {
                return new ParsedToken(result.toString(), current + 1);
            }
            result.append(c);
            current++;
        }
        throw new IllegalArgumentException("Unclosed quoted string");
    }

    private record ParsedToken(String value, int nextIndex) {
    }
}
