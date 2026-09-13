// mermaid 차트 파싱 검증 스크립트 (jsdom 기반)
import { JSDOM } from "jsdom";

const dom = new JSDOM("<!DOCTYPE html><html><body></body></html>", {
  pretendToBeVisual: true,
  url: "http://localhost/",
});
globalThis.window = dom.window;
globalThis.document = dom.window.document;
Object.defineProperty(globalThis, "navigator", {
  value: dom.window.navigator,
  configurable: true,
});
globalThis.Element = dom.window.Element;
globalThis.SVGElement = dom.window.SVGElement;
globalThis.HTMLElement = dom.window.HTMLElement;
globalThis.Node = dom.window.Node;
globalThis.getComputedStyle = dom.window.getComputedStyle;
globalThis.requestAnimationFrame = (cb) => setTimeout(cb, 0);

const { default: mermaid } = await import("mermaid");
mermaid.initialize({ startOnLoad: false, theme: "default", securityLevel: "loose" });

const { readFileSync } = await import("node:fs");
const chartFile = process.argv[2];
const src = readFileSync(chartFile, "utf8");

// 백틱 템플릿 리터럴 안의 차트 본문들을 모두 추출
const charts = [...src.matchAll(/`([\s\S]*?)`/g)].map((m) => m[1]);

let failed = 0;
for (const [i, chart] of charts.entries()) {
  if (!/^\s*(flowchart|graph|sequenceDiagram|classDiagram)/m.test(chart)) continue;
  try {
    await mermaid.parse(chart);
    console.log(`chart[${i}] PARSE OK`);
  } catch (e) {
    failed += 1;
    console.log(`chart[${i}] PARSE FAIL:`);
    console.log(e?.message ?? String(e));
    if (e?.str) console.log("---\n" + e.str);
  }
}
if (failed === 0) console.log("ALL OK");
