package org.sonar.sonarvalidator_backend.Service.cli;

public record CliIngestRequest(String product, String target, String raw) {

    public static CliIngestRequest empty() {
        return new CliIngestRequest(null, null, null);
    }

    public boolean hasRaw() {
        return raw != null && !raw.isBlank();
    }
}
