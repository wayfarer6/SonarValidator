package org.sonar.sonarvalidator_backend.Policy.advice;

/**
 * AI 가 제시한 <b>해결 선택지 한 가지</b>입니다.
 *
 * <h2>왜 "해결책"이 아니라 "선택지"인가</h2>
 * <p>망분리 위반의 해결은 하나로 정해지지 않습니다. 같은 위반이라도
 * <b>등급 재분류</b>(빠르지만 등급 체계가 흔들림)와 <b>중계 구간 신설</b>
 * (안전하지만 장비·비용이 듦)이 모두 유효합니다. 운영자가 트레이드오프를
 * 보고 고르려면, 각 안의 <b>보안 영향과 운영 부담</b>이 함께 있어야 합니다.
 *
 * <p>하나만 제시하면 운영자는 다른 선택이 있었다는 사실을 모른 채
 * 보안을 약화시키는 방향으로 갑니다.
 *
 * @param title           선택지 이름 (예: {@code 등급 재분류})
 * @param approach        구체적으로 무엇을 어떻게 바꾸는지
 * @param securityImpact  보안에 미치는 영향
 * @param operationalCost 운영 부담 (재작업·중단·비용)
 * @param recommended     AI 가 권하는 안인지
 */
public record PolicyAdviceOption(String title,
                                 String approach,
                                 String securityImpact,
                                 String operationalCost,
                                 boolean recommended) {

    /**
     * 필수 값이 비어 있는지 확인합니다.
     *
     * <p>모델이 키는 만들고 값을 비우는 경우가 있습니다. 빈 카드를 화면에
     * 띄우면 운영자는 "내용이 없는 조언" 을 보게 되므로 걸러냅니다.
     *
     * @return 제목과 실행 방법이 모두 있으면 true
     */
    public boolean isUsable() {
        return title != null && !title.isBlank()
                && approach != null && !approach.isBlank();
    }
}