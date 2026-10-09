package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.sonarvalidator_backend.Service.AgentBundleService;

/**
 * Agent 설치 번들 계약을 검증합니다.
 *
 * <h2>⚠️ 이 테스트가 잡는 실제 결함 3건</h2>
 * <ol>
 *   <li><b>형식이 ZIP 이었음</b> — 번들은 라우터(Alpine)·스위치(OVS)에서
 *       풀립니다. 그 장비에는 <b>{@code unzip} 이 없고</b> {@code tar·gzip} 만
 *       있습니다. ZIP 을 내려주면 장비에서 아무것도 못 합니다.</li>
 *   <li><b>포트 override 가 무시됨</b> — 화면은 {@code Management Server Port}
 *       를 받아 미리보기에 보여줬는데, 실제 번들은 서버 설정값
 *       ({@code server.port})을 썼습니다. 미리보기와 파일이 서로 다른 포트를
 *       말하므로 운영자는 그대로 믿고 방화벽을 엽니다.</li>
 *   <li><b>파일이 루트에 평평하게 담김</b> — 풀면 현재 폴더에 파일이 흩어져
 *       "어느 파일이 어디로 가는지" 를 README 로만 알 수 있었습니다.</li>
 * </ol>
 *
 * <p>번들 바이트를 직접 열어 보는 이유: 서비스가 반환하는 것이 <b>압축
 * 스트림</b>이라, 크기나 개수만 보면 위 결함이 모두 통과합니다.
 */
class AgentBundleServiceTest {

    /** 요청별 지정이 없을 때 쓰이는 서버 설정 포트. */
    private static final int CONFIGURED_PORT = 3000;

    private static AgentBundleService service(Path stage) {
        return new AgentBundleService(
                stage.toString(),
                "",                 // serverIp 자동 감지 (테스트에서는 override 를 씀)
                CONFIGURED_PORT,
                "",
                "",
                "test-terminal-shared-secret-that-is-long-enough");
    }

    /**
     * tar.gz 를 풀어 {파일 이름 → 내용} 으로 만듭니다.
     *
     * @param bytes 번들 바이트
     * @return 항목 맵
     * @throws IOException 풀기 실패 (형식이 tar.gz 가 아닐 때)
     */
    private static Map<String, String> readTarGz(byte[] bytes) throws IOException {
        final Map<String, String> files = new LinkedHashMap<>();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new GzipCompressorInputStream(new ByteArrayInputStream(bytes)))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                files.put(entry.getName(),
                        new String(tar.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    // ------------------------------------------------------------------
    //  형식: tar.gz
    // ------------------------------------------------------------------

    @Test
    @DisplayName("번들은 gzip 매직 바이트로 시작한다 (ZIP 이 아니다)")
        void bundleStartsWithGzipMagic(@TempDir Path stage) throws IOException {
                stageBinary(stage);
        final byte[] bundle = service(stage).build(
                "CiscoCatalyst8000V-Router", "Router", "10.20.0.3", null, null);

        assertTrue(bundle.length > 0, "번들이 비어 있으면 안 됩니다");
        // gzip 매직: 0x1f 0x8b. ZIP 은 'P''K'(0x50 0x4b) 입니다.
        assertEquals(0x1f, bundle[0] & 0xff, "gzip 매직 바이트가 아닙니다");
        assertEquals(0x8b, bundle[1] & 0xff, "gzip 매직 바이트가 아닙니다");
    }

    @Test
    @DisplayName("번들 파일 이름이 .tar.gz 로 끝난다")
    void fileNameUsesTarGzExtension(@TempDir Path stage) {
        final String fileName = service(stage).fileNameFor("Gateway-Router");

        assertEquals("sonar-agent-Gateway-Router.tar.gz", fileName);
    }

    @Test
    @DisplayName("파일은 Installer/ 폴더 아래에 담긴다")
    void entriesLiveUnderInstallerDirectory(@TempDir Path stage) throws IOException {
                stageBinary(stage);
        final Map<String, String> files = readTarGz(service(stage).build(
                "Gateway-Router", "Router", "10.20.0.3", null, null));

        // 풀면 Installer/ 폴더가 생기고 그 안에 파일이 모여야 합니다.
        assertTrue(files.containsKey("Installer/default.conf"),
                "default.conf 는 Installer/ 아래에 있어야 합니다: " + files.keySet());
        assertTrue(files.containsKey("Installer/README.txt"),
                "README.txt 는 Installer/ 아래에 있어야 합니다: " + files.keySet());
        // 루트에 평평하게 담기면 안 됩니다.
        assertFalse(files.containsKey("default.conf"),
                "루트에 그대로 담기면 풀었을 때 파일이 흩어집니다");
    }

    @Test
    @DisplayName("tar 는 마지막 0 블록을 써서 정상 종료한다")
    void archiveIsProperlyTerminated(@TempDir Path stage) throws IOException {
                stageBinary(stage);
        final byte[] bundle = service(stage).build(
                "Gateway-Router", "Router", "10.20.0.3", null, null);

        // 위 readTarGz 가 예외 없이 끝났다면 tar 종료 블록이 있습니다.
        // (finish() 를 빼먹으면 여기서 "Unexpected EOF" 로 실패합니다)
        assertFalse(readTarGz(bundle).isEmpty(), "항목이 하나도 없습니다");
    }

    // ------------------------------------------------------------------
    //  설정 값: 주소·포트 override
    // ------------------------------------------------------------------

    @Test
    @DisplayName("요청 포트가 default.conf 에 그대로 들어간다")
    void portOverrideLandsInDefaultConf(@TempDir Path stage) throws IOException {
                stageBinary(stage);
        final Map<String, String> files = readTarGz(service(stage).build(
                "Gateway-Router", "Router", "10.20.0.3", "8443", null));

        final String conf = files.get("Installer/default.conf");
        assertNotNull(conf, "default.conf 가 없습니다");
        assertTrue(conf.contains("SERVER_IP=10.20.0.3;"),
                "요청 주소가 들어가야 합니다:\n" + conf);
        assertTrue(conf.contains("SERVER_PORT=8443;"),
                "요청 포트가 들어가야 합니다 (설정값 3000 이 아니라):\n" + conf);
        assertTrue(conf.contains("NODE_TYPE=Router;"), conf);
        assertTrue(conf.contains("AGENT_NAME=Gateway-Router;"), conf);
        assertTrue(conf.contains("TERMINAL_SHARED_SECRET=test-terminal-shared-secret-that-is-long-enough;"), conf);
    }

    @Test
    @DisplayName("포트를 지정하지 않으면 서버 설정값을 쓴다")
        void missingPortFallsBackToConfigured(@TempDir Path stage) throws IOException {
                stageBinary(stage);
        final String conf = readTarGz(service(stage).build(
                "Gateway-Router", "Router", "10.20.0.3", null, null))
                .get("Installer/default.conf");

        assertTrue(conf.contains("SERVER_PORT=" + CONFIGURED_PORT + ";"), conf);
    }

    @Test
    @DisplayName("숫자가 아니거나 범위 밖인 포트는 조용히 넘기지 않고 설정값으로 되돌린다")
        void invalidPortFallsBackToConfigured(@TempDir Path stage) throws IOException {
                stageBinary(stage);
        final AgentBundleService service = service(stage);

        for (final String bogus : new String[] {"abc", "0", "70000", "-1", "  "}) {
            final String conf = readTarGz(service.build(
                    "Gateway-Router", "Router", "10.20.0.3", bogus, null))
                    .get("Installer/default.conf");
            assertTrue(conf.contains("SERVER_PORT=" + CONFIGURED_PORT + ";"),
                    "잘못된 포트(" + bogus + ")는 설정값으로 되돌려야 합니다:\n" + conf);
        }
    }

    @Test
    @DisplayName("info 응답도 확정된 포트를 알려준다 (미리보기와 파일이 일치)")
        void describeReportsResolvedPort(@TempDir Path stage) throws IOException {
                stageBinary(stage);
        final Map<String, Object> info = service(stage).describe(
                "Gateway-Router", "Router", "10.20.0.3", "8443");

        // ⚠️ 여기서 설정값(3000)이 나오면 화면 미리보기가 실제 번들과 어긋납니다.
        assertEquals(8443, info.get("server_port"), "미리보기 포트가 실제와 달라집니다");
        assertEquals("10.20.0.3", info.get("server_ip"));
        assertEquals("tar.gz", info.get("archive_format"));
        assertEquals("Installer", info.get("installer_dir"));
        assertEquals("sonar-agent-Gateway-Router.tar.gz", info.get("file_name"));
    }

    // ------------------------------------------------------------------
    //  스테이징 자산 포함
    // ------------------------------------------------------------------

    @Test
    @DisplayName("스테이징된 바이너리·스크립트·템플릿을 Installer/ 아래에 담고 실행 권한을 준다")
    void stagedAssetsAreIncludedWithExecutableMode(@TempDir Path stage) throws IOException {
        // 배포 가이드가 /tmp/sonar_stage 에 두는 자산을 흉내냅니다.
        Files.write(stage.resolve("sonar_validator_prober"), new byte[] {0x7f, 'E', 'L', 'F'});
        Files.writeString(stage.resolve("Installer.sh"), "#!/bin/sh\necho install\n");
        Files.writeString(stage.resolve("restart.sh"), "#!/bin/sh\necho restart\n");
        Files.write(stage.resolve("default_template.sqlite"), new byte[] {0x53, 0x51, 0x4c});

        final byte[] bundle = service(stage).build(
                "Gateway-Router", "Router", "10.20.0.3", null, null);

        final Map<String, String> files = readTarGz(bundle);
        assertTrue(files.containsKey("Installer/sonar_validator_prober"), files.keySet().toString());
        assertTrue(files.containsKey("Installer/Installer.sh"), files.keySet().toString());
        assertTrue(files.containsKey("Installer/restart.sh"), files.keySet().toString());
        assertTrue(files.containsKey("Installer/default_template.sqlite"),
                files.keySet().toString());

        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new GzipCompressorInputStream(new ByteArrayInputStream(bundle)))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.getName().endsWith(".sh") || entry.getName().endsWith("sonar_validator_prober")) {
                    assertTrue((entry.getMode() & 0111) != 0,
                            entry.getName() + " 에 실행 권한이 없습니다: 0"
                                    + Integer.toOctalString(entry.getMode()));
                }
            }
        }
    }

        @Test
        @DisplayName("필수 Prober 바이너리가 없으면 불완전한 다운로드 생성을 거부한다")
        void missingBinaryRejectsBundle(@TempDir Path stage) {
                final IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> service(stage).build(
                                                "Gateway-Router", "Router", "10.20.0.3", null, null));

                assertTrue(error.getMessage().contains("sonar_validator_prober"), error.getMessage());
        }

        @Test
        @DisplayName("번들에 포함된 Prober 바이트가 스테이징 파일과 일치한다")
        void stagedBinaryContentsArePreserved(@TempDir Path stage) throws IOException {
                final byte[] binary = new byte[] {0x7f, 'E', 'L', 'F', 0x01, 0x02};
                Files.write(stage.resolve("sonar_validator_prober"), binary);

                final byte[] bundle = service(stage).build(
                                "Gateway-Router", "Router", "10.20.0.3", null, null);

                try (TarArchiveInputStream tar = new TarArchiveInputStream(
                                new GzipCompressorInputStream(new ByteArrayInputStream(bundle)))) {
                        TarArchiveEntry entry;
                        while ((entry = tar.getNextEntry()) != null) {
                                if (entry.getName().equals("Installer/sonar_validator_prober")) {
                                        assertEquals(java.util.Arrays.toString(binary),
                                                        java.util.Arrays.toString(tar.readAllBytes()));
                                        return;
                                }
                        }
                }
                throw new AssertionError("번들에 Prober 바이너리가 없습니다");
        }

        private static void stageBinary(Path stage) throws IOException {
                Files.write(stage.resolve("sonar_validator_prober"), new byte[] {0x7f, 'E', 'L', 'F'});
        }
}