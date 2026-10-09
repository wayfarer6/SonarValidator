import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { Script } from "node:vm";
import ts from "typescript";

const source=readFileSync(new URL('../src/lib/discovery.ts',import.meta.url),'utf8');
const exports={};
new Script(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText).runInNewContext({exports});
const {observedVlans}=exports;
const iface=(name,addresses=[],access_vlan=null,trunk_vlans=[])=>({name,addresses,access_vlan,trunk_vlans,subnet_class:null});
const devices=[
 {agent_id:'switch',interfaces:[iface('eth0',[],null,[111,131]),iface('eth1',[],111),iface('eth2',[],131),iface('eth11',['172.16.255.101/24'])],vlans:[{vlan_id:111,members:['eth1']},{vlan_id:131,members:['eth2']}]},
 {agent_id:'router',interfaces:[iface('lo',['127.0.0.1/8','10.255.255.1/32']),iface('eth0.111',['10.20.111.1/24','fe80::1/64'],111),iface('eth7',['172.16.255.1/24'])],vlans:[{vlan_id:111,members:['eth0.111']}]},
];
test('L2 VLANs and trunks survive without fabricating CIDRs or copying another device address',()=>{
 const rows=observedVlans(devices);
 assert.equal(rows.length,3);
 const sw=rows.find(r=>r.agent==='switch'&&r.vlan===131);
 assert.deepEqual([...sw.access],['eth2']);assert.deepEqual([...sw.trunk],['eth0']);assert.equal(sw.cidrs.length,0);
 assert.equal(rows.find(r=>r.agent==='switch'&&r.vlan===111).cidrs.length,0);
 assert.deepEqual([...rows.find(r=>r.agent==='router').cidrs],['10.20.111.0/24']);
});
