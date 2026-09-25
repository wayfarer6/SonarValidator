package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.List;

import org.sonar.sonarvalidator_backend.Service.cli.CliJson;
import org.sonar.sonarvalidator_backend.Service.cli.CliText;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code show ip interface brief} / {@code show ip interface} 계열 한 줄을
 * 인터페이스 레코드로 바꿉니다.
 *
 * <h2>왜 공유하는가</h2>
 * <p>FRR({@code eth0  up  up}), Cisco({@code GigabitEthernet1 192.168.122.254
 * YES NVRAM up up}), Arista 출력은 표기가 다르지만 "이름 [주소] 나머지 필드"
 * 라는 뼈대가 같습니다. 세 벤더 방문자가 같은 규칙을 중복 구현하면 한쪽만
 * 고쳐지는 드리프트가 생기므로 여기 한 곳에 둡니다.
 *
 * <h2>문법이 판별하고 여기서는 조립만 한다</h2>
 * <p>이 클래스는 <b>문자열을 보고 종류를 추측하지 않습니다.</b> 각 방문자가
 * 자기 문법의 토큰 타입을 {@link Kind} 로 옮겨 주면, 여기서는 열 순서만
 * 해석합니다(첫 상태 단어 = link, 둘째 = protocol). 벤더별 어휘 차이는
 * 문법의 {@code STATUSWORD}/{@code METHOD} 토큰이 이미 흡수했습니다.
 *
 * <p>C++ Prober 의 {@code ParseBriefEntryTokens} 와 동일한 의미론입니다.
 */
public final class BriefEntry {

    /**
     * 브리프 줄 조각의 의미. 방문자가 자기 문법의 토큰 타입을 이 값으로 옮깁니다.
     */
    public enum Kind {
        /** 인터페이스명 (첫 조각) */
        NAME,
        /** IP 주소 (CIDR 포함) */
        ADDRESS,
        /** {@code unassigned} */
        UNASSIGNED,
        /** 주소 획득 방법 ({@code YES}/{@code NVRAM}/{@code manual}) */
        METHOD,
        /** 상태 단어. 순서대로 status → protocol 로 들어갑니다. */
        STATUS,
        /** 위 어디에도 속하지 않는 조각 */
        EXTRA
    }

    /**
     * 브리프 줄의 조각 하나.
     *
     * @param kind 의미
     * @param text 원문 텍스트
     */
    public record Piece(Kind kind, String text) {
    }

    private BriefEntry() {
    }

    /**
     * 브리프 한 줄의 조각들을 인터페이스 레코드로 조립합니다.
     *
     * @param pieces 의미가 붙은 조각 목록
     * @return 인터페이스 레코드 (조각이 없으면 빈 객체)
     */
    public static ObjectNode parse(List<Piece> pieces) {
        final ObjectNode entry = CliJson.object();
        if (pieces.isEmpty()) {
            return entry;
        }

        final ArrayNode extras = CliJson.array();

        for (final Piece piece : pieces) {
            final String token = CliText.trimPunct(piece.text());
            if (token.isEmpty() && piece.kind() != Kind.UNASSIGNED) {
                continue;
            }

            switch (piece.kind()) {
                case NAME -> {
                    if (!entry.has("name")) {
                        entry.put("name", token);
                    } else {
                        extras.add(token);
                    }
                }
                case ADDRESS -> {
                    if (!entry.has("ip_address")) {
                        final String[] bare = new String[1];
                        final int[] prefixLen = new int[1];
                        if (CliText.splitCidr(token, bare, prefixLen)) {
                            entry.put("ip_address", bare[0]);
                            if (prefixLen[0] >= 0) {
                                entry.put("prefix_len", prefixLen[0]);
                            }
                        } else {
                            entry.put("ip_address", token);
                        }
                    } else {
                        extras.add(token);
                    }
                }
                case UNASSIGNED -> {
                    entry.put("ip_address", "");
                    entry.put("unassigned", true);
                }
                case METHOD -> entry.put("method", token);
                case STATUS -> {
                    // 첫 상태 단어는 link 상태, 두 번째는 line protocol 상태다.
                    if (!entry.has("status")) {
                        entry.put("status", token);
                    } else if (!entry.has("protocol")) {
                        entry.put("protocol", token);
                    } else {
                        extras.add(token);
                    }
                }
                case EXTRA -> extras.add(token);
            }
        }

        if (!extras.isEmpty()) {
            entry.set("extras", extras);
        }
        return entry;
    }
}