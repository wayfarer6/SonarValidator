import type { ApiDiscoveredDevice } from "./api/types";

export function networkCidr(address: string): string | null {
  const [ip, bits = "32"] = address.trim().split("/");
  const parts = ip.split(".");
  if (parts.length !== 4 || parts.some(p => !/^\d+$/.test(p) || +p > 255)) return null;
  const octets = parts.map(Number);
  const prefix = Number(bits);
  if (!/^\d+$/.test(bits) || prefix < 0 || prefix > 32 || octets[0] === 127) return null;
  const value = ((octets[0] << 24) | (octets[1] << 16) | (octets[2] << 8) | octets[3]) >>> 0;
  const network = (value & (prefix === 0 ? 0 : 0xffffffff << (32 - prefix))) >>> 0;
  return `${network >>> 24}.${(network >>> 16) & 255}.${(network >>> 8) & 255}.${network & 255}/${prefix}`;
}

function interfaceVlans(device: ApiDiscoveredDevice, name: string): number[] {
  const iface = device.interfaces.find(i => i.name === name);
  const svi = sviVlan(name);
  return [...new Set([
    ...(iface?.access_vlan != null ? [iface.access_vlan] : []),
    // SVI(예: `Vlan8`)는 그 자체가 VLAN 8 의 구성원입니다.
    // ⚠️ 장비가 내려주는 `vlans[].members` 에는 SVI 가 없는 경우가 많아
    //    (실측 Arista: VLAN8 members=[Cpu, Et2]) 이름을 보지 않으면
    //    SVI 주소(10.0.8.1/24)가 VLAN 8 과 연결되지 않아
    //    "IP 대역 미수집"으로 잘못 표시됩니다.
    ...(svi != null ? [svi] : []),
    ...(device.vlans ?? []).filter(v => v.members.includes(name)).map(v => v.vlan_id),
  ])];
}

/** SVI 인터페이스 이름에서 VLAN 번호를 얻습니다. (예: {@code Vlan8} → 8) */
function sviVlan(name: string): number | null {
  const match = /^vlan(\d+)$/i.exec(name ?? "");
  return match ? Number(match[1]) : null;
}

export interface ObservedVlan {
  agent: string;
  vlan: number;
  access: string[];
  trunk: string[];
  cidrs: string[];
}

/**
 * 장비에서 수집한 VLAN 목록입니다.
 *
 * <p>⚠️ <b>IP 대역이 수집된 VLAN 만</b> 돌려줍니다. 주소가 없는 L2 전용 VLAN 은
 * 라우팅 대역이 아니어서 정책·토폴로지 어디에도 쓰이지 않으므로, 수집 결과에서
 * 제외합니다. (실측 Arista: VLAN1=default, VLAN99=미사용 이 여기서 빠집니다.)
 */
export function observedVlans(devices: ApiDiscoveredDevice[]): ObservedVlan[] {
  return devices.flatMap(device => {
    const ids = new Set((device.vlans ?? []).map(v => v.vlan_id));
    for (const iface of device.interfaces ?? []) {
      if (iface.access_vlan != null) ids.add(iface.access_vlan);
      iface.trunk_vlans?.forEach(v => ids.add(v));
    }
    return [...ids].sort((a,b) => a-b).map(vlan => {
      const members = device.interfaces.filter(i => interfaceVlans(device,i.name).includes(vlan));
      return {
        agent: device.agent_id, vlan,
        access: members.map(i => i.name),
        trunk: device.interfaces.filter(i => i.trunk_vlans?.includes(vlan)).map(i => i.name),
        cidrs: [...new Set(members.flatMap(i => i.addresses.map(networkCidr).filter((x): x is string => x !== null)))],
      };
    });
  }).filter(row => row.cidrs.length > 0);
}
