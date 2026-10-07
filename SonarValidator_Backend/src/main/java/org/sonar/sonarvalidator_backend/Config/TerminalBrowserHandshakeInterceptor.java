package org.sonar.sonarvalidator_backend.Config;

import java.util.Map;

import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.springframework.security.core.Authentication;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class TerminalBrowserHandshakeInterceptor implements HandshakeInterceptor {

    private final ExpectedAgentRepository expectedAgents;
    private final ProjectRepository projects;
    private final WebMvcConfig webMvcConfig;

    public TerminalBrowserHandshakeInterceptor(ExpectedAgentRepository expectedAgents,
                                               ProjectRepository projects,
                                               WebMvcConfig webMvcConfig) {
        this.expectedAgents = expectedAgents;
        this.projects = projects;
        this.webMvcConfig = webMvcConfig;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        if (!(request.getPrincipal() instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority())
                                || "ROLE_OPERATOR".equals(authority.getAuthority()))
                || !webMvcConfig.isAllowedOrigin(request.getHeaders().getOrigin())) {
            return false;
        }
        final var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        final String agentId = query.getFirst("agentId");
        final String projectId = query.getFirst("projectId");
        if (agentId == null || projectId == null || !projects.existsByProjectKey(projectId)) {
            return false;
        }
        final boolean belongsToProject = expectedAgents.findByAgentId(agentId)
                .map(agent -> projectId.equals(agent.getProjectKey()))
                .orElse(false);
        if (belongsToProject) {
            attributes.put("agentId", agentId);
            attributes.put("projectId", projectId);
        }
        return belongsToProject;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler handler,
                               Exception exception) {
    }
}
