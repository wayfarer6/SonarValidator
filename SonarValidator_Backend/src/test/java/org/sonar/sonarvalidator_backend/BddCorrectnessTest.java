package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Policy.*;

class BddCorrectnessTest {
    @Test
    void everyThreeVariableFunctionMatchesItsTruthTable() {
        var manager = new BddManager();
        var variables = new BddNode[]{manager.variable(0), manager.variable(1), manager.variable(2)};
        var functions = new BddNode[256];
        for (int mask = 0; mask < 256; mask++) {
            var function = manager.zero();
            for (int value = 0; value < 8; value++) {
                if ((mask & (1 << value)) == 0) continue;
                var term = manager.one();
                for (int bit = 0; bit < 3; bit++) {
                    term = manager.and(term, (value & (1 << bit)) == 0
                            ? manager.not(variables[bit]) : variables[bit]);
                }
                function = manager.or(function, term);
            }
            functions[mask] = function;
        }
        for (int mask = 0; mask < 256; mask++) {
            var function = functions[mask];
            assertThat(manager.not(function)).isSameAs(functions[mask ^ 255]);
            assertThat(manager.satCount(function)).isEqualTo(BigInteger.valueOf(Integer.bitCount(mask)));
            var support = new HashSet<Integer>();
            for (int bit = 0; bit < 3; bit++) {
                int existential = 0;
                int universal = 0;
                for (int value = 0; value < 8; value++) {
                    boolean low = (mask & (1 << (value & ~(1 << bit)))) != 0;
                    boolean high = (mask & (1 << (value | (1 << bit)))) != 0;
                    if (low || high) existential |= 1 << value;
                    if (low && high) universal |= 1 << value;
                    if (low != high) support.add(bit);
                }
                assertThat(manager.exists(function, bit)).as("exists mask=%s bit=%s", mask, bit)
                        .isSameAs(functions[existential]);
                assertThat(manager.forAll(function, bit)).as("forall mask=%s bit=%s", mask, bit)
                        .isSameAs(functions[universal]);
            }
            assertThat(manager.support(function)).as("support mask=%s", mask).isEqualTo(support);
            var sample = manager.anySat(function);
            if (mask == 0) {
                assertThat(sample).isNull();
            } else {
                int value = 0;
                for (int bit = 0; bit < 3; bit++) if (sample[bit] == 1) value |= 1 << bit;
                assertThat(mask & (1 << value)).isNotZero();
            }
            for (int right = 0; right < 256; right++) {
                assertThat(manager.and(function, functions[right])).isSameAs(functions[mask & right]);
                assertThat(manager.or(function, functions[right])).isSameAs(functions[mask | right]);
                assertThat(manager.xor(function, functions[right])).isSameAs(functions[mask ^ right]);
            }
        }
    }

    @Test
    void supportVisitsDistinctNodesAtTheSameVariable() {
        var manager = new BddManager();
        var x = manager.variable(0);
        var y = manager.variable(1);
        var z = manager.variable(2);
        var w = manager.variable(3);
        var function = manager.or(manager.and(manager.not(x), manager.and(y, z)),
                manager.and(x, manager.and(y, w)));
        assertThat(manager.support(function)).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void foreignNodesCannotAliasLocalNodeIds() {
        var local = new BddManager();
        var own = local.variable(0);
        var foreign = new BddManager().variable(1);
        assertThatThrownBy(() -> local.and(own, foreign)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> local.not(foreign)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> local.exists(foreign, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> local.anySat(foreign)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> local.satCount(foreign)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> local.support(foreign)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partialPortWitnessKeepsBitPositions() {
        var manager = PacketVariables.newManager();
        var ports = manager.or(PacketVariables.port(manager, 256), PacketVariables.port(manager, 257));
        int port = PacketVariables.portOf(manager.anySat(ports));
        assertThat(port).isIn(256, 257);
        assertThat(manager.and(ports, PacketVariables.port(manager, port)).isFalse()).isFalse();
    }

    @Test
    void partiallyConstrainedProtocolHasConcreteRepresentative() {
        int[] assignment = new int[PacketVariables.TOTAL_BITS];
        Arrays.fill(assignment, -1);
        assignment[PacketVariables.PROTOCOL_OFFSET] = 1;
        assertThat(PacketVariables.protocolOf(assignment)).isEqualTo("ip-protocol-128");
    }

    @Test
    void malformedAddressSyntaxIsRejected() {
        for (String cidr : List.of("10.0.0.0/24/garbage", "10.0.0.0/", "10.0.0.0/+24",
                "10.0.0.0./24", "1x.0.0.0/24", "10.0.0.0/24/")) {
            assertThatThrownBy(() -> PacketVariables.parseCidr(cidr)).as(cidr)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
