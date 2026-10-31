package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectDto;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ProjectDtoSerializationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("서브넷 본문의 snake_case 필드가 모두 파싱된다")
    void subnetPayloadParsesSnakeCase() {
        final String json = """
                {
                  "id": "Subnet-0004",
                  "cidr": "10.10.131.0/24",
                  "subnet_class": "Confidential",
                  "name": "기밀망",
                  "agent_id": "agent-1",
                  "manually_edited": true
                }
                """;

        final ProjectDto.SubnetPayload payload =
                objectMapper.readValue(json, ProjectDto.SubnetPayload.class);

        assertThat(payload.id()).isEqualTo("Subnet-0004");
        assertThat(payload.cidr()).isEqualTo("10.10.131.0/24");
        // 이 단언이 핵심입니다. 매핑이 깨지면 null 이 되어 Open 으로 강등됩니다.
        assertThat(payload.subnetClass()).isEqualTo("Confidential");
        assertThat(payload.name()).isEqualTo("기밀망");
        assertThat(payload.agentId()).isEqualTo("agent-1");
        assertThat(payload.manuallyEdited()).isTrue();

        // 도메인 객체로 변환했을 때 등급이 유지되어야 합니다.
        assertThat(payload.toPolicySubnet().getZoneClass()).isEqualTo(ZoneClass.CONFIDENTIAL);
    }

    @Test
    @DisplayName("생략된 선택 필드는 기본값으로 안전하게 채워진다")
    void omittedOptionalFieldsGetDefaults() {
        // manually_edited 와 agent_id 를 뺀 최소 본문
        final String json = """
                {"id":"Subnet-0001","cidr":"10.0.0.0/24","subnet_class":"Open"}
                """;

        final ProjectDto.SubnetPayload payload =
                objectMapper.readValue(json, ProjectDto.SubnetPayload.class);

        // primitive boolean 이었다면 여기서 Cannot map null into boolean 으로 실패합니다.
        assertThat(payload.manuallyEdited()).isNull();
        assertThat(payload.toPolicySubnet().isManuallyEdited()).isFalse();
        assertThat(payload.toPolicySubnet().getZoneClass()).isEqualTo(ZoneClass.OPEN);
    }

    @Test
    @DisplayName("규칙 본문의 필드와 기본값이 올바르다")
    void rulePayloadParses() {
        final String json = """
                {
                  "id": "Rule-0002",
                  "src": "Subnet-0004",
                  "dst": "Subnet-0001",
                  "port": 443,
                  "protocol": "tcp",
                  "origin": "DISCOVERED",
                  "enabled": false,
                  "note": "검토 필요"
                }
                """;

        final ProjectDto.RulePayload payload =
                objectMapper.readValue(json, ProjectDto.RulePayload.class);

        assertThat(payload.id()).isEqualTo("Rule-0002");
        assertThat(payload.src()).isEqualTo("Subnet-0004");
        assertThat(payload.dst()).isEqualTo("Subnet-0001");
        assertThat(payload.port()).isEqualTo(443);

        final var rule = payload.toPolicyRule();
        assertThat(rule.getSource()).isEqualTo("Subnet-0004");
        assertThat(rule.getDestination()).isEqualTo("Subnet-0001");
        assertThat(rule.getPort()).isEqualTo(443);
        assertThat(rule.isEnabled()).isFalse();
        assertThat(rule.getOrigin())
                .isEqualTo(org.sonar.sonarvalidator_backend.Policy.PolicyRule.Origin.DISCOVERED);
    }

    @Test
    @DisplayName("포트가 없는 규칙은 전체 포트 허용으로 해석된다")
    void ruleWithoutPortMeansAnyPort() {
        final String json = """
                {"id":"Rule-0009","src":"Subnet-0001","dst":"Subnet-0002"}
                """;

        final ProjectDto.RulePayload payload =
                objectMapper.readValue(json, ProjectDto.RulePayload.class);

        assertThat(payload.port()).isNull();
        final var rule = payload.toPolicyRule();
        assertThat(rule.hasPort()).isFalse();
        // enabled 를 생략하면 검증에 포함되는 것이 기본입니다.
        assertThat(rule.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("프로젝트 생성 본문의 project_id 가 파싱된다")
    void createRequestParsesProjectId() {
        final String json = """
                {"project_id":"PRJ-CUSTOM01","name":"Bank Net","category":"Finance"}
                """;

        final ProjectDto.CreateRequest request =
                objectMapper.readValue(json, ProjectDto.CreateRequest.class);

        assertThat(request.projectId()).isEqualTo("PRJ-CUSTOM01");
        assertThat(request.name()).isEqualTo("Bank Net");
        assertThat(request.category()).isEqualTo("Finance");
    }

    @Test
    @DisplayName("수정 본문에서 snake_case 목록이 파싱된다")
    void updateRequestParsesNestedLists() {
        final String json = """
                {
                  "name":"갱신된 프로젝트",
                  "subnets":[
                    {"id":"Subnet-0001","cidr":"10.0.0.0/24","subnet_class":"Open","manually_edited":true},
                    {"id":"Subnet-0002","cidr":"10.1.0.0/24","subnet_class":"Confidential"}
                  ],
                  "rules":[
                    {"id":"Rule-0001","src":"Subnet-0002","dst":"Subnet-0001","port":443}
                  ]
                }
                """;

        final ProjectDto.UpdateRequest request =
                objectMapper.readValue(json, ProjectDto.UpdateRequest.class);

        assertThat(request.name()).isEqualTo("갱신된 프로젝트");
        assertThat(request.subnets()).hasSize(2);
        assertThat(request.rules()).hasSize(1);

        // 두 번째 서브넷의 등급이 살아 있어야 합니다. (여기가 깨지면 위반이 안 잡힘)
        assertThat(request.subnets().get(1).toPolicySubnet().getZoneClass())
                .isEqualTo(ZoneClass.CONFIDENTIAL);
        assertThat(request.subnets().get(1).toPolicySubnet().isManuallyEdited()).isFalse();
    }
}
