package org.sonar.sonarvalidator_backend.Policy.strategy;

import org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

/**
 * 정책 규칙을 만들 때 전략에 넘기는 <b>읽기 전용 입력</b>입니다.
 *
 * <h2>⚠️ 왜 파라미터를 묶는가</h2>
 * <p>규칙 생성 메서드가 받는 값이 6~8개가 되면서 호출부마다 인자 순서가
 * 달라지는 실수가 생겼습니다. 값들을 하나로 묶으면 순서 문제가 사라지고,
 * 전략 인터페이스가 <b>한 개의 파라미터</b>만 받아 시그니처가 안정됩니다.
 *
 * <h2>{@code connection} 이 null 일 수 있다</h2>
 * <p>{@link #declarationRule} 경로(장치 선언)에는 연결이 없습니다.
 * 그래서 {@code connection} 은 null 을 허용하고, 연결이 필요한
 * {@link #enforcementRule} 경로에서만 채워집니다.
 *
 * @param subnet         이 장치에 배정된 서브넷
 * @param vendor         텔레메트리에서 관측된 벤더 (null 이면 전략 기본값)
 * @param product        텔레메트리에서 관측된 제품명 (null 이면 전략 기본값)
 * @param connection     연결 한 건 (선언 경로에서는 null)
 */
public record PolicyBuildContext(
        ProjectSubnet subnet,
        String vendor,
        String product,
        ConnectionView connection) {

    /**
     * 연결 한 건의 <b>읽기 전용 뷰</b>입니다.
     *
     * <p>이전에는 {@code PolicyRegistryService} 의 private record 였습니다.
     * 전략이 참조해야 하므로 여기로 옮겼습니다.
     *
     * @param ruleId          규칙 식별자
     * @param outgoing        이 장치의 서브넷이 출발지인지 여부
     * @param peerId          상대 서브넷 식별자
     * @param peerCidr        상대 서브넷 대역
     * @param peerClass       상대 서브넷 등급
     * @param sourceCidr      출발 대역
     * @param destinationCidr 도착 대역
     * @param protocol        프로토콜
     * @param port            허용 포트 (null 이면 미지정)
     * @param forbidden       등급을 건너뛰는 금지 연결인지 여부
     * @param reason          사람이 읽는 판정 사유
     */
    public record ConnectionView(String ruleId,
                                 boolean outgoing,
                                 String peerId,
                                 String peerCidr,
                                 ZoneClass peerClass,
                                 String sourceCidr,
                                 String destinationCidr,
                                 String protocol,
                                 Integer port,
                                 boolean forbidden,
                                 String reason) {
    }

    /**
     * 선언 규칙용 컨텍스트를 만듭니다. (연결 없음)
     *
     * @param subnet  배정된 서브넷
     * @param vendor  관측 벤더 (null 허용)
     * @param product 관측 제품명 (null 허용)
     * @return 컨텍스트
     */
    public static PolicyBuildContext forDeclaration(ProjectSubnet subnet,
                                                    String vendor,
                                                    String product) {
        return new PolicyBuildContext(subnet, vendor, product, null);
    }

    /**
     * 연결 규칙용 컨텍스트를 만듭니다.
     *
     * @param subnet     배정된 서브넷
     * @param vendor     관측 벤더 (null 허용)
     * @param product    관측 제품명 (null 허용)
     * @param connection 연결
     * @return 컨텍스트
     */
    public static PolicyBuildContext forConnection(ProjectSubnet subnet,
                                                   String vendor,
                                                   String product,
                                                   ConnectionView connection) {
        return new PolicyBuildContext(subnet, vendor, product, connection);
    }

    /**
     * 이 전략이 쓸 벤더명을 정합니다.
     *
     * @param fallback 전략의 기본 벤더명
     * @return 관측 벤더가 있으면 그것, 없으면 기본값
     */
    public String vendorOr(String fallback) {
        return PolicyJson.firstNonBlank(vendor, fallback);
    }

    /**
     * 이 전략이 쓸 제품명을 정합니다.
     *
     * @param fallback 전략의 기본 제품명
     * @return 관측 제품명이 있으면 그것, 없으면 기본값
     */
    public String productOr(String fallback) {
        return PolicyJson.firstNonBlank(product, fallback);
    }
}