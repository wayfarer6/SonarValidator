package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.Locale;

/**
 * CLI 출력 문법을 고르기 위한 장비 종류입니다.
 *
 * <p>C++ Prober 의 {@code cli_parser::Vendor} 와 1:1 로 대응합니다. Prober 는
 * {@code ProberConfig::DetectProductName()} 이 만든 문자열만 알고 있으므로,
 * 제품명 문자열과 이 열거형 사이의 변환이 필요합니다.
 *
 * <p>여기서 중요한 것은 "장비의 OS" 입니다. FRR 라우터도, nftables 방화벽도,
 * OpenVSwitch 스위치도 실제 호스트는 리눅스이므로 커널 {@code ip route show}
 * 출력을 그대로 낼 수 있습니다. 그런 출력에는 라우트 코드({@code O>*}, {@code C})
 * 가 없어서 FRR/Cisco 문법으로는 파싱되지 않습니다.
 */
public enum CliVendor {

    /** OpenVSwitch ({@code ovs-vsctl show} 등). */
    OPEN_VSWITCH("OpenVSwitch"),

    /** FRR 라우터 (vtysh 또는 커널 iproute2). */
    FRR("FRR"),

    /** Cisco IOS / IOS-XE. */
    CISCO("Cisco"),

    /** Arista EOS. */
    ARISTA("Arista"),

    /** nftables 방화벽. */
    NFTABLES("nftables"),

    /** 일반 리눅스 호스트(우분투/알파인). */
    LINUX("Ubuntu"),

    /** 판별 실패. */
    UNKNOWN("Unknown");

    private final String displayName;

    CliVendor(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 사람이 읽는 벤더 이름입니다.
     *
     * @return 표시 이름
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 호스트 OS 가 리눅스라서 커널 iproute2 출력을 낼 수 있는 장비인지 여부입니다.
     *
     * <p>이 값이 참이고 출력에 라우트 코드가 없으면 {@code ip route show} 로 보고
     * {@code IpAddr} 문법을 씁니다.
     *
     * @return 리눅스 호스트 여부
     */
    public boolean isLinuxHost() {
        return this == LINUX || this == FRR || this == NFTABLES || this == OPEN_VSWITCH;
    }

    /**
     * 제품명 문자열에서 벤더를 판별합니다.
     *
     * <p>Prober 의 {@code VendorFromProductName} 과 동일한 순서로 검사합니다.
     * 순서가 중요합니다. 예를 들어 "Cisco" 를 먼저 보지 않으면 다른 규칙에
     * 걸릴 수 있습니다.
     *
     * @param productName 제품명 (null 허용)
     * @return 벤더 (판별 실패 시 {@link #UNKNOWN})
     */
    public static CliVendor fromProductName(String productName) {
        final String name = productName == null ? "" : productName;
        if (name.contains("Cisco")) {
            return CISCO;
        }
        if (name.contains("Arista")) {
            return ARISTA;
        }
        if (name.contains("FRR")) {
            return FRR;
        }
        if (name.contains("OpenVSwitch") || name.contains("Open vSwitch")) {
            return OPEN_VSWITCH;
        }
        if (name.contains("nftables") || name.contains("nft")) {
            return NFTABLES;
        }
        if (name.contains("Ubuntu") || name.contains("Linux")) {
            return LINUX;
        }
        return UNKNOWN;
    }

    /**
     * 벤더 <b>이름</b> 문자열을 관용적으로 해석합니다. (null 안전)
     *
     * <h2>⚠️ 왜 별도 진입점이 필요한가</h2>
     * <p>호출자는 벤더를 다음 세 형태로 갖습니다.
     * <ul>
     *   <li>이미 판별된 enum (텔레메트리 경로)</li>
     *   <li>{@code null} (대상 생략 — 기본 조회)</li>
     *   <li>사용자가 적어 넣은 문자열 (진단 API)</li>
     * </ul>
     * 호출부마다 {@code vendor == null ? UNKNOWN : vendor} 를 반복하면
     * 한 곳을 빠뜨렸을 때 NPE 가 나고, 그 NPE 는 수집 경로에서 곧
     * 데이터 유실입니다.
     *
     * <p>{@link #fromProductName(String)} 과 다른 점: 이쪽은 제품명이 아니라
     * <b>벤더 이름 자체</b>를 받습니다. {@code "frr"} 은 제품명 규칙에서는
     * {@code UNKNOWN} 이지만 여기서는 {@code FRR} 입니다.
     *
     * @param value 벤더 이름 또는 제품명 (null/빈 문자열 허용)
     * @return 벤더 (해석 실패 시 {@link #UNKNOWN})
     */
    public static CliVendor parse(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        final String upper = value.trim().toUpperCase(Locale.ROOT);
        for (final CliVendor candidate : values()) {
            if (candidate.name().equals(upper)) {
                return candidate;
            }
        }
        // 이름이 아니면 제품명 규칙으로 한 번 더 시도합니다.
        // ("Open vSwitch 3.3" 처럼 이름+버전이 섞여 오는 경우)
        return fromProductName(value);
    }
}