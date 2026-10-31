package org.sonar.sonarvalidator_backend.Policy.strategy;

import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;

public final class PolicyJson {

    public static final int LAB_HOST_OFFSET = 10;

    private PolicyJson() {
    }

    public static String[] splitCidr(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return null;
        }
        final String normalized = PolicySubnet.normalizeCidr(cidr);
        final int slash = normalized.indexOf('/');
        if (slash <= 0 || slash == normalized.length() - 1) {
            return null;
        }
        return new String[] {normalized.substring(0, slash), normalized.substring(slash + 1)};
    }

    public static String maskOf(String prefix) {
        if (prefix == null) {
            return null;
        }
        final int length;
        try {
            length = Integer.parseInt(prefix.trim());
        } catch (RuntimeException ex) {
            return null;
        }
        if (length < 0 || length > 32) {
            return null;
        }
        final long mask = (length == 0) ? 0L : (0xFFFFFFFFL << (32 - length)) & 0xFFFFFFFFL;
        return ((mask >>> 24) & 0xFF) + "." + ((mask >>> 16) & 0xFF) + "."
                + ((mask >>> 8) & 0xFF) + "." + (mask & 0xFF);
    }

    public static String wildcardOf(String prefix) {
        final String mask = maskOf(prefix);
        if (mask == null) {
            return null;
        }
        final StringBuilder result = new StringBuilder();
        for (final String octet : mask.split("\\.")) {
            if (result.length() > 0) {
                result.append('.');
            }
            result.append(255 - Integer.parseInt(octet));
        }
        return result.toString();
    }

    public static String hostCidrOf(String cidr) {
        final String[] parts = splitCidr(cidr);
        if (parts == null) {
            return null;
        }
        final String[] octets = parts[0].split("\\.");
        if (octets.length != 4) {
            return null;
        }
        final int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                values[i] = Integer.parseInt(octets[i]);
            } catch (RuntimeException ex) {
                return null;
            }
            if (values[i] < 0 || values[i] > 255) {
                return null;
            }
        }

        // 네트워크 + 오프셋. 마지막 옥텟에서 자리올림이 생기면 대역을 벗어나므로
        // 그 경우에는 계산을 포기합니다. (틀린 주소를 넣느니 넣지 않습니다)
        final int last = values[3] + LAB_HOST_OFFSET;
        if (last > 254) {
            return null;
        }
        return values[0] + "." + values[1] + "." + values[2] + "." + last + "/" + parts[1];
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public static String label(org.sonar.sonarvalidator_backend.Policy.ZoneClass zone) {
        return zone == null ? "Unknown" : zone.label();
    }
}
