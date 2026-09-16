package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Agent 에게 내려보낼 정책을 만들어 주는 서비스입니다.
 *
 * <h2>지금의 구현</h2>
 * <p>실제 정책 저장소(DB/파일)가 아직 없으므로 {@code docs/Agent/*_Policy_Design.md}
 * 에 정의된 스키마에 맞춘 <b>고정 플 정책</b>을 돌려줍니다. 문서 스키마의 특징은
 * 스칼라 값도 배열로 감싼다는 점입니다. (예: {@code "command": ["create"]})
 * C++ 쪽 {@code policy_json::AsString} 가 배열/스칼라를 모두 받아주므로 이 규칙을
 * 그대로 따릅니다.
 *
 * <h2>저장소가 생기면</h2>
 * <p>{@link #forDevice} 한 곳만 DB 조회로 바꾸면 나머지 계층(핸들러/시)은
 * 수정할 필요가 없습니다.
 */
@Service
public class PolicyRegistryService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * 장치 유형에 맞는 정책을 만듭니다.
     *
     * @param deviceType 장치 유형 (null 이면 VM 으로 간주)
     * @param deviceId 장치 식별자
     * @return Agent 가 바로 적용할 수 있는 정책 JSON
     */
    public ObjectNode forDevice(DeviceType deviceType, String deviceId) {
        final DeviceType type = (deviceType == null) ? DeviceType.VM : deviceType;
        final String id = (deviceId == null || deviceId.isBlank()) ? "unknown" : deviceId;

        final ObjectNode policy = JSON.objectNode();
        policy.put("policy_id", "pol-" + type.name().toLowerCase() + "-0001");
        policy.put("device_type", type.name());
        policy.put("device_id", id);
        policy.put("valid_until", Instant.now().plus(24, ChronoUnit.HOURS).toString());

        // C++ policy_receiver 의 ReceivePolicy 는 "policies" 배열을 우선 처리합니다.
        final ArrayNode policies = policy.putArray("policies");
        policies.add(rulesFor(type));
        return policy;
    }

    /**
     * 장치별 규칙을 만듭니다. 값은 문서의 예시를 그대로 사용합니다.
     *
     * @param type 장치 유형
     * @return 단일 정책 규칙 노드
     */
    private ObjectNode rulesFor(DeviceType type) {
        return switch (type) {
            case VM -> {
                final ObjectNode rule = JSON.objectNode();
                // docs/Agent/VM_Policy_Design.md — 인터페이스 up
                rule.putArray("vendor").add("Canonical");
                rule.putArray("product").add("Ubuntu Linux");
                rule.putArray("model").add("Ubuntu VM");
                rule.putArray("command").add("on");
                rule.putArray("interface").add("ens33");
                yield rule;
            }
            case SWITCH -> {
                final ObjectNode rule = JSON.objectNode();
                // docs/Agent/Switch_Policy_Design.md — Arista vEOS 포트 enable
                rule.putArray("vendor").add("Arista");
                rule.putArray("product").add("Arista vEOS");
                rule.putArray("model").add("Arista vEOS");
                rule.putArray("command").add("on");
                rule.putArray("enable").add("true");
                rule.putArray("port").add("Ethernet 1");
                yield rule;
            }
            case ROUTER -> {
                final ObjectNode rule = JSON.objectNode();
                // docs/Agent/Router_Policy_Design.md — Cisco IOS XE 인터페이스 on
                rule.putArray("vendor").add("Cisco");
                rule.putArray("product").add("IOS XE");
                rule.putArray("model").add("Cisco ISR");
                rule.putArray("command").add("on");
                rule.putArray("interface").add("GigabitEthernet0/0/1");
                yield rule;
            }
            case FIREWALL -> {
                final ObjectNode rule = JSON.objectNode();
                // docs/Agent/Firewall_Policy_Design.md — nftables filter 테이블 생성
                rule.putArray("vendor").add("Linux");
                rule.putArray("product").add("nftables");
                rule.putArray("model").add("Linux Netfilter");
                rule.putArray("command").add("create");
                rule.putArray("table_family").add("inet");
                rule.putArray("table_name").add("filter");

                final ArrayNode chains = rule.putArray("chains");
                chains.add(chain("input"));
                chains.add(chain("forward"));
                yield rule;
            }
        };
    }

    /**
     * nftables 체인 1개를 만듭니다. (firewall 정책 전용)
     *
     * @param hook hook 이름이자 chain 이름 (input/forward)
     * @return 체인 노드
     */
    private ObjectNode chain(String hook) {
        final ObjectNode chain = JSON.objectNode();
        chain.putArray("chain_name").add(hook);
        chain.putArray("hook").add(hook);
        chain.putArray("priority").add("0");
        chain.putArray("policy").add("drop");
        return chain;
    }
}