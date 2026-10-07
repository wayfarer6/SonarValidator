SonarValidator Agent 설치 안내
==============================

Agent 이름 : PRJ-79154FFA-Linux-VM-agent
장치 유형  : VM
서버 주소  : 172.28.158.87:3000
⚠️ 이 이름과 장치 유형은 서버에 이미 등록되어 있습니다.
   바꾸면 서버가 다른 장치로 인식합니다.

0. 번들 풀기
------------
  tar -xzf sonar-agent-PRJ-79154FFA-Linux-VM-agent.tar.gz
  cd Installer
(장비에 unzip 이 없어도 됩니다 — tar·gzip 은 기본 포함입니다)

1. 바이너리 준비
-----------------
이 번들에는 설정·스크립트와 함께 스테이징된 정적 바이너리
{@code sonar_validator_prober} 가 포함될 수 있습니다.
바이너리가 없다면 서버 배포 단계에서 스테이징이 누락된 것이므로,
먼저 정적 빌드를 만들고 /tmp/sonar_stage 에 넣어 주세요:

  cd SonarValidator_Prober && cmake -S . -B build_static -DCMAKE_BUILD_TYPE=Release
  cmake --build build_static --target sonar_validator_prober
  cp build_static/sonar_validator_prober /tmp/sonar_stage/

장치에 이미 바이너리가 있으면 다음 단계로 바로 진행하세요.
(운영 환경에서 HTTP 다운로드를 쓸 수도 있지만, 번들 안에 포함된
 바이너리가 있으면 가장 단순하고 안전합니다)

2. 설정 배치
------------
  mkdir -p /opt/sonar_validator/data
  cp default.conf /opt/sonar_validator/default.conf
  cp default_template.sqlite /opt/sonar_validator/
  rm -f /opt/sonar_validator/data/settings.conf

⚠️ 기존 settings.conf 를 지워야 새 AGENT_NAME 이 반영됩니다.
   남아 있으면 예전 이름으로 접속해 배포 예정과 합쳐지지 않습니다.

3. 실행
-------
  cd /opt/sonar_validator
  SONAR_DATA_DIR=/opt/sonar_validator/data \
  SONAR_TEMPLATE_PATH=/opt/sonar_validator/default_template.sqlite \
  SONAR_CONFIG_PATH=/opt/sonar_validator/default.conf \
    nohup ./sonar_validator_prober > run.log 2>&1 &

4. 재시작 (⚠️ SIGTERM 필수)
--------------------------
  sh restart.sh 172.28.158.87 VM

kill -9 는 SQLite 에 hot journal 을 남겨 다음 기동이
"Runtime initialization failed" 로 실패합니다.

5. 장치별 참고
--------------
PRJ-79154FFA-Linux-VM-agent

6. 확인
-------
  30초 주기로 텔레메트리를 전송합니다.
  서버에서 GET /api/v1/agents/overview 로 "connected" 를 확인하세요.
