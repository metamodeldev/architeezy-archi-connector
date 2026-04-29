/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.api;

/**
 * Minimal JSON-object utilities used by the response parsers.
 *
 * <p>Spring Data REST renders entity references (scope, project, creator,
 * lastModifier, ...) as nested JSON objects whose fields collide with
 * top-level keys (id, name, slug). A naive {@code indexOf}-based extractor
 * would grab the first occurrence - which is now inside a nested object - so
 * code that wants top-level scalars walks only depth-1 entries.
 */
final class JsonObjects {

    private JsonObjects() {
    }

    /**
     * Returns the unquoted string value for the given top-level key.
     *
     * @param json the JSON object body to search
     * @param key the field name
     * @return the unquoted string value, or {@code null} if missing or non-string
     */
    static String extractTopLevelString(String json, String key) {
        final var raw = extractTopLevelValue(json, key);
        if (raw == null || raw.length() < 2 || raw.charAt(0) != '"') {
            return null;
        }
        return raw.substring(1, raw.length() - 1);
    }

    /**
     * Returns the substring representing a top-level nested object value
     * (including the surrounding braces).
     *
     * @param json the JSON object body to search
     * @param key the field name
     * @return the object substring, or {@code null} if missing or not an object
     */
    static String extractTopLevelObject(String json, String key) {
        final var raw = extractTopLevelValue(json, key);
        if (raw == null || raw.isEmpty() || raw.charAt(0) != '{') {
            return null;
        }
        return raw;
    }

    /**
     * Walks the depth-1 entries of the JSON object and returns the raw
     * substring of the value associated with {@code key}: with quotes for
     * strings, with braces for objects, with brackets for arrays.
     *
     * @param json the JSON object body to search
     * @param key the field name
     * @return the raw value substring, or {@code null} if not found
     */
    static String extractTopLevelValue(String json, String key) {
        final var objStart = json.indexOf('{');
        if (objStart < 0) {
            return null;
        }
        final var objEnd = findMatchingBracket(json, objStart, '{', '}');
        return objEnd < 0 ? null : scanEntries(json, key, objStart + 1, objEnd);
    }

    private static String scanEntries(String json, String key, int from, int limit) {
        var i = from;
        while (i < limit) {
            i = skipWhitespace(json, i, limit);
            if (i >= limit || json.charAt(i) != '"') {
                i++;
                continue;
            }
            final var entry = nextEntry(json, i, limit);
            if (entry == null) {
                return null;
            }
            if (keyMatches(json, i, key, entry)) {
                return json.substring(entry.valStart(), entry.valEnd());
            }
            i = afterComma(json, entry.valEnd(), limit);
        }
        return null;
    }

    private static boolean keyMatches(String json, int keyStart, String key, Entry entry) {
        return key.length() == entry.keyLen()
                && json.regionMatches(keyStart + 1, key, 0, entry.keyLen());
    }

    private static int afterComma(String json, int from, int limit) {
        var i = skipWhitespace(json, from, limit);
        if (i < limit && json.charAt(i) == ',') {
            i++;
        }
        return i;
    }

    // Reads the entry whose opening quote sits at keyStart; returns its key
    // length plus the value's substring range, or null on malformed input.
    private static Entry nextEntry(String json, int keyStart, int limit) {
        final var keyEnd = findClosingQuote(json, keyStart + 1);
        if (keyEnd < 0 || keyEnd >= limit) {
            return null;
        }
        final var afterKey = skipWhitespace(json, keyEnd + 1, limit);
        if (afterKey >= limit || json.charAt(afterKey) != ':') {
            return null;
        }
        final var valStart = skipWhitespace(json, afterKey + 1, limit);
        if (valStart >= limit) {
            return null;
        }
        final var valEnd = findValueEnd(json, valStart, limit);
        return valEnd < 0 ? null : new Entry(keyEnd - (keyStart + 1), valStart, valEnd);
    }

    private static int findValueEnd(String json, int valStart, int limit) {
        final var vc = json.charAt(valStart);
        if (vc == '"') {
            final var qe = findClosingQuote(json, valStart + 1);
            return qe < 0 ? -1 : qe + 1;
        }
        if (vc == '{') {
            final var matched = findMatchingBracket(json, valStart, '{', '}');
            return matched < 0 ? -1 : matched + 1;
        }
        if (vc == '[') {
            final var matched = findMatchingBracket(json, valStart, '[', ']');
            return matched < 0 ? -1 : matched + 1;
        }
        var end = valStart;
        while (end < limit && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        return end;
    }

    private static int skipWhitespace(String s, int start, int limit) {
        var i = start;
        while (i < limit && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int findClosingQuote(String s, int start) {
        var i = start;
        while (i < s.length()) {
            final var c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') {
                return i;
            }
            i++;
        }
        return -1;
    }

    /**
     * Finds the closing bracket matching the opening bracket at {@code start},
     * honoring quoted strings so brackets inside string values do not affect
     * the depth count.
     *
     * @param s the string to search
     * @param start index of the opening bracket
     * @param open the opening bracket character
     * @param close the closing bracket character
     * @return index of the matching closing bracket, or {@code -1} if not found
     */
    static int findMatchingBracket(String s, int start, char open, char close) {
        var depth = 0;
        var inString = false;
        for (int i = start; i < s.length(); i++) {
            final var c = s.charAt(i);
            if (isQuoteToggle(s, i, c)) {
                inString = !inString;
            }
            if (!inString) {
                depth += bracketDelta(c, open, close);
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isQuoteToggle(String s, int i, char c) {
        return c == '"' && (i == 0 || s.charAt(i - 1) != '\\');
    }

    private static int bracketDelta(char c, char open, char close) {
        if (c == open) {
            return 1;
        }
        if (c == close) {
            return -1;
        }
        return 0;
    }

    private record Entry(int keyLen, int valStart, int valEnd) {
    }

}
