package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.ArrayList;
import java.util.List;

public final class CliText {

    private CliText() {
    }

    public static String trim(String text) {
        return text == null ? "" : text.strip();
    }

    public static String trimPunct(String text) {
        if (text == null) {
            return "";
        }
        int end = text.length();
        while (end > 0) {
            final char ch = text.charAt(end - 1);
            if (ch == ',' || ch == ';' || ch == ')') {
                end--;
            } else {
                break;
            }
        }
        return text.substring(0, end);
    }

    public static String unquote(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    public static boolean isNumber(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean splitCidr(String value, String[] address, int[] prefixLen) {
        if (value == null) {
            return false;
        }
        final int slash = value.indexOf('/');
        if (slash < 0) {
            address[0] = value;
            prefixLen[0] = -1;
            return !value.isEmpty();
        }
        address[0] = value.substring(0, slash);
        final String prefix = value.substring(slash + 1);
        prefixLen[0] = isNumber(prefix) ? Integer.parseInt(prefix) : -1;
        return !address[0].isEmpty();
    }

    public static String dottedMacToColon(String token) {
        final String value = trimPunct(token);
        final StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (Character.digit(ch, 16) >= 0) {
                digits.append(Character.toLowerCase(ch));
            }
        }
        if (digits.length() != 12) {
            return value;
        }

        final StringBuilder normalized = new StringBuilder(17);
        for (int i = 0; i < digits.length(); i++) {
            if (i != 0 && i % 2 == 0) {
                normalized.append(':');
            }
            normalized.append(digits.charAt(i));
        }
        return normalized.toString();
    }

    public static List<String> splitTokens(String line) {
        final List<String> tokens = new ArrayList<>();
        if (line == null) {
            return tokens;
        }
        for (final String token : line.strip().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
