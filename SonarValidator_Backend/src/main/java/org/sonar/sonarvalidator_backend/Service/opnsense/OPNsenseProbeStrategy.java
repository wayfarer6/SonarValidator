package org.sonar.sonarvalidator_backend.Service.opnsense;

public interface OPNsenseProbeStrategy {

    boolean matches(String normalizedTarget);

    String name();

    String description();

    OPNsenseApiClient.Result probe(OPNsenseApiClient client, OPNsenseConnection connection);
}
