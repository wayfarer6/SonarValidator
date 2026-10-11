package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonar.sonarvalidator_backend.support.StubRepository.UNHANDLED;
import static org.sonar.sonarvalidator_backend.support.StubRepository.of;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Config.SiteProperties;
import org.sonar.sonarvalidator_backend.Model.dto.ProjectDto;
import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Service.ProjectService;

/**
 * 프로젝트별 <b>제어평면 대역</b> 지정을 검증합니다.
 *
 * <h2>⚠️ 왜 중요한가</h2>
 * <p>제어평면(관리망) 대역은 격리에서 <b>절대 차단 대상이 되어서는 안 됩니다.</b>
 * 그리고 요구사항상 <b>프로젝트마다 다를 수 있습니다.</b> 이 값이 제대로
 * 저장·조회되지 않으면, 격리 경고가 엉뚱한 대역을 인용하거나 전역 기본값으로
 * 조용히 폴백합니다.
 */
class ProjectManagementPrefixTest {

    private final List<Project> rows = new ArrayList<>();
    private ProjectService service;

    @BeforeEach
    void setUp() {
        rows.clear();

        final ProjectRepository repository = of(
                ProjectRepository.class,
                (method, args) -> switch (method) {
                    case "save" -> {
                        final Project project = (Project) args[0];
                        if (!rows.contains(project)) {
                            rows.add(project);
                        }
                        yield project;
                    }
                    case "findByProjectKey" -> rows.stream()
                            .filter(p -> args[0] != null && args[0].equals(p.getProjectKey()))
                            .findFirst();
                    case "findAllByOrderByCreatedAtDesc" -> List.copyOf(rows);
                    case "existsByProjectKey" -> rows.stream()
                            .anyMatch(p -> args[0] != null && args[0].equals(p.getProjectKey()));
                    default -> UNHANDLED;
                });

        service = new ProjectService(repository, null, null);
    }

    @Test
    @DisplayName("생성 시 지정한 제어평면 대역이 저장된다")
    void createStoresManagementPrefix() {
        final Project created = service.create(new ProjectDto.CreateRequest(
                "PRJ-MGMT", "관리망 프로젝트", "Defense", null, "DRAFT", "10.20.0.0/24"));

        assertEquals("10.20.0.0/24", created.getManagementPrefix(), "프로젝트 대역 저장");
    }

    @Test
    @DisplayName("지정하지 않으면 null 로 두고 전역 기본값을 쓴다")
    void createWithoutPrefix() {
        final Project created = service.create(new ProjectDto.CreateRequest(
                "PRJ-DEFAULT", "기본 프로젝트", "Defense", null, "DRAFT", null));

        // ⚠️ 빈 문자열이 아니라 null 이어야 "미지정" 이 분명해집니다.
        //    빈 문자열을 저장하면 설정 해석기가 "지정했지만 빈 값" 으로 봅니다.
        assertNull(created.getManagementPrefix(), "미지정은 null");

        final SiteProperties site = new SiteProperties();
        assertEquals("172.16.255.0/24",
                site.managementPrefixFor(created.getManagementPrefix()),
                "전역 기본값으로 폴백");
    }

    @Test
    @DisplayName("수정 시 null 은 유지, 빈 문자열은 전역 기본값으로 되돌린다")
    void updateNullKeepsEmptyClears() {
        service.create(new ProjectDto.CreateRequest(
                "PRJ-UP", "프로젝트", "Defense", null, "DRAFT", "10.20.0.0/24"));

        // null → 변경 없음 (기존 값 유지)
        final Project kept = service.update("PRJ-UP", new ProjectDto.UpdateRequest(
                null, null, null, null, null, null, null));
        assertEquals("10.20.0.0/24", kept.getManagementPrefix(), "null 이면 유지");

        // 빈 문자열 → 지우기 (전역 기본값으로 폴백)
        final Project cleared = service.update("PRJ-UP", new ProjectDto.UpdateRequest(
                null, null, null, null, "", null, null));
        assertNull(cleared.getManagementPrefix(), "빈 값이면 지움");
    }

    @Test
    @DisplayName("프로젝트 대역이 전역 기본값보다 우선한다")
    void projectPrefixOverridesGlobal() {
        final Project created = service.create(new ProjectDto.CreateRequest(
                "PRJ-WIN", "프로젝트", "Defense", null, "DRAFT", "192.168.10.0/24"));

        final SiteProperties site = new SiteProperties();
        site.setManagementPrefix("172.16.255.0/24");

        final String resolved = site.managementPrefixFor(created.getManagementPrefix());
        assertEquals("192.168.10.0/24", resolved, "프로젝트 지정이 이김");
        assertTrue(site.managementPrefixesFor(resolved).contains("192.168.10.0/24"));
    }

    @Test
    @DisplayName("관리 대역을 응답 맵에 싣는다")
    void responseCarriesPrefix() {
        final Project project = service.create(new ProjectDto.CreateRequest(
                "PRJ-RESP", "프로젝트", "Defense", null, "DRAFT", "10.30.0.0/24"));

        final var body = org.sonar.sonarvalidator_backend.Model.dto.ProjectMapper.toResponse(project);

        assertEquals("10.30.0.0/24", body.get("management_prefix"), "응답에 포함");
    }

    @Test
    @DisplayName("Agent 서버 주소와 포트를 프로젝트에 저장하고 응답한다")
    void updateStoresAgentServerSettings() {
        service.create(new ProjectDto.CreateRequest(
                "PRJ-AGENT", "Agent 프로젝트", "Defense", null, "DRAFT", null));

        final Project saved = service.update("PRJ-AGENT", new ProjectDto.UpdateRequest(
                null, null, null, null, null, "192.168.122.1", 3000, null, null));

        assertEquals("192.168.122.1", saved.getManagementServerIp());
        assertEquals(3000, saved.getManagementServerPort());
        final var body = org.sonar.sonarvalidator_backend.Model.dto.ProjectMapper.toSummary(saved);
        assertEquals("192.168.122.1", body.get("management_server_ip"));
        assertEquals(3000, body.get("management_server_port"));
    }

    @Test
    @DisplayName("Agent 서버 포트는 유효한 TCP 포트만 저장한다")
    void updateRejectsInvalidAgentServerPort() {
        service.create(new ProjectDto.CreateRequest(
                "PRJ-AGENT-PORT", "Agent 프로젝트", "Defense", null, "DRAFT", null));

        final var exception = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.update("PRJ-AGENT-PORT", new ProjectDto.UpdateRequest(
                        null, null, null, null, null, null, 70000, null, null)));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    @DisplayName("없는 프로젝트를 수정하면 예외를 던진다")
    void updateMissingProjectThrows() {
        try {
            service.update("PRJ-NOPE", new ProjectDto.UpdateRequest(
                    null, null, null, null, null, null, null));
            org.junit.jupiter.api.Assertions.fail("예외를 기대했습니다");
        } catch (ProjectService.ProjectNotFoundException expected) {
            assertTrue(expected.getMessage().contains("PRJ-NOPE"), "키 포함");
        }
    }

    /** 스텁 저장소가 Optional 을 돌려주도록 강제하는지 확인용 (미사용 경고 방지). */
    @SuppressWarnings("unused")
    private static Optional<Project> unused() {
        return Optional.empty();
    }
}