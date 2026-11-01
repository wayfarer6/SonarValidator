# Robot 녹화의 발표 자료 수록

`prepare_clips.py`는 최종 Robot `output.xml`의 캡처 시각과 시나리오별 WebM을 이용해 발표용 H.264/yuv420p MP4와 실제 프레임 PNG를 만듭니다. 재생 속도는 바꾸지 않습니다. Lab1은 전체 페이지 캡처 때문에 영상의 viewport 크기가 바뀌기 직전에서 끝냅니다. 원본 WebM과 전체 캡처는 그대로 보관합니다.

```bash
python -m pip install -r tests/presentation/requirements.txt
python tests/presentation/prepare_clips.py EVIDENCE_ROOT MEDIA_DIR
```

`EVIDENCE_ROOT/robot/lab1`부터 `lab4`까지 최종 결과를 복사한 뒤 실행합니다. `--lab lab1`처럼 하나만 갱신할 수 있습니다. `manifest.json`에 지정한 슬라이드 번호에 대응하는 포스터를 PPTX에 각각 이미지 하나로 배치합니다. 표와 설명은 편집 가능한 일반 PowerPoint 개체를 사용합니다.

```bash
python tests/presentation/embed_recordings.py posters.pptx with-video.pptx MEDIA_DIR/manifest.json --audit video-audit.json
```

대상 파일은 새 이름이어야 합니다. 기존 원본을 덮어쓰지 않습니다. 영상은 외부 링크 대신 PPTX의 `ppt/media`에 넣고, 클릭 재생 동작과 video/media 관계를 추가합니다. 절대·상대 OOXML 관계 경로를 모두 처리합니다. 관련 XML 전체를 파싱하고 영상 해시를 감사 파일에 기록합니다.

최종 확인은 XML/관계 무결성, 요청한 슬라이드 수, 편집 가능한 표, 글꼴과 배치, 포스터 렌더링, MP4 전체 디코딩을 포함합니다. 이 검사만으로 실제 PowerPoint 재생 성공을 주장하지 않습니다. PowerPoint가 설치된 환경에서는 슬라이드 쇼의 영상 영역을 클릭해 재생을 확인합니다. 외부 플레이어용 MP4도 함께 제공합니다.

미디어 구문은 [Microsoft Open XML 영상 추가 문서](https://learn.microsoft.com/en-us/office/open-xml/presentation/how-to-add-a-video-to-a-slide-in-a-presentation)를 참고합니다.

한 슬라이드에 여러 Lab 영상을 배치하면 manifest의 `poster_index`(0부터 시작)로 대상 이미지 순서를 명시합니다. 재생 관계 ID는 슬라이드 번호와 이미지 인덱스를 함께 사용합니다. 이미 영상이 붙은 포스터, 범위를 벗어난 인덱스, 모호한 다중 포스터는 실패 처리합니다. 전체 WebM은 근거 묶음에 보존하고, 발표용 클립의 자른 구간과 속도 변경 여부를 manifest에 기록합니다.

교수님 시연은 `tests/robot/suites/presentation_demo.robot`의 실제 UI 격리·해제 리허설을 사용합니다. 5분 발표 본문 8장에 이어 Lab별 상세 시나리오 4장을 부록으로 배치합니다. 침해 징후는 상황 가정이며 영상이 자동 침해 탐지의 증거는 아닙니다. 자세한 실행 순서는 `docs/docs/testing/professor-demo.md`에 있습니다.
