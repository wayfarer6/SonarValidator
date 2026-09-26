package org.sonar.sonarvalidator_backend.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 배포할 Agent 의 <b>설정이 미리 채워진 설치 번들</b>을 만듭니다.
 *
 * <h2>⚠️ 왜 서버가 만드는가</h2>
 * <p>프로버는 {@code Installer/default.conf} 의 값(SERVER_IP / SERVER_PORT /
 * NODE_TYPE / AGENT_NAME)으로 동작합니다. 그런데 이 값을 사람이 손으로 채우면
 * <b>반드시 틀립니다.</b>
 *
 * <ul>
 *   <li>서버 주소는 랩마다 다릅니다 ({@code 192.168.122.58} vs
 *       {@code 172.16.255.245}) — 관리망만 있는 장치는 NAT 주소에 도달하지
 *       못합니다.</li>
 *   <li>장치 유형을 잘못 적으면 정책 적용기가 다른 벤더 명령을 만듭니다.</li>
 *   <li>{@code AGENT_NAME} 이 "배포 예정" 등록 이름과 다르면 같은 장치가
 *       <b>두 줄로</b> 나타나고, 등록한 장치는 영원히 무응답으로 남습니다.</li>
 * </ul>
 *
 * <p>이 세 값은 모두 <b>서버가 이미 알고 있는 정보</b>(장치 유형, 프로젝트
 * 서브넷, 관리망 여부)이므로 서버가 채우는 것이 맞습니다.
 *
 * <h2>무엇을 담는가</h2>
 * <ul>
 *   <li>{@code default.conf} — 값이 채워진 설정</li>
 *   <li>{@code README.txt} — 이 장치에 맞춘 배포 절차 (사람이 읽고 실행)</li>
 *   <li>{@code Installer.sh}, {@code restart.sh} — 스크립트</li>
 *   <li>{@code default_template.sqlite} — DB 템플릿 (있으면)</li>
 * </ul>
 *
 * <p>바이너리({@code sonar_validator_prober})는 <b>넣지 않습니다</b> —
 * 빌드 산출물이라 저장소에 없고, 배포 스크립트가 HTTP 로 내려받습니다.
 * README 에 그 절차를 적어 둡니다.
 */
@Service
public class AgentBundleService {

    private static final Logger log = LoggerFactory.getLogger(AgentBundleService.class);

    /**
     * 스테이징된 배포 자산이 있는 디렉터리입니다.
     *
     * <p>배포 가이드의 {@code /tmp/sonar_stage} 관례를 그대로 씁니다.
     * 설정으로 덮어쓸 수 있습니다.
     */
    private final String stageDirectory;

    /** 서버가 Agent 에게 알려줄 주소입니다. (기본: 이 서버의 관리 주소) */
    private final String serverIp;

    /** Agent 가 접속할 포트입니다. */
    private final int serverPort;

    /**
     * @param stageDirectory 배포 자산 디렉터리
     * @param serverIp       Agent 에 넣을 서버 주소
     * @param serverPort     Agent 에 넣을 서버 포트
     */
    public AgentBundleService(
            @Value("${sonar.deploy.stage-dir:/tmp/sonar_stage}") String stageDirectory,
            @Value("${sonar.deploy.server-ip:192.168.122.58}") String serverIp,
            @Value("${server.port:3000}") int serverPort) {
        this.stageDirectory = stageDirectory;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }

    /**
     * Agent 설치 번들(ZIP)을 만듭니다.
     *
     * @param agentId     Agent 식별자 (= 배포 예정 등록 이름)
     * @param nodeType    {@code Router} / {@code Switch} / {@code VM} / {@code Firewall}
     * @param serverIpOverride 장치별 서버 주소 (null 이면 기본값)
     * @param dataDirectory DATA_DIRECTORY 값 (null/빈 값이면 프로버 기본 경로)
     * @return ZIP 바이트
     */
    public byte[] build(String agentId, String nodeType, String serverIpOverride,
                        String dataDirectory) {
        final String type = normalizeNodeType(nodeType);
        final String ip = blankToNull(serverIpOverride) == null ? serverIp : serverIpOverride.trim();

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {

            put(zip, "default.conf", defaultConf(ip, type, agentId, dataDirectory));
            put(zip, "README.txt", readme(agentId, type, ip));
            putFileIfPresent(zip, "Installer.sh");
            putFileIfPresent(zip, "restart.sh");
            putFileIfPresent(zip, "default_template.sqlite");

            zip.finish();
            log.info("agent bundle built: agent={} type={} server={}:{} bytes={}",
                    agentId, type, ip, serverPort, buffer.size());
            return buffer.toByteArray();

        } catch (IOException ex) {
            // ZIP 생성 실패는 복구 불가한 서버 측 문제입니다.
            // 빈 파일을 내려주면 운영자가 그것을 설치하려다 원인을 알 수 없습니다.
            throw new IllegalStateException("agent bundle build failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 내려받을 파일 이름을 만듭니다.
     *
     * @param agentId Agent 식별자
     * @return 예: {@code sonar-agent-Gateway-Router.zip}
     */
    public String fileNameFor(String agentId) {
        final String safe = (agentId == null ? "agent" : agentId)
                .trim()
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return "sonar-agent-" + safe + ".zip";
    }

    /**
     * 번들에 넣을 {@code default.conf} 본문을 만듭니다.
     *
     * <p>프로버의 설정 파서는 {@code KEY=VALUE;} 형식을 씁니다.
     * ({@code ProberConfig::ReadDefaultValue})
     *
     * @param ip            서버 주소
     * @param nodeType      장치 유형
     * @param agentId       Agent 이름
     * @param dataDirectory DATA_DIRECTORY (빈 값이면 생략)
     * @return 파일 내용
     */
    private String defaultConf(String ip, String nodeType, String agentId, String dataDirectory) {
        final StringBuilder text = new StringBuilder();
        text.append("# SonarValidator Agent 설정\n")
                .append("# 이 파일은 서버가 생성했습니다. 손으로 고치지 마세요.\n")
                .append("# (고치면 배포 예정 등록 이름과 어긋나 같은 장치가 두 줄로 보입니다)\n")
                .append("#\n")
                .append("# NODE_TYPE 은 Router / Switch / VM / Firewall 네 가지만 있습니다.\n\n");
        text.append("SERVER_IP=").append(ip).append(";\n");
        text.append("SERVER_PORT=").append(serverPort).append(";\n");
        text.append("NODE_TYPE=").append(nodeType).append(";\n");
        // ⚠️ AGENT_NAME 이 핵심입니다. 배포 예정 등록 이름과 같아야
        //    "예정" 과 "연결" 이 한 줄로 합쳐집니다.
        text.append("AGENT_NAME=").append(agentId).append(";\n");

        final String dir = blankToNull(dataDirectory);
        if (dir != null) {
            // 장치마다 쓸 수 있는 경로가 다릅니다 (예: Arista /mnt/flash).
            text.append("DATA_DIRECTORY=").append(dir).append(";\n");
        }
        return text.toString();
    }

    /**
     * 이 장치에 맞춘 배포 절차를 만듭니다.
     *
     * @param agentId  Agent 이름
     * @param nodeType 장치 유형
     * @param ip       서버 주소
     * @return README 본문
     */
    private String readme(String agentId, String nodeType, String ip) {
        // ⚠️ 장치별 준비 절차는 AgentNodeType 이 소유합니다.
        //    이전에는 여기 switch 가 있었고, normalizeNodeType 에도 같은
        //    유형 switch 가 따로 있었습니다. 두 곳이 서로 다른 표기를 쓰면
        //    "VM 번들인데 라우터 안내" 가 됩니다.
        final AgentNodeType type = AgentNodeType.parse(nodeType);
        final String how = type.prepareNote();
        final String canonical = type.canonical();

        return """
                SonarValidator Agent 설치 안내
                ==============================

                Agent 이름 : %s
                장치 유형  : %s
                서버 주소  : %s:%d
                ⚠️ 이 이름과 장치 유형은 서버에 이미 등록되어 있습니다.
                   바꾸면 서버가 다른 장치로 인식합니다.

                1. 바이너리 준비
                -----------------
                이 번들에는 설정과 스크립트만 들어 있습니다.
                바이너리는 서버에서 HTTP 로 내려받습니다 (빌드 산출물이라 함께 담지 않음):

                  # 서버에서
                  cd SonarValidator_Prober && cmake --build build_static --target sonar_validator_prober
                  cp build_static/sonar_validator_prober /tmp/sonar_stage/
                  cd /tmp/sonar_stage && python3 -m http.server 8099 --bind 0.0.0.0 &

                  # 장치에서
                  wget -O /opt/sonar_validator/sonar_validator_prober http://%s:8099/sonar_validator_prober
                  chmod +x /opt/sonar_validator/sonar_validator_prober

                ⚠️ 라우터(Alpine)에는 curl 이 없습니다. wget 을 쓰세요.

                2. 설정 배치
                ------------
                  mkdir -p /opt/sonar_validator/data
                  cp default.conf /opt/sonar_validator/default.conf
                  cp default_template.sqlite /opt/sonar_validator/
                  rm -f /opt/sonar_validator/data/settings.conf

                ⚠️ 기존 settings.conf 를 지워야 새 AGENT_NAME 이 반영됩니다.
                   남아 있으면 예전 이름으로 접속해 배포 예정과 합쳐지지 않습니다.

                3. 실행
                -------
                  cd /opt/sonar_validator
                  SONAR_DATA_DIR=/opt/sonar_validator/data \\
                  SONAR_TEMPLATE_PATH=/opt/sonar_validator/default_template.sqlite \\
                  SONAR_CONFIG_PATH=/opt/sonar_validator/default.conf \\
                    nohup ./sonar_validator_prober > run.log 2>&1 &

                4. 재시작 (⚠️ SIGTERM 필수)
                --------------------------
                  sh restart.sh %s VM

                kill -9 는 SQLite 에 hot journal 을 남겨 다음 기동이
                "Runtime initialization failed" 로 실패합니다.

                5. 장치별 참고
                --------------
                %s

                6. 확인
                -------
                  30초 주기로 텔레메트리를 전송합니다.
                  서버에서 GET /api/v1/agents/overview 로 "connected" 를 확인하세요.
                """.formatted(agentId, canonical, ip, serverPort, ip, ip, how);
    }

    /**
     * 장치 유형을 정규화합니다. 프로버가 기대하는 표기로 맞춥니다.
     *
     * <p>표기 규칙의 유일한 출처는 {@link AgentNodeType} 입니다. 이 메서드는
     * 기존 호출부를 위한 얇은 위임으로 남겨 둡니다 — 정책 서비스가
     * {@code normalizeNodeType} 을 필요로 할 수 있고, 계약을 바꾸면
     * 두 곳이 서로 다른 이름을 쓰게 됩니다.
     *
     * @param nodeType 입력 (null 허용)
     * @return {@code Router} / {@code Switch} / {@code VM} / {@code Firewall}
     */
    public static String normalizeNodeType(String nodeType) {
        return AgentNodeType.canonicalOf(nodeType);
    }

    /** @return 빈 문자열이면 null */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * ZIP 항목을 추가합니다.
     *
     * @param zip  대상 스트림
     * @param name 항목 이름
     * @param body 내용
     * @throws IOException 쓰기 실패
     */
    private static void put(ZipOutputStream zip, String name, String body) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /**
     * 스테이징 디렉터리에 있으면 그대로 담습니다. (없으면 조용히 건너뜀)
     *
     * <p>없다고 실패시키지 않는 이유: 스크립트는 배포 방식에 따라 필요 여부가
     * 다릅니다. 설정만 있으면 프로버는 동작합니다.
     *
     * @param zip  대상 스트림
     * @param name 파일 이름
     * @throws IOException 읽기/쓰기 실패
     */
    private void putFileIfPresent(ZipOutputStream zip, String name) throws IOException {
        final java.nio.file.Path source = java.nio.file.Path.of(stageDirectory, name);
        if (!java.nio.file.Files.isRegularFile(source)) {
            log.debug("bundle asset not staged: {}", source);
            return;
        }
        zip.putNextEntry(new ZipEntry(name));
        zip.write(java.nio.file.Files.readAllBytes(source));
        zip.closeEntry();
    }

    /**
     * 번들 생성 정보를 요약합니다. (화면 표시용)
     *
     * @param agentId  Agent 이름
     * @param nodeType 장치 유형
     * @return 정보 맵
     */
    public Map<String, Object> describe(String agentId, String nodeType) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agentId);
        body.put("node_type", normalizeNodeType(nodeType));
        body.put("server_ip", serverIp);
        body.put("server_port", serverPort);
        body.put("file_name", fileNameFor(agentId));
        body.put("staged_assets", stagedAssets());
        return body;
    }

    /**
     * 스테이징된 자산 목록을 확인합니다.
     *
     * @return 자산 이름 → 존재 여부
     */
    private Map<String, Boolean> stagedAssets() {
        final Map<String, Boolean> result = new LinkedHashMap<>();
        for (final String name : new String[] {
                "default_template.sqlite", "Installer.sh", "restart.sh"}) {
            result.put(name, java.nio.file.Files.isRegularFile(
                    java.nio.file.Path.of(stageDirectory, name)));
        }
        return result;
    }
}