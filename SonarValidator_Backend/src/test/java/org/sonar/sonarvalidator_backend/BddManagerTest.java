package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Policy.BddManager;
import org.sonar.sonarvalidator_backend.Policy.BddNode;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

/**
 * BDD 패키지 자체의 정확성 테스트입니다.
 *
 * <p>엔진 테스트가 "정책 판정이 맞는가" 를 본다면, 여기서는 <b>집합 연산이
 * 수학적으로 맞는가</b> 를 봅니다. 이 계층이 틀리면 엔진 결과도 조용히
 * 틀리기 때문에 별도로 검증합니다.
 */
class BddManagerTest {

    @Test
    @DisplayName("상수 노드는 축약 규칙으로 즉시 결정된다")
    void terminalShortcuts() {
        final BddManager manager = new BddManager();
        final BddNode x = manager.variable(0);

        assertThat(manager.and(x, manager.zero()).isFalse()).isTrue();
        assertThat(manager.or(x, manager.one()).isTrue()).isTrue();
        assertThat(manager.not(manager.zero()).isTrue()).isTrue();
        assertThat(manager.not(manager.one()).isFalse()).isTrue();
        assertThat(manager.xor(x, x).isFalse()).isTrue();
    }

    @Test
    @DisplayName("같은 논리 함수는 같은 노드로 공유된다")
    void uniqueTableSharesNodes() {
        final BddManager manager = new BddManager();
        final BddNode a = manager.and(manager.variable(0), manager.variable(1));
        final BddNode b = manager.and(manager.variable(0), manager.variable(1));

        assertThat(a).isSameAs(b);
    }

    @Test
    @DisplayName("드모르간 법칙이 성립한다")
    void deMorgan() {
        final BddManager manager = new BddManager();
        final BddNode x = manager.variable(0);
        final BddNode y = manager.variable(1);

        final BddNode left = manager.not(manager.and(x, y));
        final BddNode right = manager.or(manager.not(x), manager.not(y));
        assertThat(manager.xor(left, right).isFalse()).isTrue();
    }

    @Test
    @DisplayName("분배 법칙이 성립한다")
    void distributivity() {
        final BddManager manager = new BddManager();
        final BddNode x = manager.variable(0);
        final BddNode y = manager.variable(1);
        final BddNode z = manager.variable(2);

        final BddNode left = manager.and(x, manager.or(y, z));
        final BddNode right = manager.or(manager.and(x, y), manager.and(x, z));
        assertThat(manager.xor(left, right).isFalse()).isTrue();
    }

    @Test
    @DisplayName("존재 양화는 변수를 무시한 투영이다")
    void existsProjectsVariable() {
        final BddManager manager = new BddManager();
        final BddNode x = manager.variable(0);
        final BddNode y = manager.variable(1);

        // ∃x. (x ∧ y) == y
        final BddNode projected = manager.exists(manager.and(x, y), 0);
        assertThat(manager.xor(projected, y).isFalse()).isTrue();
    }

    @Test
    @DisplayName("만족 할당이 없으면 anySat 은 null 이다")
    void anySatReturnsNullWhenUnsatisfiable() {
        final BddManager manager = new BddManager();
        final BddNode x = manager.variable(0);

        assertThat(manager.anySat(manager.and(x, manager.not(x)))).isNull();
    }

    @Test
    @DisplayName("만족 할당 수가 정확히 계산된다 (매니저의 전체 변수 기준)")
    void satCountIsExact() {
        final BddManager manager = PacketVariables.newManager();

        // 변수 0 만 고정하고 나머지 79개는 자유 → 2^79
        final BddNode single = manager.variable(0);
        assertThat(manager.satCount(single))
                .isEqualTo(java.math.BigInteger.TWO.pow(PacketVariables.TOTAL_BITS - 1));

        // 전체 집합은 2^80
        assertThat(manager.satCount(manager.one()))
                .isEqualTo(java.math.BigInteger.TWO.pow(PacketVariables.TOTAL_BITS));
        // 공집합은 0
        assertThat(manager.satCount(manager.zero())).isEqualTo(java.math.BigInteger.ZERO);
    }

    @Test
    @DisplayName("변수를 등록하지 않으면 방문한 변수만 세므로 결과가 달라진다")
    void satCountDependsOnRegisteredVariables() {
        // 매니저가 아는 변수가 1개뿐이면 전체 공간이 2^1 이므로,
        // "x0 = 1" 을 만족하는 할당은 1가지로 셉니다.
        // 이 차이 때문에 검증 엔진은 항상 변수 80개를 미리 등록합니다.
        final BddManager manager = new BddManager();
        assertThat(manager.satCount(manager.variable(0))).isEqualTo(java.math.BigInteger.ONE);

        // 변수 80개 매니저에서 같은 함수는 2^79 가지입니다.
        final BddManager full = PacketVariables.newManager();
        assertThat(full.satCount(full.variable(0)))
                .isEqualTo(java.math.BigInteger.TWO.pow(PacketVariables.TOTAL_BITS - 1));
    }

    @Test
    @DisplayName("CIDR 접두사가 정확한 대역 크기를 만든다")
    void cidrPrefixProducesExpectedSpace() {
        final BddManager manager = PacketVariables.newManager();
        final BddNode cidr = PacketVariables.cidr(manager, "10.10.131.0/24", PacketVariables.SRC_IP_OFFSET);

        // 출발지 하위 8비트 + 목적지 32비트 + 포트 16비트가 자유 → 2^56
        final int freeSrcBits = PacketVariables.IP_BITS - 24;
        final int freeBits = freeSrcBits + PacketVariables.IP_BITS + PacketVariables.PORT_BITS;
        assertThat(manager.satCount(cidr))
                .isEqualTo(java.math.BigInteger.TWO.pow(freeBits));

        // /0 은 아무 비트도 고정하지 않으므로 "모든 패킷" 과 같습니다.
        // 규칙의 최종 집합은 목적지/포트 집합과의 교집합에서 좁혀집니다.
        final BddNode any = PacketVariables.cidr(manager, "0.0.0.0/0", PacketVariables.SRC_IP_OFFSET);
        assertThat(any.isTrue()).isTrue();
        assertThat(manager.satCount(any))
                .isEqualTo(java.math.BigInteger.TWO.pow(PacketVariables.TOTAL_BITS));
    }

    @Test
    @DisplayName("변수 하나만 자유롭게 둔 함수도 정확히 셈해진다")
    void satCountHandlesSingleFreeVariable() {
        // 80 변수 매니저에서 "나머지 79개를 전부 고정" 한 형태를 만듭니다.
        final BddManager manager = PacketVariables.newManager();
        BddNode fixed = manager.one();
        for (int index = 1; index < PacketVariables.TOTAL_BITS; index++) {
            fixed = manager.and(fixed, manager.not(manager.variable(index)));
        }
        // 변수 0 만 자유 → 2
        assertThat(manager.satCount(fixed)).isEqualTo(java.math.BigInteger.TWO);
    }

    @Test
    @DisplayName("접두사 밖 비트는 제약하지 않는다")
    void prefixDoesNotConstrainHostBits() {
        final BddManager manager = PacketVariables.newManager();
        final BddNode cidr = PacketVariables.cidr(manager, "192.168.10.0/24", PacketVariables.SRC_IP_OFFSET);

        // 같은 /24 안의 다른 주소 두 개가 모두 만족되어야 합니다.
        final BddNode first = PacketVariables.cidr(manager, "192.168.10.5/32", PacketVariables.SRC_IP_OFFSET);
        final BddNode second = PacketVariables.cidr(manager, "192.168.10.200/32", PacketVariables.SRC_IP_OFFSET);

        assertThat(manager.and(cidr, first).isFalse()).isFalse();
        assertThat(manager.and(cidr, second).isFalse()).isFalse();
    }

    @Test
    @DisplayName("x 옥텟 표기를 0 으로 흡수한다")
    void toleratesPlaceholderOctet() {
        final PacketVariables.ParsedCidr parsed = PacketVariables.parseCidr("192.168.0.x/24");

        assertThat(parsed.prefixLength()).isEqualTo(24);
        assertThat(parsed.address()).isEqualTo(PacketVariables.parseIp("192.168.0.0"));
    }

    @Test
    @DisplayName("잘못된 입력은 예외로 알린다")
    void invalidInputThrows() {
        assertThatThrownBy(() -> PacketVariables.parseCidr("10.0.0.0/33"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PacketVariables.parseIp("999.1.1.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PacketVariables.parseIp("not-an-ip"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BddManager().variable(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("등급 인접 판정: 차이가 1 이하면 허용, 2 이상이면 금지")
    void zoneAdjacencyRules() {
        assertThat(ZoneClass.allowsDirectConnection(ZoneClass.OPEN, ZoneClass.SENSITIVE)).isTrue();
        assertThat(ZoneClass.allowsDirectConnection(ZoneClass.SENSITIVE, ZoneClass.CONFIDENTIAL)).isTrue();
        assertThat(ZoneClass.allowsDirectConnection(ZoneClass.CONFIDENTIAL, ZoneClass.CONFIDENTIAL)).isTrue();

        assertThat(ZoneClass.forbidsDirectConnection(ZoneClass.CONFIDENTIAL, ZoneClass.OPEN)).isTrue();
        assertThat(ZoneClass.forbidsDirectConnection(ZoneClass.OPEN, ZoneClass.CONFIDENTIAL)).isTrue();

        // 등급을 모르면 판정하지 않습니다. (오탐 방지)
        assertThat(ZoneClass.allowsDirectConnection(null, ZoneClass.OPEN)).isTrue();
    }
}
