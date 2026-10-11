/**
 * 프로버 배포 화면/카드가 공유하는 장비 목록 정의입니다.
 *
 * <h2>⚠️ 왜 별도 모듈인가</h2>
 * Fast Refresh(개발 중 상태 유지)는 한 파일이 <b>컴포넌트만</b> export 할 때
 * 동작합니다. {@code AgentDeployCard.tsx} 가 상수 배열을 함께 export 하면
 * 핫 리로드가 전체 새로고침으로 떨어집니다. 그래서 상수와 그 타입은 이 모듈에
 * 두고, 카드 컴포넌트가 import 해서 씁니다.
 */

/** 배포 가능한 장비 한 종류. */
export interface AgentDeviceType {
  /** 화면에 보이는 이름. */
  label: string;
  /** 로고 이미지 주소. */
  image: string;
  /** 로고 대체 텍스트. */
  imageAlt: string;
  /** 프로버 `default.conf` 에 넣을 NODE_TYPE. */
  nodeType: "Router" | "Switch" | "VM" | "Firewall";
  /** 별도 안내가 필요하면 표시 (OPNsense 의 "API 설정 필요"). */
  note?: string;
  /** 클릭 시 장비 카드 대신 설정 모달을 여는지. */
  opensCredentialModal?: boolean;
}

/**
 * 장비 목록입니다.
 *
 * <p>순서는 배포 화면과 동일하게 두었습니다. 화면마다 순서가 다르면 같은
 * 장비를 찾을 때 눈이 다시 훑어야 합니다.
 *
 * <p>`for poc` 가 붙은 항목은 랩 검증용입니다. 실장비(Cisco/Arista/OPNsense/
 * Linux)와 섞이지 않도록 이름에 표시를 유지합니다.
 */
export const AGENT_DEVICE_TYPES: AgentDeviceType[] = [
  {
    label: "Cisco Router",
    image: "https://companieslogo.com/img/orig/CSCO-187e9f61.png?t=1728111511",
    imageAlt: "Cisco Router",
    nodeType: "Router",
  },
  {
    label: "Arista Switch",
    image: "https://companieslogo.com/img/orig/ANET_BIG-150f82cc.png?t=1720244490",
    imageAlt: "Arista Switch",
    nodeType: "Switch",
  },
  {
    label: "OPNsense Firewall",
    image: "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/png/opnsense.png",
    imageAlt: "OPNsense Firewall",
    nodeType: "Firewall",
    note: "API 설정 필요",
    opensCredentialModal: true,
  },
  {
    label: "Linux VM",
    image: "https://img.icons8.com/color/150/linux.png",
    imageAlt: "Linux VM",
    nodeType: "VM",
  },
  {
    label: "Open vSwitch(for poc)",
    image:
      "https://images.seeklogo.com/logo-png/27/1/open-vswitch-logo-png_seeklogo-271617.png",
    imageAlt: "OpenvSwitch",
    nodeType: "Switch",
  },
  {
    label: "Alpine Based Firewall (for poc)",
    image: "https://cdn-icons-png.flaticon.com/512/6071/6071236.png",
    imageAlt: "Alpine Based Firewall",
    nodeType: "Firewall",
  },
  {
    label: "FRRouting (for poc)",
    image: "https://docs.frrouting.org/en/stable-8.5/_static/frr-icon.svg",
    imageAlt: "FRRouting",
    nodeType: "Router",
  },
];
