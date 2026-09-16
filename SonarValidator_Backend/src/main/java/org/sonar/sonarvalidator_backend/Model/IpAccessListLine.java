package org.sonar.sonarvalidator_backend.Model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IpAccessListLine implements AclLineMatchExpr{
    private String _name;
    private LineAction _action;
    private AclLineMatchExpr _matchCondition;

    @Override
    public boolean match() {
        return false;
    }
}
/*
 * AclLineMatchExpr (매칭 조건 표현식)
 * 출발지/목적지 IP(OriginatingIp, DestinationIp),
 * 포트(DestinationPort), TCP 플래그 등 복잡한 패킷 필터링 조건을
 * 트리 구조로 평가하기 위한 인터페이스/추상 클래스 계층입니다.
 */