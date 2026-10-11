# Prober compiler image

C++23, CMake, ANTLR4 C++ runtime을 포함한 Prober 전용 컴파일 이미지입니다.
Backend 런타임 및 최종 compiler 산출 이미지에는 이 툴체인이 들어가지 않고,
빌드 결과인 `/tmp/sonar_stage`만 전달됩니다.

저장소 루트에서 다음과 같이 독립적으로 빌드할 수 있습니다.

```bash
docker compose -f docker/compose/compose.yaml build prober-compiler
```

Backend 빌드 시에는 Compose의 추가 빌드 컨텍스트를 통해 자동으로 함께 빌드됩니다.
