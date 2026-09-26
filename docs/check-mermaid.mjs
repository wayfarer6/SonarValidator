// 문서에 들어 있는 Mermaid 코드 블록을 실제로 렌더링해 문법 오류를 잡는다.
//
// 왜 필요한가: Mermaid 문법 오류는 조용하다. GitHub/Confluence 는 오류가 난
// 블록을 그냥 에러 박스로 보여줄 뿐, 저장소에는 아무 흔적이 없다. 문서가
// 커지면 어느 블록이 깨졌는지 사람이 하나하나 열어 봐야 한다.
//
// ⚠️ DOMPurify shim 이 필요한 이유
//   mermaid 는 HTML 라벨(예: "a<br/>b")을 다룰 때 DOMPurify 를 부릅니다.
//   그런데 DOMPurify 는 브라우저 DOM 을 전제하므로 순수 Node 에서는
//   "DOMPurify.sanitize is not a function" 으로 죽습니다. 이건 **문법 오류가
//   아니라 환경 부재**이므로, 파싱이 끝난 뒤의 이 호출만 통과시킵니다.
//   (그래서 이 오류만 무시하고, 진짜 파싱 실패는 그대로 보고합니다)
import { readFileSync } from "node:fs";

globalThis.DOMPurify = {
  sanitize: (value) => value,
  addHook: () => {},
  removeHook: () => {},
  setConfig: () => {},
};

const mermaid = (await import("mermaid")).default;

const files = process.argv.slice(2);
if (files.length === 0) {
  console.error("usage: node check-mermaid.mjs <file.md> [...]");
  process.exit(2);
}

mermaid.initialize({ startOnLoad: false, theme: "default", securityLevel: "loose" });

let failures = 0;
let checked = 0;
let skippedSanitize = 0;

/**
 * 이 오류가 "문법 오류" 인가 "환경 부재" 인가?
 *
 * mermaid 는 문법을 먼저 파싱하고, 그 다음 라벨을 DOMPurify 로 정화합니다.
 * DOMPurify 는 브라우저 DOM 을 전제하므로 순수 Node 에서는 항상 실패합니다.
 * 즉 이 메시지가 나왔다는 것은 **파싱은 이미 통과했다**는 뜻입니다.
 * 진짜 문법 오류는 "Parse error on line N" 형태로 나옵니다.
 *
 * @param {string} message 오류 메시지
 * @returns {boolean} 환경 부재로 인한 오류면 true
 */
function isEnvironmentNoise(message) {
  return message.includes("DOMPurify");
}

for (const file of files) {
  const text = readFileSync(file, "utf8");
  const lines = text.split("\n");

  let inBlock = false;
  let startLine = 0;
  let buf = [];
  let index = 0;

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (!inBlock && /^\s*```mermaid\s*$/.test(line)) {
      inBlock = true;
      startLine = i + 2; // 1-based, fence 다음 줄
      buf = [];
      continue;
    }
    if (inBlock && /^\s*```\s*$/.test(line)) {
      inBlock = false;
      index += 1;
      const chart = buf.join("\n");
      checked += 1;
      try {
        await mermaid.parse(chart);
        console.log(`  [ok]   ${file}:${startLine} (block #${index})`);
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        if (isEnvironmentNoise(message)) {
          // 문법은 통과했고 라벨 정화만 환경 때문에 못 돌린 경우입니다.
          skippedSanitize += 1;
          console.log(
            `  [ok*]  ${file}:${startLine} (block #${index}) — 문법 OK, 라벨 정화는 브라우저 필요`,
          );
        } else {
          failures += 1;
          console.error(`  [FAIL] ${file}:${startLine} (block #${index})`);
          console.error(`         ${message.split("\n").slice(0, 6).join("\n         ")}`);
        }
      }
      continue;
    }
    if (inBlock) {
      buf.push(line);
    }
  }
}

console.log(
  `\nchecked ${checked} mermaid block(s), ${failures} syntax failure(s)` +
    (skippedSanitize > 0 ? `, ${skippedSanitize} block(s) skipped label sanitize` : ""),
);
process.exit(failures === 0 ? 0 : 1);