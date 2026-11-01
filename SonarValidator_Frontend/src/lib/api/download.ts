export function bundleFileName(disposition: string, fallback: string): string {
  const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition);
  const plain = /filename\s*=\s*(?:"([^"]*)"|([^;]*))/i.exec(disposition);
  let name = fallback;
  if (encoded) {
    try { name = decodeURIComponent(encoded[1].trim()); } catch { name = fallback; }
  } else if (plain) {
    const value = (plain[1] ?? plain[2]).trim();
    if (!value.startsWith("=?")) name = value;
  }
  return name && !/[/\\\r\n\0]/.test(name) ? name : fallback;
}

export function bundleDownloadError(status: number, text: string): string {
  try {
    const body = JSON.parse(text);
    const message = body.message ?? body.detail;
    if (typeof message === "string" && message.trim()) return message.slice(0, 240);
  } catch { /* A proxy may return HTML instead of JSON. */ }
  return `Agent 파일 요청을 완료하지 못했습니다 (HTTP ${status}).`;
}
