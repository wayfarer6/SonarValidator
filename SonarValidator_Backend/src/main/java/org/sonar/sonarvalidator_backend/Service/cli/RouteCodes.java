package org.sonar.sonarvalidator_backend.Service.cli;

public final class RouteCodes {

    private RouteCodes() {
    }

    public static String normalize(String code) {
        if (code == null || code.isEmpty()) {
            return "unknown";
        }
        return switch (code.charAt(0)) {
            case 'K' -> "kernel";
            case 'C' -> "connected";
            case 'S' -> "static";
            case 'R' -> "rip";
            case 'O' -> "ospf";
            case 'I' -> "isis";
            case 'B' -> "bgp";
            case 'E', 'D' -> "eigrp";
            case 'N', 'H', 'G' -> "nhrp";
            case 'A' -> "babel";
            case 'L' -> "local";
            case 'M' -> "mobile";
            case 'P' -> "periodic";
            case 'U' -> "per-user";
            case 'T' -> "table";
            default -> String.valueOf(Character.toLowerCase(code.charAt(0)));
        };
    }

    public static boolean isRouteCodeToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            final char ch = token.charAt(i);
            if (!Character.isLetter(ch) && ch != '*' && ch != '<' && ch != '>' && ch != '&') {
                return false;
            }
        }
        return true;
    }

    public static boolean isDestinationToken(String token) {
        return token != null && token.indexOf('/') >= 0 && token.matches(".*[0-9].*");
    }
}
