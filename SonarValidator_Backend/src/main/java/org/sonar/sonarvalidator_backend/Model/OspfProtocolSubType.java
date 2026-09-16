package org.sonar.sonarvalidator_backend.Model;

// OSPF 라우터에 NSSA(Not-So-Stubby Area) 등의 기능을 쓰기위해 만들었다고 함.
public enum OspfProtocolSubType {
    OSPF, /** OSPF Intra-Area 경로 (영역 내부) */
    OSPF_IA,     /** OSPF Inter-Area 경로 (영역 간) */
    OSPF_E1,    /** OSPF External Type 1 (외부 타입 1) */
    OSPF_E2,    /** OSPF External Type 2 (외부 타입 2) */
    OSPF_N1,    /** OSPF NSSA External Type 1 */
    OSPF_N2;   /** OSPF NSSA External Type 2 */

    public boolean isOspf() {
        return this == OSPF
                || this == OSPF_IA
                || this == OSPF_E1
                || this == OSPF_E2
                || this == OSPF_N1
                || this == OSPF_N2;
    }
}
