*** Settings ***
Documentation     Real isolation/release: packets, management SSH, current-operation ACK, collected topology and live SVG. All changes are restored in teardown.
Resource          ../labs/${LAB}.resource
Resource          ../resources/ui.resource
Library           ../libraries/LabEvidence.py    ${LAB}
Suite Setup       Prepare Lab
Suite Teardown    Finish Lab
Test Setup        Open Scenario
Test Teardown     Finish Isolation Scenario
Test Timeout      8 minutes
Test Tags         ${LAB}    isolation    live-network

*** Variables ***
${ISOLATION_AGENT}    Lab1-R1
${ISOLATION_CIDR}     192.168.42.0/24
${PROBE_AGENT}        Lab1-R1
${PROBE_DESTINATION}  192.168.42.1
${INTERFACE_DOWN}     true

*** Test Cases ***
Q01_Isolate_Observe_And_Release
    Wait Until Keyword Succeeds    150s    3s    Prepare Isolation Check    ${ISOLATION_AGENT}    ${ISOLATION_CIDR}    ${PROBE_AGENT}    ${PROBE_DESTINATION}    ${INTERFACE_DOWN}    ${OUTPUT_DIR}/evidence
    Go To    ${UI_URL}/network?project_id=${PROJECT_ID}
    Wait For Elements State    css=svg.flowchart    visible
    Get Property    css=[aria-label="네트워크 다이어그램"]    clientHeight    <=    ${600}
    Scene    ${LAB_TITLE} · ${ISOLATION_AGENT} · 격리 전 실제 통신과 수집된 토폴로지
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q01_before    fullPage=True
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q01_before_graph    selector=css=[aria-label="네트워크 다이어그램"]
    Request Live Isolation
    Scene    ${LAB_TITLE} · ${ISOLATION_AGENT} · 격리 요청 → 장비 적용 → 데이터 통신 차단 확인
    Wait Until Keyword Succeeds    150s    3s    Live Isolation Should Be Confirmed
    Wait For Function    () => {const svg=document.querySelector('svg.flowchart');return svg && svg.textContent.includes('격리 적용 확인');}
    Scene    ${LAB_TITLE} · ${ISOLATION_AGENT} · 새로고침 없이 토폴로지의 격리 상태 갱신
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q01_isolated    fullPage=True
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q01_isolated_graph    selector=css=[aria-label="네트워크 다이어그램"]
    Request Live Release
    Scene    ${LAB_TITLE} · ${ISOLATION_AGENT} · 해제 요청 → 통신 복구 → 수집된 연결 복원
    Wait Until Keyword Succeeds    150s    3s    Live Release Should Be Confirmed
    Wait For Function    () => {const svg=document.querySelector('svg.flowchart');return svg && !svg.textContent.includes('격리 적용 확인') && !svg.textContent.includes('해제 확인 대기');}
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q01_released    fullPage=True
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q01_released_graph    selector=css=[aria-label="네트워크 다이어그램"]
    Pause For Presenter

Q02_UI_Scope_Selection_And_Apply
    [Tags]    ui-isolation
    Wait Until Keyword Succeeds    150s    3s    Prepare Isolation Check    ${ISOLATION_AGENT}    ${ISOLATION_CIDR}    ${PROBE_AGENT}    ${PROBE_DESTINATION}    ${INTERFACE_DOWN}    ${OUTPUT_DIR}/evidence
    Go To    ${UI_URL}/agent
    ${row}=    Set Variable    css=tr:has-text("${ISOLATION_AGENT}")
    Show And Click    ${row} >> role=button[name="격리 설정"]
    Get Property    role=combobox[name="격리 범위"]    value    ==    __unselected
    Get Property    role=button[name="격리 요청"]    disabled    ==    ${True}
    Select Options By    role=combobox[name="격리 범위"]    value    ${ISOLATION_CIDR}
    Scene    ${ISOLATION_AGENT} · 격리 범위를 명시적으로 선택
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q02_scope_modal    fullPage=True
    Arm Isolation Cleanup
    Show And Click    role=button[name="격리 요청"]
    Wait Until Keyword Succeeds    20s    1s    Adopt UI Isolation Request
    Go To    ${UI_URL}/network?project_id=${PROJECT_ID}
    Wait Until Keyword Succeeds    150s    3s    Live Isolation Should Be Confirmed
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q02_applied    fullPage=True
    Request Live Release
    Wait Until Keyword Succeeds    150s    3s    Live Release Should Be Confirmed
    Scene    ${ISOLATION_AGENT} · 화면에서 선택한 범위만 적용하고 복구 확인
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q02_restored    fullPage=True

Q03_Active_Isolation_Journal_Survives_VM_Restart
    [Tags]    reboot-isolation
    [Timeout]    15 minutes
    Skip If    '${LAB}' != 'lab1'    This power-cycle regression uses Lab1 Cisco devices.
    Wait Until Keyword Succeeds    150s    3s    Prepare Isolation Check    ${ISOLATION_AGENT}    ${ISOLATION_CIDR}    ${PROBE_AGENT}    ${PROBE_DESTINATION}    ${INTERFACE_DOWN}    ${OUTPUT_DIR}/evidence
    Request Live Isolation
    Wait Until Keyword Succeeds    150s    3s    Live Isolation Should Be Confirmed
    Go To    ${UI_URL}/network?project_id=${PROJECT_ID}
    Scene    ${ISOLATION_AGENT} · 격리 원본 상태를 저장한 채 VM 재시작
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q03_before_reboot    fullPage=True
    Reboot Isolated Device And Verify Journal
    Wait Until Keyword Succeeds    150s    3s    Live Isolation Should Be Confirmed
    Request Live Release
    Wait Until Keyword Succeeds    150s    3s    Live Release Should Be Confirmed
    Scene    ${ISOLATION_AGENT} · 재시작 후 원본 포트 상태와 통신 복구
    Take Screenshot    ${OUTPUT_DIR}/screenshots/Q03_recovered    fullPage=True

*** Keywords ***
Finish Isolation Scenario
    TRY
        Restore Live Isolation
    FINALLY
        Finish Scenario
    END
