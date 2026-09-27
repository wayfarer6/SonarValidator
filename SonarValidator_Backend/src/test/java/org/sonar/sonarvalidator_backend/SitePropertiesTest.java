package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Config.SiteProperties;

/**
 * 사이트 설정(하드코딩 제거)의 계약을 검증합니다.
 *
 * <h2>⚠️ 이 테스트가 겨냥하는 최악의 실패 모드</h2>
 * <p>제어평면(관리망) 대역은 격리에서 <b>절대 차단 대상이 되어서는 안 됩니다.</b>
 * 차단하면 서버로 나가는 길이 사라져 해제 명령조차 도달하지 못하고,
 * 운영자가 장치를 되살릴 방법이 없습니다.
 *
 * <p>그래서 여기서는 다음을 봅니다.
 * <ul>
 *   <li>프로젝트가 관리 대역을 지정하면 <b>그 값이 우선</b>하는가 (요구사항)</li>
 *   <li>지정하지 않으면 전역 기본값으로 폴백하는가</li>
 *   <li>쉼표로 여러 대역을 받는가</li>
 *   <li>업링크/브리지/테이블 이름이 설정으로 바뀌는가</li>
 * </ul>
 */
class SitePropertiesTest {

    @Test
    @DisplayName("프로젝트가 지정한 관리 대역이 전역 기본값보다 우선한다")
    void projectPrefixWins() {
        final SiteProperties site = new SiteProperties();
        site.setManagementPrefix("172.16.255.0/24");

        // ⚠️ 요구사항 — 제어평면 대역은 프로젝트마다 다를 수 있습니다.
        //    순서를 뒤집으면 "이 프로젝트는 관리망이 다르다" 를 표현할 수 없습니다.
        assertEquals("10.20.0.0/24", site.managementPrefixFor("10.20.0.0/24"),
                "프로젝트 지정이 이김");
        assertEquals("172.16.255.0/24", site.managementPrefixFor(null),
                "미지정이면 전역 기본값");
        assertEquals("172.16.255.0/24", site.managementPrefixFor("   "),
                "공백도 미지정으로 봄");
    }

    @Test
    @DisplayName("여러 관리 대역을 쉼표로 받을 수 있다")
    void multiplePrefixes() {
        final SiteProperties site = new SiteProperties();

        final List<String> prefixes = site.managementPrefixesFor("10.0.0.0/24, 172.16.255.0/24");

        assertEquals(2, prefixes.size(), "두 대역");
        assertTrue(prefixes.contains("10.0.0.0/24"), "앞 대역 파싱");
        assertTrue(prefixes.contains("172.16.255.0/24"), "뒤 대역 공백 제거");
    }

    @Test
    @DisplayName("관리 대역이 없으면 빈 목록을 돌려준다 (예외 없음)")
    void emptyPrefixes() {
        final SiteProperties site = new SiteProperties();
        site.setManagementPrefix("");

        assertTrue(site.managementPrefixesFor(null).isEmpty(), "빈 목록");
        assertEquals("", site.managementPrefixFor(null), "빈 문자열");
    }

    @Test
    @DisplayName("스위치 업링크·브리지 이름은 설정으로 바뀐다")
    void switchDefaultsAreConfigurable() {
        final SiteProperties site = new SiteProperties();
        // 기본값은 랩 관례를 따릅니다.
        assertEquals("eth0", site.getSwitchDefaults().getUplinkPort());
        assertEquals("br0", site.getSwitchDefaults().getBridgeName());

        // ⚠️ 제품·배포마다 다르므로 코드 수정 없이 바꿀 수 있어야 합니다.
        site.getSwitchDefaults().setUplinkPort("Ethernet1");
        site.getSwitchDefaults().setBridgeName("Bridge");

        assertEquals("Ethernet1", site.getSwitchDefaults().getUplinkPort(), "업링크 변경");
        assertEquals("Bridge", site.getSwitchDefaults().getBridgeName(), "브리지 변경");
    }

    @Test
    @DisplayName("방화벽 테이블 이름은 설정으로 바뀌고 기본은 filter 가 아니다")
    void firewallTableIsConfigurable() {
        final SiteProperties site = new SiteProperties();

        // ⚠️ 기본 filter 를 쓰면 기존 방화벽 규칙과 충돌해 랩 전체가 끊길 수 있습니다.
        assertFalse("filter".equals(site.getFirewallDefaults().getTableName()),
                "기본 filter 테이블을 쓰지 않음");
        assertEquals("sonar", site.getFirewallDefaults().getTableName(), "기본 전용 테이블");

        site.getFirewallDefaults().setTableName("sonar_validate");
        assertEquals("sonar_validate", site.getFirewallDefaults().getTableName(), "테이블 변경");
    }
}