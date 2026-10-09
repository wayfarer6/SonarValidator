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

/**
 * 배포할 Agent 의 <b>설정이 미리 채워진 다운로드 파일</b>을 만듭니다.
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
 * <p>이 값들은 모두 <b>서버가 이미 알고 있는 정보</b>(장치 유형, 프로젝트
 * 서브넷, 관리망 여부)이므로 서버가 채우는 것이 맞습니다.
 *
 * <h2>⚠️ 왜 ZIP 이 아니라 tar.gz 인가</h2>
 * <p>번들은 네트워크 장비에서 풀립니다. 그중 라우터(Alpine)·스위치(Open
 * vSwitch)에는 <b>{@code unzip} 이 없습니다.</b> BusyBox 의 {@code tar} 와
 * {@code gzip} 은 사실상 모든 배포판에 들어 있어, {@code tar -xzf} 한 줄로
 * 풀 수 있는 형식이 안전합니다. (예전에는 ZIP 이었고, 장비에서 풀리지 않아
 * 손으로 각 파일을 옮겨야 했습니다)
 *
 * <h2>무엇을 담는가</h2>
 * <p>모두 저장소의 {@code Installer/} 폴더 구조 그대로 담아, 풀면 그 자리가
 * 그대로 나오게 합니다. (예전에는 루트에 평평하게 담겨 "어느 파일이
 * 어디로 가는지" 를 README 로만 알 수 있었습니다)
 * <ul>
 *   <li>{@code Installer/default.conf} — 값이 채워진 설정</li>
 *   <li>{@code Installer/README.txt} — 이 장치에 맞춘 배포 절차</li>
 *   <li>{@code Installer/Installer.sh}, {@code Installer/restart.sh} — 스크립트</li>
 *   <li>{@code Installer/default_template.sqlite} — DB 템플릿 (있으면)</li>
 * </ul>
 *
 * <p>Agent 다운로드 파일에는 스테이징된 바이너리({@code sonar_validator_prober})를
 * 반드시 넣습니다. 실행 파일이 없는 번들은 설치할 수 없으므로, 생성 전에 필수
 * 바이너리를 확인하고 누락된 경우 다운로드를 실패시킵니다.
 */
@Service
public class AgentBundleService {

    private static final Logger log = LoggerFactory.getLogger(AgentBundleService.class);

    /**
     * 번들을 풀었을 때 나오는 루트 폴더 이름입니다.
     *
     * <p>저장소의 {@code SonarValidator_Prober/Installer/} 와 같은 이름을 씁니다.
     * 평평하게(루트에 파일만) 담으면 {@code Installer.sh} 처럼 "설치 스크립트" 인지
     * "설치 폴더" 인지 구분되지 않고, 풀었을 때 현재 폴더에 파일이 흩어집니다.
     */
    private static final String BUNDLE_ROOT = "Installer";

    /**
     * 스테이징된 배포 자산이 있는 디렉터리입니다.
     *
     * <p>배포 가이드의 {@code /tmp/sonar_stage} 관례를 그대로 씁니다.
     * 설정으로 덮어쓸 수 있습니다.
     */
    private final String stageDirectory;

    /** 서버가 Agent 에게 알려줄 주소입니다. 비어 있으면 자동 감지합니다. */
    private final String serverIp;

    /** Agent 가 접속할 포트입니다. (요청별 지정이 없을 때 쓰는 기본값) */
    private final int serverPort;

    /**
     * 관리 인터페이스 이름입니다. (선택)
     *
     * <p>예: {@code ens3}. 지정하면 그 인터페이스의 IPv4 를 씁니다.
     * 랩마다 관리망 인터페이스 이름이 다르므로 설정으로 바꿉니다.
     */
    private final String managementInterface;

    /**
     * 관리망 대역 목록입니다. (선택, 쉼표 구분)
     *
     * <p>예: {@code 10.20.0.0/24,172.16.255.0/24}.
     * 인터페이스 이름을 모르거나 DHCP 로 바뀔 때 씁니다.
     */
    private final List<String> managementCidrs;
    private final String terminalSharedSecret;

    /**
     * @param stageDirectory      배포 자산 디렉터리
     * @param serverIp            Agent 에 넣을 서버 주소 (비우면 자동 감지)
     * @param serverPort          Agent 에 넣을 서버 포트
     * @param managementInterface 관리 인터페이스 이름 (선택)
     * @param managementCidrs     관리망 대역 (선택, 쉼표 구분)
     */
    public AgentBundleService(
            @Value("${sonar.deploy.stage-dir:/tmp/sonar_stage}") String stageDirectory,
            // ⚠️ 여기에 특정 랩 주소를 기본값으로 두지 않습니다.
            //    (예전 기본값 192.168.122.58 은 D-AI-PBL 랩 전용이었고,
            //     랩을 바꾸면 **오류 없이** 엉뚱한 주소가 배포되었습니다)
            @Value("${sonar.deploy.server-ip:}") String serverIp,
            @Value("${server.port:3000}") int serverPort,
            @Value("${sonar.deploy.management-interface:}") String managementInterface,
            // ⚠️ 관리망 대역은 이제 사이트 설정과 같은 값을 씁니다.
            //    (sonar.site.management-prefix 가 단일 진실 공급원)
            //    별도 키로 두면 배포와 격리 경고가 서로 다른 대역을 말할 수 있습니다.
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

    /**
     * 로컬 인터페이스에서 쓸 만한 IPv4 주소를 모읍니다.
     *
     * <h2>⚠️ NAT 인터페이스를 먼저 배제하는 이유</h2>
     *
     * <p>개발 호스트는 보통 두 망에 동시에 붙습니다.
     * 예: {@code ens3 = 10.20.0.3/24}(관리망),
     * {@code ens4 = 192.168.122.32/24}(NAT, 기본 라우트).
     *
     * <p>단순히 "첫 번째 주소" 를 쓰면 <b>NAT 주소가 나갈 수 있습니다.</b>
     * 그러면 관리망만 있는 장치(스위치 등)가 그 주소에 도달하지 못해
     * 프로버가 조용히 "무응답" 으로 남습니다.
     *
     * <p>그래서 <b>기본 라우트가 나가는 인터페이스</b>는 후순위로 둡니다.
     * 데이터망 노드(라우터/방화벽/VM)는 NAT 로도 닿지만,
     * 관리망 전용 장치는 관리 주소로만 닿기 때문입니다.
     *
     * @return 관리 후보를 앞에 둔 주소 목록
     */
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

    /**
     * 기본 라우트가 나가는 인터페이스 이름을 구합니다.
     *
     * <p>이 인터페이스(NAT 망)는 관리망 전용 장치가 도달하지 못하므로
     * 자동 감지에서 후순위로 둡니다.
     *
     * @return 인터페이스 이름 (판별 실패하면 빈 문자열)
     */
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

    /**
     * 주소가 CIDR 목록 중 하나에 속하는지 봅니다.
     *
     * @param host  IPv4 주소 문자열
     * @param cidrs CIDR 목록 (예: {@code 10.20.0.0/24})
     * @return 하나라도 포함되면 true
     */
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

    /**
     * Agent 에게 알릴 서버 주소와 포트를 결정합니다.
     *
     * <h2>⚠️ 포트도 여기서 확정한다</h2>
     * <p>예전에는 포트가 <b>서버 설정값 고정</b>이었습니다. 그래서 화면이
     * {@code Management Server Port} 를 받아도 번들에는 반영되지 않고
     * 미리보기와 실제 파일이 <b>서로 다른 포트</b>를 말했습니다.
     * 운영자는 미리보기를 믿고 방화벽을 열었다가 프로버가 연결되지 않는 것을
     * 보게 됩니다. 주소와 같은 자리에서 확정합니다.
     *
     * @param overrideIp   장치별 서버 주소 (null/빈 값이면 무시)
     * @param overridePort 장치별 서버 포트 (null/빈 값/비정상이면 서버 설정값)
     * @return 결정된 주소·포트와 그 출처 (진단용)
     */
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

    /**
     * 포트 override 를 해석합니다.
     *
     * <p>범위를 벗어나거나 숫자가 아니면 <b>조용히 무시하지 않고</b> 서버
     * 설정값으로 돌아갑니다. 잘못된 포트를 번들에 실으면 장비에서 프로버가
     * 아무 말 없이 연결에 실패하므로, 차라리 알려진 정상 포트를 씁니다.
     * (무시했다는 사실은 로그로 남깁니다)
     *
     * @param overridePort 요청 포트 (null/빈 값 허용)
     * @return 확정된 포트
     */
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

    /**
     * 서버 주소와 그 출처입니다.
     *
     * @param ip         결정된 주소
     * @param port       결정된 포트
     * @param source     {@code request} / {@code config} / {@code detected} / {@code fallback}
     * @param candidates 자동 감지에서 발견한 다른 후보들 (운영자가 선택할 수 있게)
     */
    record ResolvedServer(String ip, int port, String source, List<String> candidates) {
    }

    /**
     * Agent 다운로드 파일(tar.gz)을 만듭니다.
     *
     * @param agentId           Agent 식별자 (= 배포 예정 등록 이름)
     * @param nodeType          {@code Router} / {@code Switch} / {@code VM} / {@code Firewall}
     * @param serverIpOverride  장치별 서버 주소 (null 이면 기본값)
     * @param serverPortOverride 장치별 서버 포트 (null 이면 서버 설정값)
     * @param dataDirectory     DATA_DIRECTORY 값 (null/빈 값이면 프로버 기본 경로)
     * @return tar.gz 바이트
     */
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
                    terminalSharedSecret));
                put(tar, "README.txt", readme(agentId, type, ip, port));
                putFile(tar, "sonar_validator_prober");
                putOptionalFile(tar, "Installer.sh");
                putOptionalFile(tar, "restart.sh");
                putOptionalFile(tar, "default_template.sqlite");

                // ⚠️ finish() 는 필수입니다. tar 는 마지막에 1024바이트 0 블록을
                //    쓰는데, 이것이 없으면 일부 tar 가 "Unexpected EOF" 로 거부합니다.
                tar.finish();
            }

            // ⚠️ toByteArray() 는 반드시 스트림을 모두 닫은 <b>뒤</b>에 부릅니다.
            //    블록 안에서 부르면 gzip 트레일러가 아직 안 쓰여 잘린 파일이 되고,
            //    장비에서 "unexpected end of file" 로 풀리지 않습니다.
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

    /**
     * 내려받을 파일 이름을 만듭니다.
     *
     * @param agentId Agent 식별자
     * @return 예: {@code sonar-agent-Gateway-Router.tar.gz}
     */
    public String fileNameFor(String agentId) {
        final String safe = (agentId == null ? "agent" : agentId)
                .trim()
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return "sonar-agent-" + safe + ".tar.gz";
    }

    /**
     * 번들에 넣을 {@code default.conf} 본문을 만듭니다.
     *
     * <p>프로버의 설정 파서는 {@code KEY=VALUE;} 형식을 씁니다.
     * ({@code ProberConfig::ReadDefaultValue})
     *
     * @param ip            서버 주소
     * @param port          서버 포트
     * @param nodeType      장치 유형
     * @param agentId       Agent 이름
     * @param dataDirectory DATA_DIRECTORY (빈 값이면 생략)
     * @return 파일 내용
     */
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

    /**
     * 이 장치에 맞춘 배포 절차를 만듭니다.
     *
     * @param agentId  Agent 이름
     * @param nodeType 장치 유형
     * @param ip       서버 주소
     * @param port     서버 포트
     * @return README 본문
     */
    private String readme(String agentId, String nodeType, String ip, int port) {
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
     * 번들에 파일 하나를 추가합니다.
     *
     * <p>모든 항목은 {@link #BUNDLE_ROOT} 아래에 들어가므로, 풀면
     * {@code Installer/} 폴더가 생기고 그 안에 파일이 모입니다.
     *
     * @param tar   대상 스트림
     * @param name  파일 이름 (루트 기준)
     * @param body  내용
     * @throws IOException 쓰기 실패
     */
    private static void put(TarArchiveOutputStream tar, String name, String body)
            throws IOException {
        putBytes(tar, name, body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 바이트를 tar 항목으로 씁니다.
     *
     * <p>사람이 읽고 실행하는 파일이므로 실행 스크립트({@code .sh})는 0755,
     * 나머지는 0644 로 둡니다. 권한을 지정하지 않으면 tar 기본값(0644)이 되어
     * 장비에서 {@code sh Installer.sh} 는 되지만 {@code ./Installer.sh} 는
     * 거부됩니다 — "실행했는데 permission denied" 로 보입니다.
     *
     * @param tar    대상 스트림
     * @param name   파일 이름 (루트 기준)
     * @param content 내용 바이트
     * @throws IOException 쓰기 실패
     */
    private static void putBytes(TarArchiveOutputStream tar, String name, byte[] content)
            throws IOException {
        final boolean executable = name.endsWith(".sh") || name.equals("sonar_validator_prober");
        final TarArchiveEntry entry = new TarArchiveEntry(BUNDLE_ROOT + "/" + name);
        entry.setSize(content.length);
        entry.setMode(executable ? 0755 : 0644);
        entry.setModTime(System.currentTimeMillis());
        tar.putArchiveEntry(entry);
        tar.write(content);
        tar.closeArchiveEntry();
    }

    /** 선택 배포 자산을 스테이징되어 있으면 아카이브에 넣습니다. */
    private void putOptionalFile(TarArchiveOutputStream tar, String name) throws IOException {
        final Path source = Path.of(stageDirectory, name);
        if (Files.isRegularFile(source)) {
            putBytes(tar, name, Files.readAllBytes(source));
        }
    }

    /** 필수 스테이징 배포 자산을 아카이브에 넣습니다. */
    private void putFile(TarArchiveOutputStream tar, String name) throws IOException {
        final Path source = Path.of(stageDirectory, name);
        if (!Files.isRegularFile(source)) {
            throw new IOException("Required bundle asset is missing: " + source);
        }
        putBytes(tar, name, Files.readAllBytes(source));
    }

    /**
     * 번들 생성 정보를 요약합니다. (화면 표시용)
     *
     * @param agentId  Agent 이름
     * @param nodeType 장치 유형
     * @param serverIpOverride 장치별 서버 주소 (null/빈 값이면 설정·감지)
     * @param serverPortOverride 장치별 서버 포트 (null/빈 값이면 설정값)
     * @return 정보 맵
     */
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

        // server_ip 를 어디서 얻었는지 밝힙니다.
        //   request  = 장치별로 지정됨
        //   config   = sonar.deploy.server-ip 설정값
        //   detected = 로컬 인터페이스에서 자동 감지
        //   fallback = 감지 실패 → 127.0.0.1 (개발 편의)
        body.put("server_ip_source", resolved.source());
        if (resolved.candidates().size() > 1) {
            // 후보가 여럿이면 운영자가 어느 것이 맞는지 골라야 합니다.
            body.put("server_ip_candidates", resolved.candidates());
        }
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
                "sonar_validator_prober", "default_template.sqlite", "Installer.sh", "restart.sh"}) {
            result.put(name, Files.isRegularFile(Path.of(stageDirectory, name)));
        }
        return result;
    }
}