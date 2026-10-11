package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Config.TerminalBrowserHandshakeInterceptor;
import org.sonar.sonarvalidator_backend.Config.WebMvcConfig;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;

class TerminalBrowserHandshakeInterceptorTest {

    private ExpectedAgentRepository expectedAgents;
    private ProjectRepository projects;
    private WebMvcConfig webMvcConfig;
    private TerminalBrowserHandshakeInterceptor interceptor;
    private ServerHttpRequest request;
    private ServerHttpResponse response;
    private WebSocketHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        expectedAgents = mock(ExpectedAgentRepository.class);
        projects = mock(ProjectRepository.class);
        webMvcConfig = mock(WebMvcConfig.class);
        interceptor = new TerminalBrowserHandshakeInterceptor(expectedAgents, projects, webMvcConfig);
        request = mock(ServerHttpRequest.class);
        response = mock(ServerHttpResponse.class);
        handler = mock(WebSocketHandler.class);
        when(request.getURI()).thenReturn(new URI(
                "ws://localhost/api/v1/terminal/browser?projectId=project-a&agentId=agent-1"));
        final HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        when(request.getHeaders()).thenReturn(headers);
        when(webMvcConfig.isAllowedOrigin("http://localhost:5173")).thenReturn(true);
        final ExpectedAgent expectedAgent = new ExpectedAgent();
        expectedAgent.setAgentId("agent-1");
        expectedAgent.setProjectKey("project-a");
        when(expectedAgents.findByAgentId("agent-1")).thenReturn(Optional.of(expectedAgent));
        when(projects.existsByProjectKey("project-a")).thenReturn(true);
    }

    @Test
    void allowsOperatorForAgentInProject() {
        when(request.getPrincipal()).thenReturn(
                new TestingAuthenticationToken("operator", "password", "ROLE_OPERATOR"));

        assertTrue(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
    }

    @Test
    void allowsOperatorFromAlternateLocalFrontendPort() {
        when(request.getPrincipal()).thenReturn(
                new TestingAuthenticationToken("operator", "password", "ROLE_OPERATOR"));
        final HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5174");
        when(request.getHeaders()).thenReturn(headers);
        when(webMvcConfig.isAllowedOrigin("http://localhost:5174")).thenReturn(true);

        assertTrue(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
    }

    @Test
    void rejectsViewerEvenWhenAgentBelongsToProject() {
        when(request.getPrincipal()).thenReturn(
                new TestingAuthenticationToken("viewer", "password", "ROLE_VIEWER"));

        assertFalse(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
    }

    @Test
    void rejectsAgentFromAnotherProject() throws Exception {
        when(request.getPrincipal()).thenReturn(
                new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN"));
        when(request.getURI()).thenReturn(new URI(
                "ws://localhost/api/v1/terminal/browser?projectId=project-b&agentId=agent-1"));
        when(projects.existsByProjectKey("project-b")).thenReturn(true);

        assertFalse(interceptor.beforeHandshake(request, response, handler, new HashMap<>()));
    }
}
