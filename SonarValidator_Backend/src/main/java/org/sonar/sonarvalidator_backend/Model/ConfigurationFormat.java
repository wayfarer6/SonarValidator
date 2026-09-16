package org.sonar.sonarvalidator_backend.Model;

//  어떤 벤더 및 OS 규격인지 판별하여 정의해 두는 열거형(Enum) 클래스
// 나머지 벤더는 나중에 지원
public enum ConfigurationFormat {
    CISCO_IOS,
    ARISTA_vEOS,
    OPNSense,
    AlpineFirewall,
    OpenvSwitch
}
