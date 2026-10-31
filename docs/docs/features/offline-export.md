# 오프라인 JSON 파일 출력

Prober는 파일 출력 경로 하나만 제공합니다.

```bash
./sonar_validator_prober --export snapshot.json
```

한 번 수집하고 `snapshot.json`을 만든 뒤 종료합니다. 백엔드에 연결하지 않으며 세 개 worker를 시작하지 않습니다. 설정·템플릿 위치는 일반 실행과 같은 `SONAR_CONFIG_PATH`, `SONAR_DATA_DIR`, `SONAR_TEMPLATE_PATH`를 사용합니다. 생성한 파일은 프로젝트 화면의 오프라인 Prober 데이터 가져오기에 업로드합니다.

## 함수와 파일 계약

- 실제 기록: `offline::ExportSnapshot(target, document)` 한 함수.
- 문서 조립: `BuildSnapshotDocument()`가 `sonar.offline.snapshot`, 버전 1, 장비 정보, 수집 시각, payload를 만듭니다.
- 임시 파일은 대상 파일과 같은 디렉터리에 고유 이름·0600 권한으로 만듭니다. 쓰기/flush 후 rename으로 교체해 부분 파일이 노출되지 않게 합니다.
- 출력 실패는 종료 코드 1입니다. 잘못된 CLI 인자는 초기화 전에 종료 코드 2로 거부합니다.
- 실행 중 서버 전송에 실패하면 로컬 SQLite 기록을 유지하고 다음 주기에 재접속합니다. 자동으로 export 파일을 쌓는 동작은 제거했습니다.

## 이전 명령에서 변경

`--export-once`, `--export-offline`, `--export-stdout`, `--export-dir`, `SONAR_OFFLINE_DIR`는 더 이상 사용하지 않습니다. 자동화 명령은 `--export /원하는/경로/snapshot.json`으로 변경해야 합니다. 제거한 옵션은 조용히 무시하거나 daemon 실행으로 이어지지 않고 오류로 끝납니다.

## 정책 보고서와의 차이

정책 Export 화면은 **파일로 내보내기** 버튼 하나로 검증 보고서 JSON을 저장합니다. CSV 경로는 제거했습니다. 보고서에는 검증 범위·입력 오류·반례 근거·금지 쌍이 들어갑니다. 이는 Prober 스냅샷과 다른 문서이며 오프라인 Prober 데이터로 업로드하는 파일이 아닙니다.

테스트는 스키마·UTF-8 왕복, 병렬 출력의 완전한 교체, 파일 권한·오류 반환·남은 임시 파일과 CLI 모드 단일화를 검증합니다. [장비 확장 UML](../architecture/device-extensions.md)도 참조합니다.
