---
title: "07. 프론트엔드 공통 컴포넌트 (확대/축소 · 분기 카드)"
sidebar_label: "07. 공통 컴포넌트"
---

# 프론트엔드 공통 컴포넌트

이 문서는 이번 작업에서 추가한 재사용 컴포넌트 두 개를 설명합니다.
둘 다 **여러 화면에서 공유**하므로, 한 곳을 고치면 모든 사용처에 반영됩니다.

---

## 1. `MermaidDiagram` — 확대/축소 가능한 다이어그램

**경로**: `SonarValidator_Frontend/src/components/common/MermaidDiagram.tsx`

### 1.1 왜 필요한가

토폴로지는 프로젝트가 커질수록 노드가 늘어 글자가 읽을 수 없을 만큼 작아집니다.
반대로 노드가 3~4개뿐일 때는 화면이 휑하게 남습니다.
하나의 배율로 두 상황을 만족할 수 없으므로 사용자가 조절하게 합니다.

### 1.2 사용처 (자동 적용)

이 컴포넌트를 쓰는 **모든 화면**이 확대/축소를 함께 얻습니다.

| 화면 | 경로 |
| --- | --- |
| 네트워크 관리 | `pages/NetworkManagement.tsx` |
| 네트워크 분할 규칙 | `pages/NetworkSegmentationRule.tsx` → `NetworkTopologyMermaid` |
| 토폴로지/규칙 미리보기 | `pages/TopologyRulePreview.tsx` (3개 뷰) |
| 대시보드 최신 토폴로지 | `components/dashboard/LatestTopologyCard.tsx` |

### 1.3 사용법

```tsx
// 기본 (툴바 포함)
<MermaidDiagram chart={chart} />

// 툴바 숨기기 (작은 카드에서 여백을 아낄 때)
<MermaidDiagram chart={chart} showZoomControls={false} />

// 최소 높이 지정
<MermaidDiagram chart={chart} minHeight={300} />
```

### 1.4 제공하는 조작

| 조작 | 방법 |
| --- | --- |
| 확대 / 축소 | `+` / `−` 버튼 |
| 100% 로 되돌리기 | 배율 표시(`100%`) 버튼 클릭 |
| 이동 | 확대 상태에서 **드래그** |
| 커서 기준 확대 | `Ctrl`(또는 `⌘`) + 휠 |
| 키보드 | `+` 확대, `-` 축소, `0` 초기화 (뷰포트에 포커스 후) |

배율 범위는 **25% ~ 400%** 이고 한 단계는 25% 입니다.
한계에 도달하면 해당 버튼이 비활성화됩니다.

### 1.5 내부 구조 (3중 레이어)

`transform: scale()` 은 **레이아웃 크기를 바꾸지 않으므로** 스크롤 범위를
만들려면 별도 요소가 필요합니다.

```
wrapper   — 기준 폭을 재는 곳 (스크롤바 영향을 받지 않음)
  └ viewport (overflow: auto)                  ← 스크롤 담당
      └ spacer (width/height = 기준 × 배율)       ← 스크롤 범위 생성
          └ content (기준 크기, transform: scale) ← 실제 확대 대상
```

**기준 크기 계산**

| 값 | 출처 | 이유 |
| --- | --- | --- |
| 폭 | wrapper 의 `clientWidth` | 스크롤바가 나타나도 변하지 않음 |
| 높이 | SVG `viewBox` 비율 × 폭 | `viewBox` 는 컨테이너와 무관한 본질 크기 |

### 1.6 ⚠️ 구현 중 겪은 함정 3가지

#### ① 측정 피드백 루프 → 300px 로 수축

콘텐츠의 `offsetWidth` 를 재서 그 값을 다시 콘텐츠 `width` 로 넣으면 악순환입니다.

```
width 설정 → SVG 가 그 폭에 맞춰 줄어듦 → 다시 재면 더 작음 → … → 300px
```

**오류가 나지 않습니다.** 뷰포트 570px 인데 콘텐츠 300px 로 측정되어 발견했습니다.
→ 측정 대상을 콘텐츠에서 **떼어내야** 합니다 (wrapper + `viewBox`).

#### ② Mermaid 인라인 `max-width` → 552px 에서 멈춤

Mermaid 가 SVG 에 넣는 `style="max-width: 552px"` 때문에 확대가 멈춥니다.

**effect 에서 지우면 효과가 없습니다.** React 가 `dangerouslySetInnerHTML` 로
SVG 를 다시 주입할 때 인라인 스타일도 되돌리기 때문입니다.
(개발자 도구에서 직접 실행하면 유지되어 원인 파악이 어렵습니다)

→ **문자열을 주입하기 전에** 정규식으로 제거합니다.

#### ③ 버튼 연타가 누적되지 않음

`setScale(scale + STEP)` 은 아직 갱신되지 않은 이전 값으로 계산되어
3번 눌러도 150% 가 됩니다.
→ 함수형 업데이트 `setScale((prev) => clamp(prev + STEP))` 를 씁니다.

### 1.7 검증 결과

| 동작 | 실측 |
| --- | --- |
| 초기 | `100%`, `scrollWidth == clientWidth == 570` |
| `+` 4회 | `200%`, `scrollWidth 1140`, `matrix(2,0,0,2,0,0)` |
| `−` 한계 | `25%`, 축소 버튼 `disabled` |
| `+` 한계 | `400%`, 확대 버튼 `disabled` |
| 드래그 (200%) | `scrollLeft 0 → 300`, 커서 `grab` |
| 드래그 (100%) | 무시됨 (`cursor: auto`) |
| `Ctrl`+휠 | `200% → 225%`, `defaultPrevented=true` |
| 일반 휠 | 배율 유지, `defaultPrevented=false` |
| 키보드 `+`/`0` | `150%` → `100%` |

---

## 2. `Branch_Divider` — 환경 구성 방식 분기선

**경로**: `SonarValidator_Frontend/src/components/common/Branch_Divider.tsx`

### 2.1 왜 필요한가

프로버를 배포한 뒤 절차는 **서버에 닿는지**에 따라 완전히 달라집니다.

```
  공통: 장비에 맞는 Prober 다운로드
                 │
         ┌───────┴───────┐
         │  Branch_Divider (OR)
         ▼               ▼
   온라인 구성        오프라인 구성
   서버로 직접 전송    JSON → 업로드
```

이 갈림길을 그냥 여백으로 두면 운영자는 **두 절차를 동시에 하는 것**으로
오해합니다. 연결이 안 되는 장비에 IP/Port 를 넣고 "왜 안 올라오지" 를
반복하게 됩니다.

### 2.2 Props

| 이름 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `orientation` | `"horizontal" \| "vertical"` | `"horizontal"` | 분할선 방향 |
| `label` | `string` | `"OR"` | 중앙 배지 문구 (빈 값이면 배지 없음) |
| `hint` | `string` | — | 배지 아래 보조 설명 |
| `startLabel` | `string` | — | 시작 측 라벨 (예: `온라인`) |
| `endLabel` | `string` | — | 끝 측 라벨 (예: `오프라인`) |
| `children` | `ReactNode` | — | 배지 자리에 넣을 내용 |
| `className` | `string` | `""` | 추가 클래스 |

### 2.3 orientation 선택 기준

| 값 | 언제 | 모양 |
| --- | --- | --- |
| `horizontal` | **위(공통) / 아래(갈래들)** 를 나눌 때 | 좌우로 선 + 가운데 배지 |
| `vertical` | **좌우 두 갈래** 를 나눌 때 | 위아래로 선 + 가운데 배지 |

`vertical` 은 **좁은 화면에서 자동으로 가로 모양**으로 전환됩니다.
두 갈래가 세로로 쌓이면 세로선이 의미를 잃기 때문입니다.

### 2.4 사용 예

```tsx
// 위/아래 분기
<Branch_Divider orientation="horizontal" label="OR" hint="서버 연결 가능 여부로 갈립니다" />

// 좌우 분기 (반응형)
<Branch_Divider orientation="vertical" startLabel="온라인" endLabel="오프라인" />

// 순차 단계 구분 (배타적이지 않을 때)
<Branch_Divider label="NEXT" />
```

### 2.5 접근성

- 라벨이 있으면 `role="separator"` + `aria-orientation` 으로 알립니다.
- 라벨도 배지도 없으면 순수 장식이므로 `aria-hidden` 으로 숨깁니다.

### 2.6 사용처

| 화면 | 설명 |
| --- | --- |
| `pages/ProjectCreation.tsx` | **환경 구성 방식** 카드 — 온라인/오프라인 갈래 표시 |

### 2.7 검증 결과

| 요소 | 실측 |
| --- | --- |
| 카드 제목 | `환경 구성 방식` |
| 분할선 | `role="separator"`, `aria-orientation="horizontal"` |
| 배지 | `OR` + `서버 연결 가능 여부로 갈립니다` |
| 갈래 라벨 | `온라인` / `오프라인` 배지 모두 렌더링 |
| 오프라인 명령 | `./sonar_validator_prober --export-once` |
| 이동 버튼 | `오프라인 데이터 가져오기 화면으로 이동` |

---

## 3. 관련 문서

- [06. 오프라인 설정 내보내기 / 가져오기](./06-offline-config-export.md)
  — 오프라인 갈래의 전체 흐름과 Agent/Backend 구현
