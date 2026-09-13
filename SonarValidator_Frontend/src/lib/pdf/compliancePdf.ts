import type { jsPDF } from "jspdf";
import {
  formatTimestamp,
  type AgentInfo,
  type ComplianceChange,
  type ProjectInfo,
} from "../mockData";

/**
 * Compliance 변경 내역 보고서를 "텍스트 기반" PDF로 생성한다.
 *
 * html2canvas처럼 DOM을 비트맵으로 캡처하는 방식이 아니라,
 * 화면에 렌더링된 보고서의 데이터(헤더/메타/요약/테이블)를 jsPDF로
 * 직접 그려 넣는다.
 *  - 텍스트가 실제 문자열이라 PDF에서 선택·검색·복사 가능
 *  - 이미지 대비 파일 크기가 훨씬 작고 확대해도 깨지지 않음
 *  - 다크모드와 무관하게 항상 라이트 톤으로 출력
 *
 * 한글은 jsPDF 내장 폰트가 지원하지 않으므로 나눔고딕코딩 TTF를
 * Identity-H 인코딩으로 임베딩한다(jsPDF가 사용된 글리프만 서브셋).
 */

const FONT_FAMILY = "NanumGothicCoding";
const FONT_FILES = {
  normal: "NanumGothicCoding-Regular.ttf",
  bold: "NanumGothicCoding-Bold.ttf",
} as const;

const MARGIN = 14;
const CELL_PADDING_X = 2.5;
const CELL_PADDING_Y = 2;
const LINE_HEIGHT = 4.2;
const BODY_FONT_SIZE = 7.5;
const HEAD_FONT_SIZE = 7;

const COLOR = {
  title: "#101828",
  subtitle: "#667085",
  label: "#667085",
  value: "#101828",
  border: "#EAECF0",
  headBg: "#F9FAFB",
  headText: "#475467",
  rowAlt: "#FCFCFD",
  cellText: "#344054",
  brand: "#465FFF",
  white: "#FFFFFF",
} as const;

const STATUS_STYLE: Record<
  ComplianceChange["status"],
  { text: string; bg: string }
> = {
  Applied: { text: "#027A48", bg: "#ECFDF3" },
  Pending: { text: "#B54708", bg: "#FFFAEB" },
  Rejected: { text: "#B42318", bg: "#FEF3F2" },
};

/** A4 세로 기준 본문 폭(182mm)에 맞춘 열 정의 */
const COLUMNS = [
  { key: "id", label: "ID", width: 18 },
  { key: "scope", label: "구분", width: 18 },
  { key: "type", label: "유형", width: 24 },
  { key: "summary", label: "변경 요약", width: 50 },
  { key: "changedBy", label: "요청자", width: 30 },
  { key: "timestamp", label: "일시", width: 22 },
  { key: "status", label: "상태", width: 20 },
] as const;

export interface ComplianceReportInput {
  project: ProjectInfo | null;
  agent: AgentInfo | null;
  changes: ComplianceChange[];
  counts: { total: number; Applied: number; Pending: number; Rejected: number };
  generatedAt: string;
}

/* ------------------------------------------------------------------ */
/* 폰트 로딩                                                           */
/* ------------------------------------------------------------------ */

interface FontAsset {
  style: "normal" | "bold";
  file: string;
  base64: string;
}

let fontPromise: Promise<FontAsset[]> | null = null;

async function toBase64(buffer: ArrayBuffer): Promise<string> {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const CHUNK = 0x8000;
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK));
  }
  return btoa(binary);
}

/** 한글 TTF를 base64로 로드(앱 실행 중 1회만 fetch 후 캐싱) */
function fetchKoreanFonts(): Promise<FontAsset[]> {
  if (!fontPromise) {
    fontPromise = Promise.all(
      (Object.keys(FONT_FILES) as Array<keyof typeof FONT_FILES>).map(
        async (style) => {
          const file = FONT_FILES[style];
          const res = await fetch(`${import.meta.env.BASE_URL}fonts/${file}`);
          if (!res.ok) {
            throw new Error(`폰트 로드 실패: ${file} (${res.status})`);
          }
          return { style, file, base64: await toBase64(await res.arrayBuffer()) };
        },
      ),
    ).catch((error) => {
      // 실패 시 다음 시도에서 재시도할 수 있도록 캐시 해제
      fontPromise = null;
      throw error;
    });
  }
  return fontPromise;
}

/** jsPDF 인스턴스별 VFS이므로 인스턴스마다 폰트를 등록해 준다 */
async function loadKoreanFonts(pdf: jsPDF): Promise<void> {
  const assets = await fetchKoreanFonts();
  assets.forEach(({ style, file, base64 }) => {
    pdf.addFileToVFS(file, base64);
    // fontStyle="normal" 고정 + fontWeight로 normal/bold 등록
    // (bold+normal 조합은 jsPDF에서 예외, bold+bold는 키가 "boldbold"가 됨)
    pdf.addFont(file, FONT_FAMILY, "normal", style, "Identity-H");
  });
}

/* ------------------------------------------------------------------ */
/* 그리기 헬퍼                                                         */
/* ------------------------------------------------------------------ */

function setFont(pdf: jsPDF, style: "normal" | "bold", size: number) {
  pdf.setFont(FONT_FAMILY, style);
  pdf.setFontSize(size);
}

function drawText(
  pdf: jsPDF,
  text: string,
  x: number,
  y: number,
  color: string,
  style: "normal" | "bold" = "normal",
  size = BODY_FONT_SIZE,
) {
  setFont(pdf, style, size);
  pdf.setTextColor(color);
  pdf.text(text, x, y);
}

/** 텍스트를 폭에 맞게 줄바꿈한 결과(항상 1줄 이상) */
function wrap(pdf: jsPDF, text: string, maxWidth: number): string[] {
  setFont(pdf, "normal", BODY_FONT_SIZE);
  const lines = pdf.splitTextToSize(text || "-", maxWidth) as string[];
  return lines.length > 0 ? lines : ["-"];
}

function drawPill(
  pdf: jsPDF,
  text: string,
  x: number,
  y: number,
  width: number,
  height: number,
  fg: string,
  bg: string,
) {
  pdf.setFillColor(bg);
  pdf.roundedRect(x, y, width, height, 1.2, 1.2, "F");
  setFont(pdf, "bold", 6.5);
  pdf.setTextColor(fg);
  const textWidth = pdf.getTextWidth(text);
  pdf.text(text, x + (width - textWidth) / 2, y + height / 2 + 1.1);
}

/* ------------------------------------------------------------------ */
/* 보고서 렌더링                                                       */
/* ------------------------------------------------------------------ */

interface Layout {
  pageWidth: number;
  pageHeight: number;
  contentWidth: number;
  bottomLimit: number;
}

function getLayout(pdf: jsPDF): Layout {
  const pageWidth = pdf.internal.pageSize.getWidth();
  const pageHeight = pdf.internal.pageSize.getHeight();
  return {
    pageWidth,
    pageHeight,
    contentWidth: pageWidth - MARGIN * 2,
    // 바닥글(페이지 번호) 영역을 미리 확보
    bottomLimit: pageHeight - MARGIN - 8,
  };
}

function drawTableHeader(pdf: jsPDF, y: number, layout: Layout): number {
  const height = 7;
  pdf.setFillColor(COLOR.headBg);
  pdf.rect(MARGIN, y, layout.contentWidth, height, "F");
  pdf.setDrawColor(COLOR.border);
  pdf.line(MARGIN, y + height, MARGIN + layout.contentWidth, y + height);

  setFont(pdf, "bold", HEAD_FONT_SIZE);
  pdf.setTextColor(COLOR.headText);
  let x = MARGIN;
  COLUMNS.forEach((col) => {
    pdf.text(col.label, x + CELL_PADDING_X, y + height / 2 + 1);
    x += col.width;
  });
  return y + height;
}

function drawReportHeader(
  pdf: jsPDF,
  input: ComplianceReportInput,
  layout: Layout,
): number {
  const { project, agent, counts, generatedAt } = input;
  let y = MARGIN;

  // 브랜드 바 + 제목
  pdf.setFillColor(COLOR.brand);
  pdf.rect(MARGIN, y, 3, 12, "F");

  drawText(pdf, "SonarValidator Compliance Report", MARGIN + 6, y + 5, COLOR.title, "bold", 15);
  drawText(pdf, "네트워크 설정 변경 내역 보고서", MARGIN + 6, y + 11, COLOR.subtitle, "normal", 9);

  // 우측 범위 배지
  const scopeLabel = agent ? "Agent 단위" : "프로젝트 단위";
  drawPill(pdf, scopeLabel, layout.pageWidth - MARGIN - 26, y + 1, 26, 6, COLOR.white, COLOR.brand);

  y += 18;

  // 메타 정보 (2열)
  const metaRows: Array<Array<[string, string]>> = [
    [
      ["대상", agent ? `${agent.name} (${agent.id})` : project ? `${project.name} (#${project.id})` : "-"],
      ["생성 일시", generatedAt],
    ],
  ];
  if (agent) {
    metaRows.push([["IP / 대역", `${agent.ip} / ${agent.ipRange}`], ["디바이스 유형", agent.deviceType]]);
  } else if (project) {
    metaRows.push([
      ["프로젝트 설명", project.description || "-"],
      ["생성일", project.createdAt.slice(0, 10)],
    ]);
  }
  metaRows.push([
    [
      "변경 건수",
      `${counts.total}건  (Applied ${counts.Applied} · Pending ${counts.Pending} · Rejected ${counts.Rejected})`,
    ],
    ["추출 범위", agent ? `Agent ${agent.id}` : "프로젝트 전체"],
  ]);

  const colWidth = layout.contentWidth / 2;
  metaRows.forEach((row) => {
    row.forEach(([label, value], index) => {
      const x = MARGIN + index * colWidth;
      setFont(pdf, "normal", BODY_FONT_SIZE);
      const labelWidth = pdf.getTextWidth(`${label}:`);
      drawText(pdf, `${label}:`, x, y, COLOR.label);
      const wrapped = wrap(pdf, value, colWidth - labelWidth - 8);
      setFont(pdf, "bold", BODY_FONT_SIZE);
      pdf.setTextColor(COLOR.value);
      wrapped.slice(0, 2).forEach((line, lineIndex) => {
        pdf.text(line, x + labelWidth + 2, y + lineIndex * LINE_HEIGHT);
      });
    });
    y += LINE_HEIGHT + 1.5;
  });

  y += 2;
  pdf.setDrawColor(COLOR.border);
  pdf.line(MARGIN, y, MARGIN + layout.contentWidth, y);
  return y + 6;
}

function drawFooter(pdf: jsPDF, input: ComplianceReportInput, layout: Layout) {
  const total = pdf.getNumberOfPages();
  for (let page = 1; page <= total; page += 1) {
    pdf.setPage(page);
    const y = layout.pageHeight - MARGIN + 2;
    pdf.setDrawColor(COLOR.border);
    pdf.line(MARGIN, y - 4, MARGIN + layout.contentWidth, y - 4);
    drawText(
      pdf,
      `본 보고서는 SonarValidator가 생성한 네트워크 설정 변경 이력입니다. · Generated ${input.generatedAt}`,
      MARGIN,
      y,
      COLOR.subtitle,
      "normal",
      6.5,
    );
    setFont(pdf, "normal", 6.5);
    pdf.setTextColor(COLOR.subtitle);
    const pageLabel = `${page} / ${total}`;
    pdf.text(pageLabel, MARGIN + layout.contentWidth - pdf.getTextWidth(pageLabel), y);
  }
}

/**
 * Compliance 변경 내역 보고서를 생성해 다운로드한다.
 * @returns 저장된 파일명
 */
export async function exportComplianceReportPdf(
  input: ComplianceReportInput,
): Promise<string> {
  // 무거운 jspdf 번들은 내보내기 시점에 동적 로드
  const { jsPDF: JsPdfCtor } = await import("jspdf");

  const pdf = new JsPdfCtor({
    orientation: "portrait",
    unit: "mm",
    format: "a4",
    compress: true,
  });

  await loadKoreanFonts(pdf);

  const layout = getLayout(pdf);
  let y = drawReportHeader(pdf, input, layout);

  // 테이블
  const headerTop = y;
  y = drawTableHeader(pdf, y, layout);

  if (input.changes.length === 0) {
    const emptyHeight = 16;
    pdf.setFillColor(COLOR.rowAlt);
    pdf.rect(MARGIN, y, layout.contentWidth, emptyHeight, "F");
    const message = "추출할 변경 내역이 없습니다.";
    setFont(pdf, "normal", BODY_FONT_SIZE);
    pdf.setTextColor(COLOR.subtitle);
    pdf.text(
      message,
      MARGIN + (layout.contentWidth - pdf.getTextWidth(message)) / 2,
      y + emptyHeight / 2 + 1,
    );
    y += emptyHeight;
  }

  input.changes.forEach((change, index) => {
    const cells = [
      wrap(pdf, change.id, COLUMNS[0].width - CELL_PADDING_X * 2),
      wrap(
        pdf,
        change.scope === "Agent" ? (change.agentId ?? "Agent") : "Project",
        COLUMNS[1].width - CELL_PADDING_X * 2,
      ),
      wrap(pdf, change.type, COLUMNS[2].width - CELL_PADDING_X * 2),
      wrap(pdf, change.summary, COLUMNS[3].width - CELL_PADDING_X * 2),
      wrap(pdf, change.changedBy, COLUMNS[4].width - CELL_PADDING_X * 2),
      wrap(pdf, formatTimestamp(change.timestamp), COLUMNS[5].width - CELL_PADDING_X * 2),
      [change.status],
    ];

    const lineCount = Math.max(...cells.map((lines) => lines.length));
    const rowHeight = lineCount * LINE_HEIGHT + CELL_PADDING_Y * 2;

    // 페이지가 부족하면 새 페이지로 넘기고 헤더를 다시 그린다
    if (y + rowHeight > layout.bottomLimit) {
      pdf.addPage();
      y = drawTableHeader(pdf, MARGIN, layout);
    }

    // 지브라 스트라이프
    if (index % 2 === 1) {
      pdf.setFillColor(COLOR.rowAlt);
      pdf.rect(MARGIN, y, layout.contentWidth, rowHeight, "F");
    }
    pdf.setDrawColor(COLOR.border);
    pdf.line(MARGIN, y + rowHeight, MARGIN + layout.contentWidth, y + rowHeight);

    let x = MARGIN;
    cells.forEach((lines, colIndex) => {
      const col = COLUMNS[colIndex];
      const textX = x + CELL_PADDING_X;
      const textY = y + CELL_PADDING_Y + LINE_HEIGHT - 1.1;

      if (col.key === "status") {
        const status = change.status;
        const style = STATUS_STYLE[status];
        setFont(pdf, "bold", 6.5);
        const pillWidth = Math.min(pdf.getTextWidth(status) + 4, col.width - CELL_PADDING_X * 2);
        drawPill(
          pdf,
          status,
          textX,
          y + (rowHeight - 5) / 2,
          pillWidth,
          5,
          style.text,
          style.bg,
        );
      } else {
        setFont(pdf, col.key === "id" ? "bold" : "normal", BODY_FONT_SIZE);
        pdf.setTextColor(col.key === "id" ? COLOR.title : COLOR.cellText);
        lines.forEach((line, lineIndex) => {
          pdf.text(line, textX, textY + lineIndex * LINE_HEIGHT);
        });
      }
      x += col.width;
    });

    y += rowHeight;
  });

  // 표 외곽선
  pdf.setDrawColor(COLOR.border);
  pdf.rect(MARGIN, headerTop, layout.contentWidth, y - headerTop);

  drawFooter(pdf, input, layout);

  const scope = input.agent ? `agent_${input.agent.id}` : `project_${input.project?.id ?? "all"}`;
  const date = new Date().toISOString().slice(0, 10);
  const fileName = `compliance-report_${scope}_${date}.pdf`;
  pdf.save(fileName);
  return fileName;
}
