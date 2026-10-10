import java.nio.file.*;
import java.util.*;
import org.sonar.sonarvalidator_backend.Service.opnsense.*;
import tools.jackson.databind.ObjectMapper;

// Run against compiled backend classes and the Maven runtime classpath.
// Reports contain summaries only; credentials come from the environment.
class OpnsenseProbe {
    public static void main(String[] args) throws Exception {
        final String origin = System.getenv().getOrDefault("SONAR_RVI_OPNSENSE_URL", "http://10.20.0.2");
        final var connection = new OPNsenseConnection(origin, System.getenv("SONAR_RVI_OPNSENSE_KEY"),
                System.getenv("SONAR_RVI_OPNSENSE_SECRET"), false);
        final var mapper = new ObjectMapper();
        final var client = new OPNsenseApiClient(mapper, new OPNsenseTransportPolicy(origin));
        final var reports = new ArrayList<Map<String, Object>>();
        for (String target : List.of("firmware", "interfaces", "rules", "nat", "aliases")) {
            final var result = switch (target) {
                case "interfaces" -> client.fetchInterfaces(connection);
                case "rules" -> client.fetchFirewallRules(connection);
                case "nat" -> client.fetchNatRules(connection);
                case "aliases" -> client.fetchAliases(connection);
                default -> client.checkConnection(connection);
            };
            final var report = new LinkedHashMap<String, Object>();
            report.put("target", target);
            report.put("ok", result.ok());
            report.put("status", result.statusCode());
            report.put("summary", client.summarize(result.body()));
            if (!result.ok()) report.put("error", result.error());
            reports.add(report);
            System.out.println(mapper.writeValueAsString(report));
        }
        Files.writeString(Path.of(args.length > 0 ? args[0] : "opnsense-backend-probe.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(reports) + "\n");
        if (reports.stream().anyMatch(r -> !Boolean.TRUE.equals(r.get("ok")))) System.exit(1);
    }
}
