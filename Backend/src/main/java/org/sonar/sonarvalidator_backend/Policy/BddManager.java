package org.sonar.sonarvalidator_backend.Policy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BDD 패키지의 노드를 만들고 연산하는 관리자입니다. (Batfish 의 심볼릭 패킷 집합 연산 대응)
 *
 * <h2>제공하는 것</h2>
 * <table border="1">
 *   <caption>연산</caption>
 *   <tr><th>메서드</th><th>의미</th></tr>
 *   <tr><td>{@link #variable(int)}</td><td>"변수 v 가 1" 인 술어</td></tr>
 *   <tr><td>{@link #and} / {@link #or} / {@link #xor}</td><td>집합 교집합 / 합집합 / 대칭차</td></tr>
 *   <tr><td>{@link #not}</td><td>여집합</td></tr>
 *   <tr><td>{@link #exists}</td><td>투영(사영). 변수 하나를 "무시" 합니다</td></tr>
 *   <tr><td>{@link #anySat}</td><td>만족하는 할당 하나 추출 = <b>반례 패킷</b></td></tr>
 *   <tr><td>{@link #satCount}</td><td>만족하는 할당의 개수 = 허용 조합 수</td></tr>
 * </table>
 *
 * <h2>구현 방식</h2>
 * <ul>
 *   <li><b>유일 테이블</b>: (변수, low, high) 가 같으면 노드를 공유합니다. 이 덕분에
 *       같은 부분 함수가 한 번만 만들어집니다.</li>
 *   <li><b>축약 규칙</b>: low == high 이면 그 노드는 필요 없으므로 자식을 그대로
 *       돌려줍니다. BDD 가 "축약" 되는 지점입니다.</li>
 *   <li><b>메모이제이션</b>: {@code apply} 와 {@code not} 결과를 캐시합니다.
 *       이것이 없으면 지수 시간이 걸립니다.</li>
 * </ul>
 *
 * <p>스레드 안전하지 않습니다. 요청 하나마다 새로 만들거나 호출자가 동기화하세요.
 * (분석 서비스는 규칙 집합 단위로 매니저를 새로 만듭니다.)
 */
public final class BddManager {

    /** 이항 연산 종류. */
    private enum Op {
        AND, OR, XOR
    }

    /** 유일 테이블 키. */
    private record UniqueKey(int variable, int lowId, int highId) {
    }

    /** apply 캐시 키. 교환 법칙을 쓰므로 좌/우를 정규화해 넣습니다. */
    private record ApplyKey(Op op, int leftId, int rightId) {
    }

    /** 노드 저장소 (id → 노드). */
    private final List<BddNode> nodes = new ArrayList<>();

    /** 유일 테이블. */
    private final Map<UniqueKey, BddNode> uniqueTable = new HashMap<>();

    /** not 결과 캐시. */
    private final Map<Integer, BddNode> notCache = new HashMap<>();

    /** apply 결과 캐시. */
    private final Map<ApplyKey, BddNode> applyCache = new HashMap<>();

    /** 지금까지 등장한 변수의 개수 (최대 변수 인덱스 + 1). */
    private int variableCount;

    /** 매니저를 만들고 상수 노드 두 개를 등록합니다. */
    public BddManager() {
        nodes.add(BddNode.FALSE);
        nodes.add(BddNode.TRUE);
    }

    // ------------------------------------------------------------------
    //  생성
    // ------------------------------------------------------------------

    /** @return 논리 상수 거짓 (공집합) */
    public BddNode zero() {
        return BddNode.FALSE;
    }

    /** @return 논리 상수 참 (전체 집합) */
    public BddNode one() {
        return BddNode.TRUE;
    }

    /**
     * "변수 {@code index} 가 1" 이라는 술어를 만듭니다.
     *
     * @param index 변수 인덱스 (0 이상)
     * @return 변수 노드
     */
    public BddNode variable(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("variable index must be >= 0: " + index);
        }
        if (index + 1 > variableCount) {
            variableCount = index + 1;
        }
        return mk(index, BddNode.FALSE, BddNode.TRUE);
    }

    /**
     * 노드를 만들거나, 이미 같은 노드가 있으면 그것을 돌려줍니다.
     *
     * <p>축약 규칙: {@code low == high} 이면 변수에 무관하므로 자식을 그대로 씁니다.
     *
     * @param index 분기 변수
     * @param low   변수 = 0 자식
     * @param high  변수 = 1 자식
     * @return 정규화된 노드
     */
    private BddNode mk(int index, BddNode low, BddNode high) {
        if (low == high) {
            return low;
        }
        final UniqueKey key = new UniqueKey(index, low.id(), high.id());
        final BddNode existing = uniqueTable.get(key);
        if (existing != null) {
            return existing;
        }
        final BddNode created = new BddNode(nodes.size(), index, low, high);
        nodes.add(created);
        uniqueTable.put(key, created);
        return created;
    }

    // ------------------------------------------------------------------
    //  단항 연산
    // ------------------------------------------------------------------

    /**
     * 여집합(논리 부정)을 구합니다.
     *
     * @param a 대상
     * @return ¬a
     */
    public BddNode not(BddNode a) {
        if (a.isTerminal()) {
            return a.isTrue() ? BddNode.FALSE : BddNode.TRUE;
        }
        final BddNode cached = notCache.get(a.id());
        if (cached != null) {
            return cached;
        }
        final BddNode result = mk(a.variable(), not(a.low()), not(a.high()));
        notCache.put(a.id(), result);
        return result;
    }

    // ------------------------------------------------------------------
    //  이항 연산
    // ------------------------------------------------------------------

    /**
     * 교집합을 구합니다.
     *
     * @param a 왼쪽
     * @param b 오른쪽
     * @return a ∧ b
     */
    public BddNode and(BddNode a, BddNode b) {
        return apply(Op.AND, a, b);
    }

    /**
     * 합집합을 구합니다.
     *
     * @param a 왼쪽
     * @param b 오른쪽
     * @return a ∨ b
     */
    public BddNode or(BddNode a, BddNode b) {
        return apply(Op.OR, a, b);
    }

    /**
     * 대칭차(한쪽에만 속하는 원소)를 구합니다.
     *
     * @param a 왼쪽
     * @param b 오른쪽
     * @return a ⊕ b
     */
    public BddNode xor(BddNode a, BddNode b) {
        return apply(Op.XOR, a, b);
    }

    /**
     * 목록의 합집합을 구합니다. 빈 목록은 공집합입니다.
     *
     * @param parts 합칠 노드들
     * @return ∪ parts
     */
    public BddNode orAll(List<BddNode> parts) {
        BddNode result = BddNode.FALSE;
        for (final BddNode part : parts) {
            result = or(result, part);
        }
        return result;
    }

    /**
     * 섀넌 전개(Shannon expansion)로 이항 연산을 계산합니다.
     *
     * <p>{@code f = (x ∧ f|x=1) ∨ (¬x ∧ f|x=0)} 이므로, 두 피연산자의 최상위
     * 변수 {@code x} 를 기준으로 자식을 나눠 재귀하면 됩니다.
     *
     * @param op 연산
     * @param a  왼쪽
     * @param b  오른쪽
     * @return 연산 결과
     */
    private BddNode apply(Op op, BddNode a, BddNode b) {
        // 단말끼리는 즉시 계산합니다.
        if (a.isTerminal() && b.isTerminal()) {
            final boolean left = a.isTrue();
            final boolean right = b.isTrue();
            final boolean value = switch (op) {
                case AND -> left && right;
                case OR -> left || right;
                case XOR -> left ^ right;
            };
            return value ? BddNode.TRUE : BddNode.FALSE;
        }

        // 교환 법칙을 이용해 캐시 적중률을 높입니다. (단말 id 가 0/1 이라 앞으로 옵니다)
        if (a.id() > b.id()) {
            final BddNode swap = a;
            a = b;
            b = swap;
        }

        final ApplyKey key = new ApplyKey(op, a.id(), b.id());
        final BddNode cached = applyCache.get(key);
        if (cached != null) {
            return cached;
        }

        final int index = Math.min(topVariable(a), topVariable(b));
        final BddNode result = mk(index,
                apply(op, cofactor(a, index, false), cofactor(b, index, false)),
                apply(op, cofactor(a, index, true), cofactor(b, index, true)));
        applyCache.put(key, result);
        return result;
    }

    /** 노드의 최상위 변수. 단말은 무한대로 두어 상대 변수가 선택되게 합니다. */
    private int topVariable(BddNode node) {
        return node.isTerminal() ? Integer.MAX_VALUE : node.variable();
    }

    /**
     * 특정 변수를 고정했을 때의 나머지 함수를 구합니다.
     *
     * @param node   대상
     * @param index  고정할 변수
     * @param high   {@code true} 면 변수 = 1, {@code false} 면 변수 = 0
     * @return 나머지 함수
     */
    private BddNode cofactor(BddNode node, int index, boolean high) {
        if (node.isTerminal()) {
            return node;
        }
        if (node.variable() == index) {
            return high ? node.high() : node.low();
        }
        // index 는 두 피연산자의 최소 변수이므로 node.variable() < index 인 경우는 없습니다.
        return node;
    }

    // ------------------------------------------------------------------
    //  투영 (사영)
    // ------------------------------------------------------------------

    /**
     * 변수 하나를 존재 양화합니다. (= 그 변수를 무시한 투영)
     *
     * <p>예를 들어 "출발지 × 목적지 × 포트" 집합에서 포트를 지우면
     * "출발지 × 목적지" 만 남아, 어떤 포트로든 통신이 가능한지를 묻는 집합이 됩니다.
     *
     * @param a       대상
     * @param index   지울 변수
     * @return ∃x. a
     */
    public BddNode exists(BddNode a, int index) {
        return or(cofactor(a, index, false), cofactor(a, index, true));
    }

    /**
     * 변수 여러 개를 한꺼번에 존재 양화합니다.
     *
     * @param a        대상
     * @param indices  지울 변수들
     * @return ∃x1..xn. a
     */
    public BddNode exists(BddNode a, Iterable<Integer> indices) {
        BddNode result = a;
        for (final int index : indices) {
            result = exists(result, index);
        }
        return result;
    }

    /**
     * 변수 하나를 전칭 양화합니다. (모든 값에 대해 참)
     *
     * @param a     대상
     * @param index 대상 변수
     * @return ∀x. a
     */
    public BddNode forAll(BddNode a, int index) {
        return not(exists(not(a), index));
    }

    // ------------------------------------------------------------------
    //  조회 / 진단
    // ------------------------------------------------------------------

    /** @return 지금까지 등장한 변수 개수 */
    public int variableCount() {
        return variableCount;
    }

    /** @return 지금까지 만들어진 고유 노드 개수 (BDD 크기 지표) */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * 특정 함수의 노드 개수를 셉니다. (BDD 압축률 지표)
     *
     * @param a 대상
     * @return 도달 가능한 고유 노드 수 (단말 포함)
     */
    public int nodeCount(BddNode a) {
        final Set<Integer> seen = new HashSet<>();
        collectNodes(a, seen);
        return seen.size();
    }

    /** 도달 가능한 노드 id 를 모읍니다. */
    private void collectNodes(BddNode node, Set<Integer> seen) {
        if (!seen.add(node.id())) {
            return;
        }
        if (!node.isTerminal()) {
            collectNodes(node.low(), seen);
            collectNodes(node.high(), seen);
        }
    }

    /** @return 함수가 쓰는 변수 인덱스 집합 */
    public Set<Integer> support(BddNode a) {
        final Set<Integer> result = new HashSet<>();
        collectVariables(a, result);
        return result;
    }

    /** 지지 집합을 모읍니다. */
    private void collectVariables(BddNode node, Set<Integer> out) {
        if (node.isTerminal()) {
            return;
        }
        if (!out.add(node.variable())) {
            return;
        }
        collectVariables(node.low(), out);
        collectVariables(node.high(), out);
    }

    /**
     * 만족하는 변수 할당을 하나 찾습니다. <b>정책 위반의 반례 패킷</b>을 꺼낼 때 씁니다.
     *
     * @param a 대상
     * @return 변수 개수만큼의 배열 (값은 0/1, 함수가 의존하지 않는 변수는 -1),
     *         만족하는 할당이 없으면 {@code null}
     */
    public int[] anySat(BddNode a) {
        if (a.isFalse()) {
            return null;
        }
        final int[] assignment = new int[Math.max(variableCount, 1)];
        Arrays.fill(assignment, -1);
        return satisfies(a, assignment) ? assignment : null;
    }

    /** 재귀로 만족 할당을 채웁니다. low 를 먼저 시도해 0 을 선호합니다. */
    private boolean satisfies(BddNode node, int[] assignment) {
        if (node.isFalse()) {
            return false;
        }
        if (node.isTrue()) {
            return true;
        }
        assignment[node.variable()] = 0;
        if (satisfies(node.low(), assignment)) {
            return true;
        }
        assignment[node.variable()] = 1;
        if (satisfies(node.high(), assignment)) {
            return true;
        }
        assignment[node.variable()] = -1;
        return false;
    }

    /**
     * 만족하는 할당의 개수를 셉니다. (주소 공간 대비 허용 조합 수)
     *
     * <p>BDD 가 의존하지 않는 변수는 0/1 어느 쪽이든 되므로 2배씩 곱합니다.
     *
     * @param a 대상
     * @return 만족 할당 수 (최대 2^{@link #variableCount()})
     */
    public BigInteger satCount(BddNode a) {
        return satCount(a, 0, new HashMap<>());
    }

    /** 레벨 단위로 내려가며 개수를 셉니다. */
    private BigInteger satCount(BddNode node, int level, Map<Long, BigInteger> cache) {
        if (level >= variableCount) {
            return node.isTrue() ? BigInteger.ONE : BigInteger.ZERO;
        }
        if (node.isTerminal()) {
            return node.isTrue()
                    ? BigInteger.TWO.pow(variableCount - level)
                    : BigInteger.ZERO;
        }
        if (node.variable() > level) {
            return BigInteger.TWO.multiply(satCount(node, level + 1, cache));
        }
        final long key = ((long) node.id() << 20) | level;
        final BigInteger cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        final BigInteger result = satCount(node.low(), level + 1, cache)
                .add(satCount(node.high(), level + 1, cache));
        cache.put(key, result);
        return result;
    }
}
