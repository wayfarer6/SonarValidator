// SVG 를 React 컴포넌트로 import 할 때의 타입 선언입니다.
//
// ⚠️ `import ... = require(...)` 를 쓰지 않습니다.
//   TypeScript 에서는 그 형태가 React 타입을 값으로도 가져와야 해서 동작하지만,
//   `@typescript-eslint/no-require-imports` 규칙에 걸립니다. 규칙은 **타입 전용
//   import** 를 허용하므로 타입 위치에서만 쓰는 형태로 바꿉니다.
//   (CI 의 `npm run lint` 가 이 한 줄 때문에 실패하던 것을 해소)
declare module "*.svg?react" {
  import type { FC, SVGProps } from "react";

  export const ReactComponent: FC<SVGProps<SVGSVGElement>>;

  const src: string;
  export default src;
}
