*** Settings ***
Documentation     실제 프론트엔드 다운로드 후 원본 번들을 WSL SSH로 전송·갱신합니다. SSH 화면은 Robot이 실제 명령 결과를 표시하는 증적 화면이며 제품 기능이 아닙니다.
Resource          ../labs/${LAB}.resource
Resource          ../resources/ui.resource
Library           ../libraries/BundleDeployment.py    ${LAB}
Library           ../libraries/LabEvidence.py    ${LAB}
Suite Setup       Prepare Deployment Lab
Suite Teardown    Finish Deployment Lab
Test Setup        Open Scenario
Test Teardown     Finish Scenario
Test Timeout      12 minutes
Test Tags         ${LAB}    deployment

*** Test Cases ***
D01_Frontend_Download_And_Actual_SSH_Deployment
    FOR    ${agent}    IN    @{SITE}[agents]
        Go To    ${UI_URL}/project
        Show And Click    css=[data-testid="project-${PROJECT_ID}"] >> role=button[name="Add Agent"]
        ${label}=    Evaluate    {'ios':'Cisco Router','eos':'Arista Switch','ubuntu':'Linux VM','ovs':'Open vSwitch(for poc)','frr':'FRRouting (for poc)'}[$agent['profile']]
        Show And Click    role=button[name="선택: ${label}"]
        Fill Text    role=textbox[name="Agent 이름"]    ${agent}[id]
        Show And Click    role=button[name="설정값 보기"]
        Get Text    css=[data-testid="deployment-server"]    *=    ${SITE}[server_ip]:3000
        Get Element Count    role=textbox[name="Management Server IP"]    ==    0
        Create Directory    ${OUTPUT_DIR}/private
        ${path}=    Set Variable    ${OUTPUT_DIR}/private/${agent}[id].tar.gz
        ${promise}=    Promise To Wait For Download    saveAs=${path}
        Show And Click    role=button[name="Agent 다운로드 (tar.gz)"]
        ${download}=    Wait For    ${promise}
        Should Be Equal    ${download}[state]    finished
        Should Be Equal    ${download}[suggestedFilename]    sonar-agent-${agent}[id].tar.gz
        ${checked}=    Inspect Frontend Bundle    ${agent}[id]    ${path}
        Scene    ${LAB_TITLE} · ${agent}[id] 실제 프론트엔드 다운로드
        Take Screenshot    ${OUTPUT_DIR}/screenshots/D01_${agent}[id]_download    selector=css=[data-testid="agent-deploy-card"]
        Go To    about:blank
        Open SSH Evidence    ${agent}[id]
        Show SSH Result    ${checked}
        ${upload}=    Upload Frontend Bundle By SSH    ${agent}[id]
        Show SSH Result    ${upload}
        ${installed}=    Install Uploaded Bundle    ${agent}[id]
        Show SSH Result    ${installed}
        Take Screenshot    ${OUTPUT_DIR}/screenshots/D01_${agent}[id]_ssh    fullPage=False
        Go To    ${UI_URL}/agent
        Wait Until Keyword Succeeds    45s    2s    Get Text    css=[data-testid="agent-row-${agent}[id]"]    *=    온라인
        Get Text    css=[data-testid="agent-row-${agent}[id]"]    *=    ${PROJECT_ID}
        Scene    ${LAB_TITLE} · ${agent}[id] 배포 후 연결과 소속 확인
        Take Screenshot    ${OUTPUT_DIR}/screenshots/D01_${agent}[id]_online    selector=css=[data-testid="agent-row-${agent}[id]"]
    END
    Save Deployment Receipts    ${OUTPUT_DIR}/evidence/frontend-ssh-deployment.json
    Verify Live Collection    ${OUTPUT_DIR}/evidence/collection-after-deployment.json
    Verify Prober Processes    ${OUTPUT_DIR}/evidence/processes-after-deployment.json

D02_Project_Endpoint_Ownership_And_Agent_Link
    Go To    ${UI_URL}/agent
    ${agent}=    Set Variable    ${SITE}[agents][0][id]
    ${row}=    Set Variable    css=[data-testid="agent-row-${agent}"]
    Wait For Elements State    ${row}    visible
    Get Text    ${row}    *=    ${PROJECT_ID}
    Take Screenshot    ${OUTPUT_DIR}/screenshots/D02_agent_list    fullPage=False
    Scene    ${LAB_TITLE} · Agent의 소속 프로젝트 링크
    Take Screenshot    ${OUTPUT_DIR}/screenshots/D02_project_link    selector=${row}
    Show And Click    ${row} >> role=link[name="${PROJECT_NAME} 프로젝트로 이동"]
    Wait For Elements State    role=button[name="관리 서버 설정"]    visible
    Show And Click    role=button[name="관리 서버 설정"]
    Get Property    role=textbox[name="Management Server IP"]    value    ==    ${SITE}[server_ip]
    Get Property    role=textbox[name="Management Server Port"]    value    ==    3000
    Fill Text    role=textbox[name="Management Server Port"]    70000
    Show And Click    role=button[name="서버 설정 저장"]
    Get Text    css=[data-testid="project-server-modal"]    *=    1~65535
    Fill Text    role=textbox[name="Management Server Port"]    3000
    Scene    ${LAB_TITLE} · 프로젝트 편집 한곳에서 관리 서버 설정
    Take Screenshot    ${OUTPUT_DIR}/screenshots/D02_project_server    selector=css=[data-testid="project-server-modal"]
    Show And Click    role=button[name="서버 설정 저장"]
    Wait For Elements State    text=관리 서버 설정을 저장했습니다.    visible
    Go To    ${UI_URL}/project/create?project_id=${PROJECT_ID}
    Wait For Elements State    css=[data-testid="deployment-server"]    visible
    Get Text    css=[data-testid="deployment-server"]    *=    ${SITE}[server_ip]:3000
    Get Element Count    role=textbox[name="Management Server IP"]    ==    0
    Get Element Count    role=textbox[name="Management Server Port"]    ==    0
    ${override}=    Evaluate JavaScript    ${None}
    ...    async (r) => {const v=await fetch(r,{credentials:'include'});return {status:v.status,body:await v.json()};}
    ...    arg=${API_URL}/api/v1/agents/download/${agent}?project_id=${PROJECT_ID}&server_ip=203.0.113.1
    Should Be Equal As Integers    ${override}[status]    400
    Should Contain    ${override}[body][message]    프로젝트 편집
    Dictionary Should Not Contain Key    ${override}[body]    trace
    Scene    ${LAB_TITLE} · 다운로드 화면은 프로젝트 설정을 읽기만 함
    Take Screenshot    ${OUTPUT_DIR}/screenshots/D02_readonly_download    selector=css=[data-testid="agent-deploy-card"]
    Show And Click    role=button[name="선택: Cisco Router"]
    ${missing}=    Evaluate    'ROBOT-Unregistered-'+__import__('uuid').uuid4().hex[:10]
    Fill Text    role=textbox[name="Agent 이름"]    ${missing}
    Show And Click    role=button[name="Agent 다운로드 (tar.gz)"]
    Wait For Elements State    text=먼저 프로젝트에 Agent를 등록하세요.    visible
    ${card_text}=    Get Text    css=[data-testid="agent-deploy-card"]
    Should Not Contain    ${card_text}    org.springframework
    Scene    ${LAB_TITLE} · 등록 누락은 필요한 안내만 표시
    Take Screenshot    ${OUTPUT_DIR}/screenshots/D02_download_error    selector=css=[data-testid="agent-deploy-card"]


D03_Deployment_Survives_VM_Restart
    [Timeout]    15 minutes
    Scene    ${LAB_TITLE} · 배포 파일 저장 후 GNS3 노드 재시작
    Reboot Deployed Lab
    Verify Live Collection    ${OUTPUT_DIR}/evidence/collection-after-reboot.json
    Verify Prober Processes    ${OUTPUT_DIR}/evidence/processes-after-reboot.json
    Save Deployment Receipts    ${OUTPUT_DIR}/evidence/frontend-ssh-deployment.json
    Go To    ${UI_URL}/project
    FOR    ${agent}    IN    @{EXPECTED_AGENTS}
        Wait Until Keyword Succeeds    45s    2s    Get Text    css=[data-testid="node-${agent}"]    *=    온라인
    END
    Scene    ${LAB_TITLE} · 재부팅 후 자동 실행·새 수집 확인
    Take Screenshot    ${OUTPUT_DIR}/screenshots/D03_reboot_verified    selector=css=[data-testid="project-${PROJECT_ID}"]


*** Keywords ***
Prepare Deployment Lab
    Wait Deployment Targets Ready
    Prepare Lab
    ${inventory}=    Deployment Inventory
    Set Suite Variable    ${SITE}    ${inventory}
    ${name}=    Evaluate    $LAB.replace("lab","Lab")
    Set Suite Variable    ${PROJECT_NAME}    ${name}

Finish Deployment Lab
    TRY
        Cleanup Remote Staging
    FINALLY
        TRY
            Remove Private Downloads
        FINALLY
            Finish Lab
        END
    END

Open SSH Evidence
    [Arguments]    ${agent}
    Evaluate JavaScript    ${None}
    ...    (agent) => {document.title='Robot SSH deployment evidence';document.body.replaceChildren();document.body.style.cssText='margin:0;background:#0f172a;color:#e2e8f0;padding:48px;font:22px monospace;line-height:1.5';const h=document.createElement('h1');h.style.cssText='font:32px sans-serif';h.textContent=agent+' · WSL SSH 배포';const p=document.createElement('p');p.style.cssText='font:18px sans-serif;color:#94a3b8';p.textContent='Robot 증적 화면: 실제 SSH 명령의 결과를 표시합니다. 제품의 SSH 업로드 기능이 아닙니다.';const pre=document.createElement('pre');pre.id='ssh-evidence';pre.style.cssText='white-space:pre-wrap;overflow-wrap:anywhere;font-size:20px';document.body.append(h,p,pre);}
    ...    arg=${agent}

Show SSH Result
    [Arguments]    ${result}
    Evaluate JavaScript    ${None}
    ...    (result) => {const pre=document.getElementById('ssh-evidence');const block=document.createElement('section');block.style.cssText='margin:20px 0;border-top:1px solid #334155;padding-top:12px';const details=document.createElement('details');const summary=document.createElement('summary');summary.textContent='실행한 명령 보기';summary.style.cssText='font:16px sans-serif;color:#93c5fd;cursor:pointer';const code=document.createElement('pre');code.textContent='$ '+result.command;code.style.cssText='white-space:pre-wrap;max-height:220px;overflow:auto;font-size:14px';details.append(summary,code);const output=document.createElement('pre');output.style.cssText='white-space:pre-wrap;font-size:20px';output.textContent=result.stdout;block.append(details,output);pre.append(block);window.scrollTo(0,document.body.scrollHeight);}
    ...    arg=${result}
    Pause For Presenter
