package org.sonar.sonarvalidator_backend.Controller;
import java.util.*;
import org.sonar.sonarvalidator_backend.Service.policy.AdviceExecutionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/policy/advice/{projectId}/{adviceId}/executions")
public class AdviceExecutionController {
    private final AdviceExecutionService service;
    public AdviceExecutionController(AdviceExecutionService service) { this.service=service; }
    @PostMapping public Map<String,Object> execute(@PathVariable String projectId,@PathVariable String adviceId,@RequestBody JsonNode body,Authentication auth) {
        PolicyAdviceController.requireOperator(auth);
        if(!body.path("confirmed").isBoolean() || !body.path("confirmed").asBoolean() || !body.path("proposal_index").isIntegralNumber() || !body.path("proposal_index").canConvertToInt() || !body.path("request_id").isString())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"명령 검토 확인과 유효한 실행 요청이 필요합니다.");
        return service.execute(projectId,adviceId,body.path("proposal_index").asInt(),body.path("request_id").asString(),auth.getName());
    }
    @GetMapping public Map<String,Object> history(@PathVariable String projectId,@PathVariable String adviceId,Authentication auth) {
        PolicyAdviceController.requireOperator(auth);return Map.of("executions",service.history(projectId,adviceId));
    }
    @GetMapping("/{executionId}") public Map<String,Object> get(@PathVariable String projectId,@PathVariable String adviceId,@PathVariable String executionId,Authentication auth) {
        PolicyAdviceController.requireOperator(auth);return service.get(projectId,adviceId,executionId);
    }
}
