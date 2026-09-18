package org.sonar.sonarvalidator_backend.Policy;

/**
 * 축약 순서 이진 결정 다이어그램(ROBDD)의 노드 한 개입니다.
 *
 * <h2>왜 BDD 인가 (Batfish 참고)</h2>
 * <p>Batfish 는 네트워크 도달성 분석을 할 때 패킷 집합을 <b>BDD</b> 로 표현합니다.
 * ACL 규칙 하나는 "출발지 주소 집합 x 목적지 주소 집합 x 포트 집합"이라는
 * 집합의 곱이므로, 규칙 전체는 그 합집합이 됩니다. 합집합/교집합/여집합을
 * 집합 연산으로 계산할 수 있으면 "정책 위반 패킷"은
 * <b>위반 = (허용 규칙 합집합) ∩ (금지 대역)</b> 으로 한 번에 구해집니다.
 *
 * <p>IP 주소를 32비트 리터럴로 하나씩 열거하면 주소 공간이 2^32 라 불가능하지만,
 * BDD 는 중복 노드를 공유하므로 실제로는 <b>규칙 수에 비례하는 크기</b> 로
 * 압축됩니다.
 *
 * <h2>불변 조건</h2>
 * <ul>
 *   <li>노드는 {@link BddManager} 가 유일 테이블(unique table)로 <b>intern</b> 합니다.
 *       그래서 같은 논리 함수는 항상 같은 객체(참조 동일)입니다.</li>
 *   <li>변수 순서는 고정입니다. 간선은 변수 {@code variable} 이 0일 때
 *       {@link #low()}, 1일 때 {@link #high()} 를 따릅니다.</li>
 *   <li>단말 노드는 {@link #FALSE}(id 0)와 {@link #TRUE}(id 1) 뿐입니다.</li>
 * </ul>
 *
 * <p><b>주의</b>: 단말이 아닌 노드는 반드시 자신을 만든 매니저와 함께 쓰세요.
 * 서로 다른 매니저의 노드를 섞으면 노드 공유가 깨져 결과가 틀립니다.
 */
public final class BddNode {

    /** 논리 상수 거짓 (모든 변수 할당에서 0). */
    public static final BddNode FALSE = new BddNode(0, -1, null, null);

    /** 논리 상수 참 (모든 변수 할당에서 1). */
    public static final BddNode TRUE = new BddNode(1, -1, null, null);

    /** 고유 식별자. 매니저 안에서만 유일합니다. */
    private final int id;

    /** 분기 변수 인덱스. 단말이면 -1. */
    private final int variable;

    /** 변수가 0 일 때의 자식. 단말이면 null. */
    private final BddNode low;

    /** 변수가 1 일 때의 자식. 단말이면 null. */
    private final BddNode high;

    /**
     * @param id       고유 식별자
     * @param variable 분기 변수 (-1 이면 단말)
     * @param low      변수 = 0 자식
     * @param high     변수 = 1 자식
     */
    BddNode(int id, int variable, BddNode low, BddNode high) {
        this.id = id;
        this.variable = variable;
        this.low = low;
        this.high = high;
    }

    /** @return 고유 식별자 */
    public int id() {
        return id;
    }

    /** @return 분기 변수 인덱스 (단말이면 -1) */
    public int variable() {
        return variable;
    }

    /** @return 변수 = 0 자식 (단말이면 null) */
    public BddNode low() {
        return low;
    }

    /** @return 변수 = 1 자식 (단말이면 null) */
    public BddNode high() {
        return high;
    }

    /** @return 단말 노드이면 {@code true} */
    public boolean isTerminal() {
        return variable < 0;
    }

    /** @return 논리 상수 참이면 {@code true} */
    public boolean isTrue() {
        return id == 1;
    }

    /** @return 논리 상수 거짓이면 {@code true} */
    public boolean isFalse() {
        return id == 0;
    }

    @Override
    public String toString() {
        if (isTrue()) {
            return "TRUE";
        }
        if (isFalse()) {
            return "FALSE";
        }
        return "node#" + id + "(x" + variable + ")";
    }
}
