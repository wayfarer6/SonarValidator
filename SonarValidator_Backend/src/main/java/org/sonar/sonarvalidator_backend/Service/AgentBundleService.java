package org.sonar.sonarvalidator_backend.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.sonar.sonarvalidator_backend.Repository.ProjectRepository;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;

@Service
public class AgentBundleService {
    private static final Logger log = LoggerFactory.getLogger(AgentBundleService.class);
    private static final String BUNDLE_ROOT = "Installer";
    private final String stageDirectory;
    private final String terminalSharedSecret;
    private final ExpectedAgentRepository expectedAgents;
    private final ProjectRepository projects;

    public AgentBundleService(@Value("${sonar.deploy.stage-dir:/tmp/sonar_stage}") String stageDirectory,
            @Value("${sonar.terminal.shared-secret:}") String terminalSharedSecret,
            ExpectedAgentRepository expectedAgents, ProjectRepository projects) {
        this.stageDirectory = stageDirectory;
        this.terminalSharedSecret = terminalSharedSecret;
        this.expectedAgents = expectedAgents;
        this.projects = projects;
    }

    private record BundleSettings(String ip, int port, String nodeType, String projectId, String managementPrefix) {}

    private BundleSettings settings(String agentId, String requestedType, String projectId) {
        final var agent = expectedAgents.findByAgentId(agentId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "먼저 프로젝트에 Agent를 등록하세요."));
        if (projectId == null || !projectId.equals(agent.getProjectKey())) throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Agent가 선택한 프로젝트에 소속되어 있지 않습니다.");
        final var project = projects.findByProjectKey(projectId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Agent의 소속 프로젝트를 확인하세요."));
        final String ip = ManagementServerAddress.normalize(project.getManagementServerIp());
        final Integer port = project.getManagementServerPort();
        if (ip == null ||
                port == null || port < 1 || port > 65535) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "프로젝트 편집에서 관리 서버 주소와 포트를 저장하세요.");
        }
        final String configuredType = agent.getNodeType() == null ? agent.getDeviceType() : agent.getNodeType();
        final AgentNodeType type = java.util.Arrays.stream(AgentNodeType.values()).filter(t -> t.matches(configuredType))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "등록된 장비 유형을 확인하세요."));
        if (requestedType != null && !requestedType.isBlank() && !type.matches(requestedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택한 유형이 등록된 Agent 유형과 다릅니다.");
        }
        if (agentId == null || !agentId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent 이름에 사용할 수 없는 문자가 있습니다.");
        }
        final String prefix = project.getManagementPrefix() == null ? "" : project.getManagementPrefix().trim();
        for (String value : prefix.split(",")) {
            if (!value.isBlank()) PacketVariables.parseCidr(value.trim());
        }
        return new BundleSettings(ip, port, type.canonical(), project.getProjectKey(), prefix);
    }

    public byte[] build(String agentId, String nodeType, String projectId, String dataDirectory) {
        if (terminalSharedSecret == null || terminalSharedSecret.trim().length() < 32) {
            throw new IllegalStateException("sonar.terminal.shared-secret must contain at least 32 characters");
        }
        if (dataDirectory != null && (dataDirectory.contains(";") || dataDirectory.contains("\n") || dataDirectory.contains("\r"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "데이터 경로에 사용할 수 없는 문자가 있습니다.");
        }
        final BundleSettings resolved = settings(agentId, nodeType, projectId);
        for (String name : List.of("sonar_validator_prober", "default_template.sqlite")) {
            if (!Files.isReadable(Path.of(stageDirectory, name))) throw new IllegalStateException("Required Agent asset is missing: " + name);
        }
        try {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(buffer);
                 TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
                put(tar, "default.conf", defaultConf(resolved.ip(), resolved.port(), resolved.nodeType(), agentId,
                    dataDirectory, terminalSharedSecret) + "MANAGEMENT_PREFIX=" + resolved.managementPrefix() + ";\n");
                put(tar, "README.txt", readme(agentId, resolved.nodeType(), resolved.ip(), resolved.port()));
                putFile(tar, "sonar_validator_prober");
                putFile(tar, "default_template.sqlite");
                try (var notice = AgentBundleService.class.getResourceAsStream("/agent-licenses/antlr4-runtime-LICENSE.txt")) {
                    if (notice == null) throw new IOException("ANTLR license resource is missing");
                    putBytes(tar, "licenses/antlr4-runtime-LICENSE.txt", notice.readAllBytes());
                }
                for (String name : List.of("Installer.sh", "restart.sh", "sonar_cli_bridge.tcl", "systemd/sonar_validator_prober.service",
                        "systemd/sonar_validator_prober-cisco.service", "rc-service/sonar_validator_prober")) putOptionalFile(tar, name);
                tar.finish();
            }
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Agent bundle generation failed", ex);
        }
    }

    public String fileNameFor(String agentId) {
        final String safe = (agentId == null ? "agent" : agentId)
                .trim()
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return "sonar-agent-" + safe + ".tar.gz";
    }

    private String defaultConf(String ip, int port, String nodeType, String agentId,
                               String dataDirectory, String terminalSharedSecret) {
        final StringBuilder text = new StringBuilder();
        text.append("# SonarValidator Agent 설정\n")
                .append("# 이 파일은 서버가 생성했습니다. 손으로 고치지 마세요.\n")
                .append("# (고치면 배포 예정 등록 이름과 어긋나 같은 장치가 두 줄로 보입니다)\n")
                .append("#\n")
                .append("# NODE_TYPE 은 Router / Switch / VM / Firewall 네 가지만 있습니다.\n\n");
        text.append("SERVER_IP=").append(ip).append(";\n");
        text.append("SERVER_PORT=").append(port).append(";\n");
        text.append("NODE_TYPE=").append(nodeType).append(";\n");
        // ⚠️ AGENT_NAME 이 핵심입니다. 배포 예정 등록 이름과 같아야
        //    "예정" 과 "연결" 이 한 줄로 합쳐집니다.
        text.append("AGENT_NAME=").append(agentId).append(";\n");
        if (terminalSharedSecret != null && !terminalSharedSecret.isBlank()) {
            text.append("TERMINAL_SHARED_SECRET=").append(terminalSharedSecret.trim()).append(";\n");
        }

        final String dir = blankToNull(dataDirectory);
        if (dir != null) {
            // 장치마다 쓸 수 있는 경로가 다릅니다 (예: Arista /mnt/flash).
            text.append("DATA_DIRECTORY=").append(dir).append(";\n");
        }
        return text.toString();
    }

    private String readme(String agentId, String nodeType, String ip, int port) {
        return """
                SonarValidator Prober
                Agent: %s
                Type: %s
                Project server: %s:%d

                서버 주소와 포트는 프로젝트 편집에서 저장한 값입니다.
                주소를 바꿨다면 새 번들을 다운로드하고 기존 Prober를 정상 종료한 뒤 갱신하세요.

                1. SSH/SCP로 번들을 장비의 쓰기 가능한 경로에 전송합니다.
                2. tar -xzf sonar-agent-%s.tar.gz
                   cd Installer
                3. Linux systemd/OpenRC 신규 설치:
                   sudo sh Installer.sh
                4. 수동 실행 또는 init 없는 컨테이너:
                   chmod 600 default.conf
                   mkdir -p data
                   SONAR_CONFIG_PATH="$PWD/default.conf" \
                   SONAR_TEMPLATE_PATH="$PWD/default_template.sqlite" \
                   SONAR_DATA_DIR="$PWD/data" ./sonar_validator_prober

                바이너리, default.conf, SQLite 템플릿을 같은 Installer 디렉터리에서 사용합니다.
                기존 DB를 삭제하지 마세요. 갱신 전 기존 프로세스에 SIGTERM을 보내고
                종료를 기다린 뒤 바이너리를 교체합니다. 같은 Agent를 두 개 실행하지 마세요.
                초기 수집 주기는 30초이며 서버에 저장한 노드별 설정이 이후 반영됩니다.

                Cisco GuestShell/EOS/GNS3 컨테이너는 해당 장비의 영구 저장 경로와
                기존 EEM·부팅 훅을 사용하세요. OPNsense는 Prober 대신 API 연결을 사용합니다.
                Cisco의 dohost가 동작하지 않으면 sonar_cli_bridge.tcl을 GuestShell의
                /bootflash/guest-share/sonar-cli/에 복사하고 IOS에서 다음을 등록하세요.
                기존 EEM user policy 디렉터리가 있으면 그 경로를 보존하세요.
                  event manager directory user policy bootflash:/guest-share/sonar-cli
                  event manager policy sonar_cli_bridge.tcl type user authorization bypass
                이 경로는 관리자용 공유 폴더이며 일반 사용자에게 쓰기 권한을 주지 마세요.
                격리/해제는 요청만으로 성공하지 않으며 장비 상태 재조회 후 확인됩니다.
                default.conf에는 인증 비밀값이 있으므로 공개하거나 녹화 화면에 출력하지 마세요.
                """.formatted(agentId, nodeType, ip, port, agentId);
    }

    public static String normalizeNodeType(String nodeType) {
        return AgentNodeType.canonicalOf(nodeType);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static void put(TarArchiveOutputStream tar, String name, String body)
            throws IOException {
        putBytes(tar, name, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void putBytes(TarArchiveOutputStream tar, String name, byte[] content)
            throws IOException {
        final boolean executable = name.endsWith(".sh") || name.equals("sonar_validator_prober")
                || name.equals("rc-service/sonar_validator_prober");
        final TarArchiveEntry entry = new TarArchiveEntry(BUNDLE_ROOT + "/" + name);
        entry.setSize(content.length);
        entry.setMode(name.equals("default.conf") ? 0600 : executable ? 0755 : 0644);
        entry.setModTime(System.currentTimeMillis());
        tar.putArchiveEntry(entry);
        tar.write(content);
        tar.closeArchiveEntry();
    }

    private void putOptionalFile(TarArchiveOutputStream tar, String name) throws IOException {
        final Path source = Path.of(stageDirectory, name);
        if (Files.isRegularFile(source)) {
            putBytes(tar, name, Files.readAllBytes(source));
        }
    }

    private void putFile(TarArchiveOutputStream tar, String name) throws IOException {
        final Path source = Path.of(stageDirectory, name);
        if (!Files.isRegularFile(source)) {
            throw new IOException("Required bundle asset is missing: " + source);
        }
        putBytes(tar, name, Files.readAllBytes(source));
    }

    public Map<String, Object> describe(String agentId, String nodeType, String projectId) {
        final BundleSettings resolved = settings(agentId, nodeType, projectId);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agentId);
        body.put("node_type", resolved.nodeType());
        body.put("project_id", resolved.projectId());
        body.put("server_ip", resolved.ip());
        body.put("server_port", resolved.port());
        body.put("file_name", fileNameFor(agentId));
        // 번들 형식입니다. 화면이 "ZIP" 이라고 안내하면 tar.gz 를 풀 수 없습니다.
        body.put("archive_format", "tar.gz");
        body.put("installer_dir", BUNDLE_ROOT);
        body.put("staged_assets", stagedAssets());

        // 바이너리 누락은 다운로드에서 실패하므로 미리보기에도 같은 상태를 알립니다.
        final List<String> missing = new ArrayList<>();
        stagedAssets().forEach((name, present) -> {
            if (!present) {
                missing.add(name);
            }
        });
        if (!missing.isEmpty()) {
            body.put("warning", "배포 자산이 스테이징되지 않았습니다: " + String.join(", ", missing)
                    + " (" + stageDirectory + " 를 확인하세요)");
            body.put("missing_assets", missing);
            body.put("download_ready", !missing.contains("sonar_validator_prober") && !missing.contains("default_template.sqlite"));
        } else {
            body.put("download_ready", true);
        }

        body.put("server_ip_source", "project");
        return body;
    }

    private Map<String, Boolean> stagedAssets() {
        final Map<String, Boolean> result = new LinkedHashMap<>();
        for (final String name : new String[] {
                "sonar_validator_prober", "default_template.sqlite", "Installer.sh", "restart.sh", "sonar_cli_bridge.tcl",
                "systemd/sonar_validator_prober.service", "systemd/sonar_validator_prober-cisco.service",
                "rc-service/sonar_validator_prober"}) {
            result.put(name, Files.isRegularFile(Path.of(stageDirectory, name)));
        }
        return result;
    }
}
