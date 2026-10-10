import java.nio.file.*;
import java.util.*;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import tools.jackson.databind.ObjectMapper;

// Run with the backend working directory, exactly like ./mvnw spring-boot:run.
class CheckTerminalConfig {
    public static void main(String[] args) throws Exception {
        final var environment = new StandardEnvironment();
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        final String backend = environment.getProperty("sonar.terminal.shared-secret", "").trim();
        final String agent = Files.readAllLines(Path.of(args[0])).stream()
                .filter(s -> s.startsWith("TERMINAL_SHARED_SECRET="))
                .map(s -> s.substring(s.indexOf('=') + 1).split(";", 2)[0].trim()).findFirst().orElse("");
        final var report = Map.of("backend_key_configured", backend.length() >= 32,
                "agent_key_configured", agent.length() >= 32, "keys_match", backend.equals(agent),
                "backend_key_length", backend.length(), "agent_key_length", agent.length());
        final String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report);
        Files.writeString(Path.of(args[1]), json + "\n");
        System.out.println(json);
        if (backend.length() < 32 || !backend.equals(agent)) System.exit(1);
    }
}
