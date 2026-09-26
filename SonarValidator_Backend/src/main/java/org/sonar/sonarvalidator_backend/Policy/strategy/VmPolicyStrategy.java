package org.sonar.sonarvalidator_backend.Policy.strategy;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Ubuntu/Linux VM 의 정책 전략입니다.
 *
 * <h2>이 전략이 아는 것</h2>
 * <ul>
 *   <li>폴백: 인터페이스 up (문서 {@code VM_Policy_Design.md})</li>
 *   <li>선언: netplan 스키마 ({@code config_backend}, {@code network_config})</li>
 *   <li>집행: 없음 — VM 은 라우팅 경로가 아니므로 ACL 지점이 아닙니다</li>
 * </ul>
 *
 * <h2>⚠️ 인터페이스 이름을 고정하지 않는다</h2>
 * <p>문서 예제는 {@code ens33} 이지만 랩 컨테이너 VM 은 {@code eth0}, 다른
 * 배포판은 {@code ens3} / {@code enp0s3} 을 씁니다. 서버는 장치의 인터페이스
 * 이름을 알 수 없으므로 고정하면 <code>Cannot find device "ens33"</code> 로
 * <b>모든 VM 정책이 실패</b>합니다. (실측: TOD-Cam/UAV/VDI-1/VDI-2)
 * 그래서 {@link #PRIMARY_INTERFACE_TOKEN} 을 보내고 Prober 가 실제 이름으로
 * 치환합니다.
 *
 * <h2>⚠️ 주소는 호스트 주소를 넣는다</h2>
 * <p>{@link PolicyJson#hostCidrOf} 로 네트워크 주소를 호스트 주소로 바꿉니다.
 * 네트워크 주소({@code .0})를 인터페이스에 넣으면 대역 판정이 깨져
 * 통신이 두절됩니다.
 */
@Component
public class VmPolicyStrategy implements DevicePolicyStrategy {

    private static final Logger log = LoggerFactory.getLogger(VmPolicyStrategy.class);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * 장치의 실제 기본 인터페이스로 치환될 자리표시자입니다.
     *
     * <p>이 문자열은 Prober 의 {@code ManagementService::ResolveInterfaceName}
     * 과 <b>정확히 같아야</b> 합니다. 다르면 치환되지 않고
     * {@code Cannot find device "__primary__"} 로 실패합니다.
     */
    public static final String PRIMARY_INTERFACE_TOKEN = "__primary__";

    @Override
    public boolean supports(DeviceType type) {
        return type == DeviceType.VM;
    }

    @Override
    public String defaultVendor() {
        return "Canonical";
    }

    @Override
    public String defaultProduct() {
        return "Ubuntu Linux";
    }

    @Override
    public ObjectNode defaultRule() {
        final ObjectNode rule = JSON.objectNode();
        rule.putArray("vendor").add("Canonical");
        rule.putArray("product").add("Ubuntu Linux");
        rule.putArray("model").add("Ubuntu VM");
        rule.putArray("command").add("on");
        // 폴백은 인터페이스 이름을 알 수 없으므로 자리표시자를 씁니다.
        rule.putArray("interface").add(PRIMARY_INTERFACE_TOKEN);
        return rule;
    }

    @Override
    public ObjectNode declarationRule(PolicyBuildContext context) {
        final ObjectNode rule = baseRule(context);

        // netplan 스키마 형태로 대역을 알립니다.
        rule.putArray("command").add("create");
        rule.putArray("config_backend").add("netplan");

        final String cidr = context.subnet().getCidr();
        if (cidr == null || cidr.isBlank()) {
            return rule;
        }

        final ObjectNode ethernets = rule.putObject("network_config").putObject("ethernets");
        final ObjectNode nic = ethernets.putObject(PRIMARY_INTERFACE_TOKEN);
        nic.putArray("dhcp4").add("false");

        final String host = PolicyJson.hostCidrOf(cidr);
        if (host != null) {
            nic.putArray("addresses").add(host);
        } else {
            // 주소를 계산할 수 없으면 <b>넣지 않습니다.</b> 틀린 주소를 넣으면
            // 장치가 통신 불능이 되어 프로버까지 멈춥니다.
            log.warn("VM address not derivable from subnet cidr={}; omitting address", cidr);
        }
        return rule;
    }

    @Override
    public ObjectNode enforcementRule(PolicyBuildContext context) {
        // VM 은 트래픽 집행 지점이 아닙니다. (라우터/방화벽이 담당)
        // 의도(intents)는 공통으로 함께 전달되므로 화면은 같은 정보를 봅니다.
        return null;
    }

    /**
     * 공통 머리말(vendor/product/model/subnet_id)을 채웁니다.
     *
     * @param context 빌드 컨텍스트
     * @return 머리말이 채워진 노드
     */
    private ObjectNode baseRule(PolicyBuildContext context) {
        final ObjectNode rule = JSON.objectNode();
        rule.putArray("vendor").add(context.vendorOr(defaultVendor()));
        rule.putArray("product").add(context.productOr(defaultProduct()));
        rule.putArray("model").add(defaultProduct());
        rule.putArray("subnet_id").add(
                PolicyJson.firstNonBlank(context.subnet().getSubnetId(), "subnet"));
        return rule;
    }
}