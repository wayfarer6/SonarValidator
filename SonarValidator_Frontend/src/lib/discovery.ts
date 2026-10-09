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
  return [...new Set([
    ...(iface?.access_vlan != null ? [iface.access_vlan] : []),
    ...(device.vlans ?? []).filter(v => v.members.includes(name)).map(v => v.vlan_id),
  ])];
}

export interface ObservedVlan {
  agent: string;
  vlan: number;
  access: string[];
  trunk: string[];
  cidrs: string[];
}

/** Preserve L2 VLANs even when no SVI/gateway address has been collected. */
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
  });
}
