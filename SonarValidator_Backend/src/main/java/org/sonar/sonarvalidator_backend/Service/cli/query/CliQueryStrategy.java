package org.sonar.sonarvalidator_backend.Service.cli.query;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

public interface CliQueryStrategy {

    boolean matches(String normalizedTarget);

    String primaryTarget();

    String contractKey();

    tools.jackson.databind.node.ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw);
}
