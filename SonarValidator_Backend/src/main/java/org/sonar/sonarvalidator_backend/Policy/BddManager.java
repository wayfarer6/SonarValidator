package org.sonar.sonarvalidator_backend.Policy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BddManager {

    private enum Op {
        AND, OR, XOR
    }

    private record UniqueKey(int variable, int lowId, int highId) {
    }

    private record ApplyKey(Op op, int leftId, int rightId) {
    }

    private final List<BddNode> nodes = new ArrayList<>();

    private final Map<UniqueKey, BddNode> uniqueTable = new HashMap<>();

    private final Map<Integer, BddNode> notCache = new HashMap<>();

    private final Map<ApplyKey, BddNode> applyCache = new HashMap<>();

    private int variableCount;

    public BddManager() {
        nodes.add(BddNode.FALSE);
        nodes.add(BddNode.TRUE);
    }


    public BddNode zero() {
        return BddNode.FALSE;
    }

    public BddNode one() {
        return BddNode.TRUE;
    }

    public BddNode variable(int index) {
        if (index < 0 || index == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("variable index must be in [0, Integer.MAX_VALUE): " + index);
        }
        if (index + 1 > variableCount) {
            variableCount = index + 1;
        }
        return mk(index, BddNode.FALSE, BddNode.TRUE);
    }

    private void requireOwned(BddNode node) {
        if (node == null || node.id() < 0 || node.id() >= nodes.size() || nodes.get(node.id()) != node) {
            throw new IllegalArgumentException("BDD node belongs to a different manager");
        }
    }

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


    public BddNode not(BddNode a) {
        requireOwned(a);
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


    public BddNode and(BddNode a, BddNode b) {
        requireOwned(a);
        requireOwned(b);
        return apply(Op.AND, a, b);
    }

    public BddNode or(BddNode a, BddNode b) {
        requireOwned(a);
        requireOwned(b);
        return apply(Op.OR, a, b);
    }

    public BddNode xor(BddNode a, BddNode b) {
        requireOwned(a);
        requireOwned(b);
        return apply(Op.XOR, a, b);
    }

    public BddNode orAll(List<BddNode> parts) {
        BddNode result = BddNode.FALSE;
        for (final BddNode part : parts) {
            result = or(result, part);
        }
        return result;
    }

    private BddNode apply(Op op, BddNode a, BddNode b) {
        if (a == b) return op == Op.XOR ? zero() : a;
        if (op == Op.AND && (a.isFalse() || b.isFalse())) return zero();
        if (op == Op.AND && a.isTrue()) return b;
        if (op == Op.AND && b.isTrue()) return a;
        if (op == Op.OR && (a.isTrue() || b.isTrue())) return one();
        if ((op == Op.OR || op == Op.XOR) && a.isFalse()) return b;
        if ((op == Op.OR || op == Op.XOR) && b.isFalse()) return a;
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

    private int topVariable(BddNode node) {
        return node.isTerminal() ? Integer.MAX_VALUE : node.variable();
    }

    private BddNode cofactor(BddNode node, int index, boolean high) {
        if (node.isTerminal()) {
            return node;
        }
        if (node.variable() == index) {
            return high ? node.high() : node.low();
        }
        return node;
    }


    public BddNode exists(BddNode a, int index) {
        requireOwned(a);
        if (index < 0) throw new IllegalArgumentException("variable index must be >= 0");
        return exists(a, index, new HashMap<>());
    }

    private BddNode exists(BddNode node, int index, Map<Integer, BddNode> cache) {
        if (node.isTerminal() || node.variable() > index) return node;
        if (node.variable() == index) return or(node.low(), node.high());
        final BddNode cached = cache.get(node.id());
        if (cached != null) return cached;
        final BddNode result = mk(node.variable(), exists(node.low(), index, cache), exists(node.high(), index, cache));
        cache.put(node.id(), result);
        return result;
    }

    public BddNode exists(BddNode a, Iterable<Integer> indices) {
        requireOwned(a);
        BddNode result = a;
        for (final int index : indices) {
            result = exists(result, index);
        }
        return result;
    }

    public BddNode forAll(BddNode a, int index) {
        return not(exists(not(a), index));
    }


    public int variableCount() {
        return variableCount;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int nodeCount(BddNode a) {
        requireOwned(a);
        final Set<Integer> seen = new HashSet<>();
        collectNodes(a, seen);
        return seen.size();
    }

    private void collectNodes(BddNode node, Set<Integer> seen) {
        if (!seen.add(node.id())) {
            return;
        }
        if (!node.isTerminal()) {
            collectNodes(node.low(), seen);
            collectNodes(node.high(), seen);
        }
    }

    public Set<Integer> support(BddNode a) {
        requireOwned(a);
        final Set<Integer> result = new HashSet<>();
        collectVariables(a, result, new HashSet<>());
        return result;
    }

    private void collectVariables(BddNode node, Set<Integer> out, Set<Integer> visited) {
        if (node.isTerminal()) {
            return;
        }
        if (!visited.add(node.id())) return;
        out.add(node.variable());
        collectVariables(node.low(), out, visited);
        collectVariables(node.high(), out, visited);
    }

    public int[] anySat(BddNode a) {
        requireOwned(a);
        if (a.isFalse()) {
            return null;
        }
        final int[] assignment = new int[Math.max(variableCount, 1)];
        Arrays.fill(assignment, -1);
        return satisfies(a, assignment) ? assignment : null;
    }

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

    public BigInteger satCount(BddNode a) {
        requireOwned(a);
        return satCount(a, 0, new HashMap<>());
    }

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
            return satCount(node, node.variable(), cache).shiftLeft(node.variable() - level);
        }
        final long key = ((long) node.id() << 32) | level;
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
