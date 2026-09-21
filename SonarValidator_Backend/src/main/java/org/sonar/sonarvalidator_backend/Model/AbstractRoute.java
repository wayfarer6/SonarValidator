package org.sonar.sonarvalidator_backend.Model;

// 설계는 org.batfish.datamodel 패키지를 참고 했습니다.

public interface AbstractRoute {
    Prefix prefix = Prefix.parse("0.0.0.0/0");
    Ip nextHopIp = null;
    String nextHopInterface = "";
    long metric = 0;
    int adminDistance = 0;
    long tag = 0L;

}
