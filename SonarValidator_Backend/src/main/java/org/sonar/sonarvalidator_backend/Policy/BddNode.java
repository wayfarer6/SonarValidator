package org.sonar.sonarvalidator_backend.Policy;

public final class BddNode {

    public static final BddNode FALSE = new BddNode(0, -1, null, null);

    public static final BddNode TRUE = new BddNode(1, -1, null, null);

    private final int id;

    private final int variable;

    private final BddNode low;

    private final BddNode high;

    BddNode(int id, int variable, BddNode low, BddNode high) {
        this.id = id;
        this.variable = variable;
        this.low = low;
        this.high = high;
    }

    public int id() {
        return id;
    }

    public int variable() {
        return variable;
    }

    public BddNode low() {
        return low;
    }

    public BddNode high() {
        return high;
    }

    public boolean isTerminal() {
        return variable < 0;
    }

    public boolean isTrue() {
        return id == 1;
    }

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
