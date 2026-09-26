package org.sonar.sonarvalidator_backend.Service.cli.query;

import tools.jackson.databind.node.ObjectNode;

/**
 * 원문 CLI 한 건을 파싱한 결과입니다.
 *
 * <h2>⚠️ 왜 <b>세 값</b>을 함께 돌려주는가</h2>
 * <p>호출자는 파싱 본문만 필요하지 않습니다. 소비자
 * ({@code AbstractDeviceConfigParser}) 는 <b>계약 키</b>로 본문을 찾고,
 * 운영자는 <b>어떤 대상으로 해석했는지</b>를 봐야 합니다.
 *
 * <pre>
 *   "route" 요청 → query="route", contractKey="route_status", body={routes:[...]}
 *                    │                  │
 *                    │                  └─ 소비자가 읽는 키
 *                    └─ 로그/화면에 남는 해석 결과
 * </pre>
 *
 * <p>이 셋을 따로 계산하면 <b>서로 어긋납니다.</b> 예를 들어 대상은
 * {@code route} 로 해석했는데 계약 키만 벤더 기본값({@code nic_status})을
 * 쓰면, 파싱은 성공했는데 화면에는 아무것도 안 나옵니다. 그래서 선택기가
 * 한 번에 만들어 돌려줍니다.
 *
 * @param query       해석된 대상 이름 (벤더 기본으로 폴백했으면 그 이름)
 * @param contractKey 소비자가 읽을 payload 최상위 키
 * @param body        파싱 본문 (실패 시에도 {@code parsed:false} 노드)
 */
public record QueryResult(String query, String contractKey, ObjectNode body) {

    /**
     * 각 문법이 넣는 개수 키입니다.
     *
     * <h2>⚠️ 새 문법이 늘면 여기 한 곳만 고친다</h2>
     * <p>이 목록에서 빠진 키가 하나 있었습니다 — {@code brief_count}
     * ({@code ip -br addr} 출력). 그래서 {@code brief} 로 파싱한 결과가
     * <b>항상 0건으로 판정</b>되어, 파싱이 성공했는데도 데이터가 버려졌습니다.
     *
     * <p>개수 키를 쓰는 이유는 <b>비용</b>입니다. 배열 전체를 세면 큰 규칙셋에서
     * 낭비이므로, 문법이 이미 계산해 둔 값을 먼저 씁니다.
     */
    private static final String[] COUNT_KEYS = {
        "interface_count", "brief_count", "route_count", "entry_count", "vlan_count",
        "port_count", "bridge_count", "table_count",
    };

    /**
     * 개수 키가 없을 때 대신 세는 배열 키입니다. (안전망)
     *
     * <h2>⚠️ 왜 안전망이 필요한가</h2>
     * <p>문법이 개수 키를 빠뜨리면 <b>파싱 성공이 "0건" 으로 뒤집힙니다.</b>
     * 그러면 호출자가 데이터를 버리고, 예외도 나지 않아 조용합니다.
     * (실측: {@code brief_count} 누락)
     *
     * <p>배열을 직접 세면 비용이 조금 더 들지만, <b>데이터를 잃는 것보다</b>
     * 낫습니다. 개수 키가 있는 문법은 위에서 먼저 처리되므로 평소에는
     * 이 경로를 타지 않습니다.
     */
    private static final String[] ARRAY_KEYS = {
        "interfaces", "brief", "routes", "entries", "vlans", "ports",
        "bridges", "tables", "chains", "rules",
    };

    /** @return 본문이 유효하게 파싱됐는지 */
    public boolean parsed() {
        return body != null && body.path("parsed").asBoolean(false);
    }

    /**
     * 대표 항목 수입니다.
     *
     * <p>① 문법이 계산해 둔 개수 키를 먼저 봅니다. ② 없으면 알려진 배열 키를
     * 직접 세어 <b>파싱 성공이 0건으로 뒤집히는 것을 막습니다.</b>
     *
     * @return 항목 수 (판정 불가면 0)
     */
    public int itemCount() {
        if (body == null) {
            return 0;
        }
        for (final String key : COUNT_KEYS) {
            if (body.path(key).isNumber()) {
                return body.path(key).asInt(0);
            }
        }
        // 안전망: 개수 키가 없는 문법입니다.
        int total = 0;
        for (final String key : ARRAY_KEYS) {
            final tools.jackson.databind.JsonNode array = body.path(key);
            if (array.isArray()) {
                total += array.size();
            }
        }
        return total;
    }
}