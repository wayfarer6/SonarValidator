package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;

/**
 * {@link PolicySubnet#isValidCidr(String)} 의 저장 경계 검증 테스트입니다.
 *
 * <h2>왜 이 테스트가 필요한가</h2>
 * <p>예전에는 {@code normalizeCidr()} 가 파싱 예외를 삼키고 잘못된 원문을
 * 그대로 돌려주었습니다. 그래서 {@code 10.10.131.999/24} 같은 값이 200 으로
 * 통과하고 위반 판정에 다시 쓰였습니다(BUG-CIDR-VALIDATION-001). 저장/검증
 * 경계가 이 값들을 명시적으로 거부하는지 고정합니다.
 */
class PolicySubnetCidrValidationTest {

    @Test
    @DisplayName("형식이 깨진 대역은 모두 거부한다")
    void rejectsMalformedCidrs() {
        assertThat(PolicySubnet.isValidCidr("10.10.131.999/24")).isFalse();
        assertThat(PolicySubnet.isValidCidr("10.10.131.0/33")).isFalse();
        assertThat(PolicySubnet.isValidCidr("10.10.131/24")).isFalse();
        assertThat(PolicySubnet.isValidCidr("not-an-ip/24")).isFalse();
        // 슬래시 중복
        assertThat(PolicySubnet.isValidCidr("10.0.0.1/24/extra")).isFalse();
        // 템플릿 표기(x 옥텟)는 실제 대역이 아니다.
        assertThat(PolicySubnet.isValidCidr("192.168.0.x/24")).isFalse();
        // prefix 누락 / 비어 있음 / null
        assertThat(PolicySubnet.isValidCidr("10.10.131.0/")).isFalse();
        assertThat(PolicySubnet.isValidCidr("")).isFalse();
        assertThat(PolicySubnet.isValidCidr("   ")).isFalse();
        assertThat(PolicySubnet.isValidCidr(null)).isFalse();
    }

    @Test
    @DisplayName("정상 경계값은 수락하고 호스트 비트는 정규화한다")
    void acceptsBoundaryValues() {
        assertThat(PolicySubnet.isValidCidr("0.0.0.0/0")).isTrue();
        assertThat(PolicySubnet.isValidCidr("10.10.131.7/32")).isTrue();
        assertThat(PolicySubnet.isValidCidr("10.10.131.7/24")).isTrue();
        assertThat(PolicySubnet.normalizeCidr("10.10.131.7/24")).isEqualTo("10.10.131.0/24");
    }
}
