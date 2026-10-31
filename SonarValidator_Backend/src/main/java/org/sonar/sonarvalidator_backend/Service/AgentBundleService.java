package org.sonar.sonarvalidator_backend.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentBundleService {

    private static final Logger log = LoggerFactory.getLogger(AgentBundleService.class);

    private static final String BUNDLE_ROOT = "Installer";

    private final String stageDirectory;

    private final String serverIp;

    private final int serverPort;

    private final String managementInterface;

    private final List<String> managementCidrs;
    private final String terminalSharedSecret;

    public AgentBundleService(
            @Value("${sonar.deploy.stage-dir:/tmp/sonar_stage}") String stageDirectory,



            @Value("${sonar.deploy.server-ip:}") String serverIp,
            @Value("${server.port:3000}") int serverPort,
            @Value("${sonar.deploy.management-interface:}") String managementInterface,



            @Value("${sonar.site.management-prefix:}") String managementCidrs,
            @Value("${sonar.terminal.shared-secret:}") String terminalSharedSecret) {
        this.stageDirectory = stageDirectory;
        this.serverIp = serverIp == null ? "" : serverIp.trim();
        this.serverPort = serverPort;
        this.managementInterface = managementInterface == null ? "" : managementInterface.trim();
        this.managementCidrs = (managementCidrs == null || managementCidrs.isBlank())
                ? List.of()
                : List.of(managementCidrs.split("\\s*,\\s*"));
        this.terminalSharedSecret = terminalSharedSecret;
    }

    List<String> DetectLocalAddresses() {
        try {
            final String defaultRouteIface = DefaultRouteInterface();
            final List<String> preferred = new ArrayList<>();
            final List<String> fallback = new ArrayList<>();

            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                final NetworkInterface nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }

                // 관리 인터페이스를 지정했으면 그것만 봅니다.
                if (!managementInterface.isBlank()
                        && !managementInterface.equals(nic.getName())) {
                    continue;
                }

                final Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    final InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address)
                            || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()) {
                        continue;
                    }

                    final String host = address.getHostAddress();
                    if (preferred.contains(host) || fallback.contains(host)) {
                        continue;
                    }

                    // 관리 대역으로 지정된 주소는 최우선입니다.
                    if (!managementCidrs.isEmpty() && MatchesAnyCidr(host, managementCidrs)) {
                        preferred.add(0, host);
                    }
                    else if (!nic.getName().equals(defaultRouteIface)) {
                        preferred.add(host);
                    }
                    else {
                        fallback.add(host);
                    }
                }
            }

            final List<String> result = new ArrayList<>(preferred);
            result.addAll(fallback);
            return result;
        } catch (SocketException ex) {
            log.warn("local address detection failed: {}", ex.getMessage());
            return List.of();
        }
    }

    String DefaultRouteInterface() {
        try (DatagramSocket socket = new DatagramSocket()) {
            // 라우팅 결정만 필요하므로 실제로 보내지 않습니다(DNS 불필요).
            socket.connect(InetAddress.getByName("203.0.113.1"), 9);  // TEST-NET-3
            final InetAddress local = socket.getLocalAddress();
            if (local == null || local.isAnyLocalAddress()) {
                return "";
            }
            for (final NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (Collections.list(nic.getInetAddresses()).contains(local)) {
                    return nic.getName();
                }
            }
        } catch (IOException ex) {
            log.debug("default route interface detection failed: {}", ex.getMessage());
        }
        return "";
    }

    static boolean MatchesAnyCidr(String host, List<String> cidrs) {
        try {
            final byte[] target = InetAddress.getByName(host).getAddress();
            for (final String cidr : cidrs) {
                final int slash = cidr.indexOf('/');
                if (slash < 0) {
                    continue;
                }
                final byte[] network = InetAddress.getByName(cidr.substring(0, slash)).getAddress();
                final int prefix = Integer.parseInt(cidr.substring(slash + 1).trim());
                if (network.length != target.length || prefix < 0 || prefix > 32) {
                    continue;
                }

                int bits = prefix;
                boolean same = true;
                for (int i = 0; i < target.length && same; i++) {
                    final int take = Math.min(8, Math.max(0, bits));
                    final int mask = take == 0 ? 0 : (0xFF << (8 - take)) & 0xFF;
                    if ((target[i] & mask) != (network[i] & mask)) {
                        same = false;
                    }
                    bits -= 8;
                }
                if (same) {
                    return true;
                }
            }
        } catch (Exception ex) {
            log.debug("cidr match failed for {}: {}", host, ex.getMessage());
        }
        return false;
    }

    ResolvedServer resolveServer(String overrideIp, String overridePort) {
        final int port = resolvePort(overridePort);

        final String explicit = blankToNull(overrideIp);
        if (explicit != null) {
            return new ResolvedServer(explicit, port, "request", List.of());
        }
        if (!serverIp.isBlank()) {
            return new ResolvedServer(serverIp, port, "config", List.of());
        }
        final List<String> detected = DetectLocalAddresses();
        if (!detected.isEmpty()) {
            return new ResolvedServer(detected.get(0), port, "detected", detected);
        }
        return new ResolvedServer("127.0.0.1", port, "fallback", List.of());
    }

    private int resolvePort(String overridePort) {
        final String raw = blankToNull(overridePort);
        if (raw == null) {
            return serverPort;
        }
        try {
            final int parsed = Integer.parseInt(raw.trim());
            if (parsed < 1 || parsed > 65535) {
                log.warn("server_port override out of range, using {}: {}",
                        serverPort, raw);
                return serverPort;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            log.warn("server_port override is not a number, using {}: {}",
                    serverPort, raw);
            return serverPort;
        }
    }

    record ResolvedServer(String ip, int port, String source, List<String> candidates) {
    }

    public byte[] build(String agentId, String nodeType, String serverIpOverride,
                        String serverPortOverride, String dataDirectory) {
        if (terminalSharedSecret == null || terminalSharedSecret.trim().length() < 32) {
            throw new IllegalStateException(
                    "sonar.terminal.shared-secret must contain at least 32 characters to build an Agent bundle");
        }
        final String type = normalizeNodeType(nodeType);
        final ResolvedServer resolved = resolveServer(serverIpOverride, serverPortOverride);
        final String ip = resolved.ip();
        final int port = resolved.port();
        final Path proberBinary = Path.of(stageDirectory, "sonar_validator_prober");
        if (!Files.isRegularFile(proberBinary) || !Files.isReadable(proberBinary)) {
            throw new IllegalStateException("Required Agent binary is missing or unreadable: "
                + proberBinary);
        }

        try {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            // ⚠️ tar → gzip → buffer 순서로 close 되어야 gzip 트레일러(CRC·길이)가
            //    마지막에 기록됩니다. 안쪽부터 닫히도록 중첩 try 를 씁니다.
            try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(buffer);
                 TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {

                // ⚠️ 긴 파일 이름을 허용합니다. 기본값(POSIX)에서는 100자를 넘는
                //    이름이 조용히 잘려 장비에서 다른 이름으로 풀립니다.
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                // 긴 링크 이름도 마찬가지입니다.
                tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

                put(tar, "default.conf", defaultConf(ip, port, type, agentId, dataDirectory,
                    terminalSharedSecret) + "MANAGEMENT_PREFIX=" + String.join(",", managementCidrs) + ";\n");
                put(tar, "README.txt", readme(agentId, type, ip, port));
                putFile(tar, "sonar_validator_prober");
                putOptionalFile(tar, "Installer.sh");
                putOptionalFile(tar, "restart.sh");
                putOptionalFile(tar, "default_template.sqlite");
                putOptionalFile(tar, "systemd/sonar_validator_prober.service");
                putOptionalFile(tar, "systemd/sonar_validator_prober-cisco.service");
                putOptionalFile(tar, "rc-service/sonar_validator_prober");

                // ⚠️ finish() 는 필수입니다. tar 는 마지막에 1024바이트 0 블록을
                //    쓰는데, 이것이 없으면 일부 tar 가 "Unexpected EOF" 로 거부합니다.
                tar.finish();
            }




            final byte[] bundle = buffer.toByteArray();
            log.info("agent download generated: agent={} type={} server={}:{} format=tar.gz bytes={}",
                    agentId, type, ip, port, bundle.length);
            return bundle;

        } catch (IOException ex) {
            // 번들 생성 실패는 복구 불가한 서버 측 문제입니다.
            // 빈 파일을 내려주면 운영자가 그것을 설치하려다 원인을 알 수 없습니다.
            throw new IllegalStateException("agent download generation failed: " + ex.getMessage(), ex);
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

                0. 번들 풀기
                ------------
                  tar -xzf sonar-agent-%s.tar.gz
                  cd Installer
                (장비에 unzip 이 없어도 됩니다 — tar·gzip 은 기본 포함입니다)

                1. 바이너리 준비
                -----------------
                이 번들에는 설정·스크립트와 함께 스테이징된 정적 바이너리
                {@code sonar_validator_prober} 가 포함될 수 있습니다.
                바이너리가 없다면 서버 배포 단계에서 스테이징이 누락된 것이므로,
                먼저 정적 빌드를 만들고 /tmp/sonar_stage 에 넣어 주세요:

                  cd SonarValidator_Prober && cmake -S . -B build_static -DCMAKE_BUILD_TYPE=Release
                  cmake --build build_static --target sonar_validator_prober
                  cp build_static/sonar_validator_prober /tmp/sonar_stage/

                장치에 이미 바이너리가 있으면 다음 단계로 바로 진행하세요.
                (운영 환경에서 HTTP 다운로드를 쓸 수도 있지만, 번들 안에 포함된
                 바이너리가 있으면 가장 단순하고 안전합니다)

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
                """.formatted(agentId, canonical, ip, port, agentId, ip, agentId, how);
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
        entry.setMode(executable ? 0755 : 0644);
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

    public Map<String, Object> describe(String agentId, String nodeType,
                                        String serverIpOverride, String serverPortOverride) {
        final ResolvedServer resolved = resolveServer(serverIpOverride, serverPortOverride);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agent_id", agentId);
        body.put("node_type", normalizeNodeType(nodeType));
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
            body.put("download_ready", !missing.contains("sonar_validator_prober"));
        } else {
            body.put("download_ready", true);
        }

        body.put("server_ip_source", resolved.source());
        if (resolved.candidates().size() > 1) {
            // 후보가 여럿이면 운영자가 어느 것이 맞는지 골라야 합니다.
            body.put("server_ip_candidates", resolved.candidates());
        }
        return body;
    }

    private Map<String, Boolean> stagedAssets() {
        final Map<String, Boolean> result = new LinkedHashMap<>();
        for (final String name : new String[] {
                "sonar_validator_prober", "default_template.sqlite", "Installer.sh", "restart.sh",
                "systemd/sonar_validator_prober.service", "systemd/sonar_validator_prober-cisco.service",
                "rc-service/sonar_validator_prober"}) {
            result.put(name, Files.isRegularFile(Path.of(stageDirectory, name)));
        }
        return result;
    }
}
