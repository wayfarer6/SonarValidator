import React from 'react';
import {Redirect} from '@docusaurus/router';

/**
 * 루트(`/`)를 인트로 문서로 돌립니다.
 *
 * 인트로 내용은 `docs/intro.md`(slug: intro) 한 곳에만 두고,
 * 첫 화면은 그 문서로 리다이렉트합니다.
 * (내용을 복제하지 않으므로 문서가 두 곳으로 갈라지지 않습니다)
 */
export default function Home() {
  return <Redirect to="/docs/intro" />;
}