*** Settings ***
Documentation     Professor demo: assumed incident, explicit UI isolation, actual packets/SSH/topology, UI release and exact recovery. No exploit or automatic intrusion detection.
Resource          ../labs/${LAB}.resource
Resource          ../resources/ui.resource
Library           ../libraries/LabEvidence.py    ${LAB}
Suite Setup       Prepare Lab
Suite Teardown    Finish Lab
Test Setup        Open Scenario
Test Teardown     Finish Incident Demonstration
Test Timeout      10 minutes
Test Tags         ${LAB}    presentation    live-network

*** Test Cases ***
P01_Assumed_Incident_Isolate_And_Recover
    ${scene}=    Presentation Scenario
    Wait Until Keyword Succeeds    150s    3s    Prepare Isolation Check    ${scene}[agent]    ${scene}[cidr]    ${scene}[probe]    ${scene}[destination]    ${scene}[interface_down]    ${OUTPUT_DIR}/evidence
    Go To    ${UI_URL}/network?project_id=${PROJECT_ID}
    Wait For Elements State    css=svg.flowchart    visible
    Scene    ${LAB_TITLE} · 1. 침해 징후 가정: ${scene}[title]
    Log    ${scene}[incident]
    Take Screenshot    ${OUTPUT_DIR}/screenshots/P01_01_baseline_graph    selector=css=[aria-label="네트워크 다이어그램"]
    Go To    ${UI_URL}/agent
    ${row}=    Set Variable    css=tr:has-text("${scene}[agent]")
    Show And Click    ${row} >> role=button[name="격리 설정"]
    Get Property    role=combobox[name="격리 범위"]    value    ==    __unselected
    Select Options By    role=combobox[name="격리 범위"]    value    ${scene}[cidr]
    Scene    ${LAB_TITLE} · 2. 운영자가 격리 범위 선택 · 자동 탐지 아님
    Take Screenshot    ${OUTPUT_DIR}/screenshots/P01_02_scope    fullPage=True
    Arm Isolation Cleanup
    Show And Click    role=button[name="격리 요청"]
    Wait Until Keyword Succeeds    20s    1s    Adopt UI Isolation Request
    Go To    ${UI_URL}/network?project_id=${PROJECT_ID}
    Wait Until Keyword Succeeds    150s    3s    Live Isolation Should Be Confirmed
    Wait For Function    () => document.querySelector('svg.flowchart')?.textContent.includes('격리 적용 확인')
    Scene    ${LAB_TITLE} · 3. 실제 데이터 차단 확인 · 관리 SSH 유지 · 장비 ACK 확인
    Take Screenshot    ${OUTPUT_DIR}/screenshots/P01_03_isolated_graph    selector=css=[aria-label="네트워크 다이어그램"]
    Go To    ${UI_URL}/agent
    ${release}=    Set Variable If    $scene['cidr']    서브넷 해제    장치 격리 해제
    Scene    ${LAB_TITLE} · 4. 원인 조치 완료를 가정하고 운영자가 격리 해제
    Show And Click    ${row} >> role=button[name="${release}"]
    Wait Until Keyword Succeeds    20s    1s    Adopt UI Release Request
    Go To    ${UI_URL}/network?project_id=${PROJECT_ID}
    Wait Until Keyword Succeeds    150s    3s    Live Release Should Be Confirmed
    Wait For Function    () => {const s=document.querySelector('svg.flowchart');return s && !s.textContent.includes('격리 적용 확인') && !s.textContent.includes('해제 확인 대기');}
    Scene    ${LAB_TITLE} · 5. 실제 통신 및 관측 연결 복구 · 원래 경로/포트 상태 비교 완료
    Take Screenshot    ${OUTPUT_DIR}/screenshots/P01_05_recovered_graph    selector=css=[aria-label="네트워크 다이어그램"]
    Pause For Presenter

*** Keywords ***
Finish Incident Demonstration
    TRY
        Restore Live Isolation
    FINALLY
        Finish Scenario
    END
