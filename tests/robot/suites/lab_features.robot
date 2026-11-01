*** Settings ***
Documentation     Lab1~4 주요 기능을 실제 수집·네트워크 검사와 화면 조작으로 검증합니다. 정책 푸시·격리는 수행하지 않습니다.
Resource          ../labs/${LAB}.resource
Resource          ../resources/ui.resource
Library           ../libraries/LabEvidence.py    ${LAB}
Suite Setup       Prepare Feature Lab
Suite Teardown    Finish Feature Lab
Test Setup        Open Scenario
Test Teardown     Finish Scenario
Test Timeout      5 minutes
Test Tags         ${LAB}    features

*** Variables ***
${MOCK_PROVIDER_ID}    ${EMPTY}

*** Test Cases ***
F01_Project_And_Live_Collection
    ${result}=    Verify Live Collection    ${OUTPUT_DIR}/evidence/collection.json
    FOR    ${agent}    IN    @{EXPECTED_AGENTS}
        Wait Until Keyword Succeeds    30s    2s    Get Text    css=[data-testid="node-${agent}"]    *=    온라인
    END
    Scene    ${LAB_TITLE} · 실제 장비 수집과 프로젝트 소속 확인
    Highlight Elements    css=[data-testid="project-${PROJECT_ID}"]    duration=1200ms
    Take Screenshot    ${OUTPUT_DIR}/screenshots/F01_project_collection    selector=css=[data-testid="project-${PROJECT_ID}"]
    Pause For Presenter

F02_Network_And_Device_Path
    ${network}=    Verify Network Path    ${OUTPUT_DIR}/evidence/network.json
    IF    'firewall' in $SITE
        Go To    ${UI_URL}/api-nodes
        ${row}=    Set Variable    css=tr:has-text("${SITE}[firewall][id]")
        Wait For Elements State    ${row} >> role=button[name="연결 테스트"]    enabled
        Show And Click    ${row} >> role=button[name="연결 테스트"]
        Wait For Elements State    ${row} >> role=button[name="연결 테스트"]    enabled
        ${credential}=    Call API    opnsense/credentials/${SITE}[firewall][id]
        Should Be Equal    ${credential}[status]    OK
        Get Text    ${row}    *=    Key·Secret 저장됨
        Scene    ${LAB_TITLE} · OPNsense API 연결과 인터페이스·규칙·NAT 조회
        Take Screenshot    ${OUTPUT_DIR}/screenshots/F02_opnsense_api    fullPage=True
    ELSE
        Show And Click    css=[data-testid="project-${PROJECT_ID}"] >> role=link[name="Manage"]
        Wait For Elements State    text=서브넷 등급 설정    visible
        Scene    ${LAB_TITLE} · 인터넷 경로와 관리망 분리 확인
        Take Screenshot    ${OUTPUT_DIR}/screenshots/F02_network_path    fullPage=True
    END
    Pause For Presenter

F03_Node_Connections_And_Interval
    Open Node Connections    ${SITE}[settings_agent]
    Get Element Count    role=combobox[name="서브넷 선택"]    ==    1
    Show And Click    role=combobox[name="네트워크 역할"]
    Press Keys    role=combobox[name="네트워크 역할"]    Escape
    Select Options By    role=combobox[name="네트워크 역할"]    value    SERVICE
    Select Options By    role=combobox[name="CSO 보안 등급"]    value    Sensitive
    Scene    ${LAB_TITLE} · 노드별 역할·보안 등급·연결 대상 선택
    Take Screenshot    ${OUTPUT_DIR}/screenshots/F03_connection_modal    selector=css=[data-testid="node-settings-modal"]
    Handle Future Dialogs    accept
    Show And Click    role=button[name="노드 설정 닫기"]
    ${agent}=    Set Variable    ${SITE}[settings_agent]
    ${original}=    Call API    agents/${agent}/monitor-settings?project_id=${PROJECT_ID}
    TRY
        Open Node Settings    ${agent}
        ${target}=    Evaluate    '2' if $original['monitor_interval_seconds'] != 2 else '3'
        Select Options By    role=combobox[name="수집 주기 단위"]    value    seconds
        Fill Text    role=spinbutton[name="수집 주기 값"]    ${target}
        Show And Click    role=button[name="수집 주기 저장"]
        Wait Until Keyword Succeeds    30s    1s    Check Monitor Applied    ${agent}    ${target}
        Scene    ${LAB_TITLE} · 노드 수집 주기 변경과 Prober 적용 확인
        Take Screenshot    ${OUTPUT_DIR}/screenshots/F03_interval_applied    selector=css=[data-testid="node-settings-modal"]
    FINALLY
        ${current}=    Call API    agents/${agent}/monitor-settings?project_id=${PROJECT_ID}
        ${body}=    Evaluate    {'monitor_interval_seconds':$original['monitor_interval_seconds'],'revision':$current['revision']}
        Call API    agents/${agent}/monitor-settings?project_id=${PROJECT_ID}    PUT    ${body}
    END

F04_Authenticated_Web_Terminal
    Observe Terminal Output
    ${agent}=    Set Variable    ${SITE}[terminal_agent]
    Show And Click    css=[data-testid="node-${agent}"] >> role=button[name="터미널 열기"]
    Wait For Shell Prompt
    Keyboard Input    type    echo LAB_FEATURE_OK_${LAB}; id -un    delay=60ms
    Press Keys    css=.xterm-helper-textarea    Enter
    Wait For Function    () => window.__robotTerminal.split('LAB_FEATURE_OK_${LAB}').length >= 3
    Scene    ${LAB_TITLE} · 인증 채널의 실제 장비 셸 응답
    Take Screenshot    ${OUTPUT_DIR}/screenshots/F04_terminal    selector=css=[data-testid="node-${agent}"]
    Pause For Presenter
    Show And Click    css=[data-testid="project-${PROJECT_ID}"] >> role=button[name="Agent 목록 새로고침"]
    Scene    ${LAB_TITLE} · 목록 갱신 중에도 같은 터미널 세션 유지
    Sleep    16s
    ${connections}=    Evaluate JavaScript    ${None}    () => window.__robotTerminalConnections
    ${starts}=    Evaluate JavaScript    ${None}    () => window.__robotShellStarts
    Should Be Equal As Integers    ${connections}    1
    Should Be Equal As Integers    ${starts}    1
    Focus    css=.xterm-helper-textarea
    Keyboard Input    type    exit
    Press Keys    css=.xterm-helper-textarea    Enter
    Wait For Elements State    text=셸 종료됨    visible
    Show And Click    css=button[aria-label="터미널 닫기"]

F05_Bdd_Policy_And_Single_Export
    Create Fixture
    ${subnets}=    Evaluate    [{'id':'ROBOT-A','cidr':$SITE['demo_cidr'],'name':'${LAB} service example','agent_id':$FIXTURE_AGENT,'subnet_class':'Confidential','network_role':'SERVICE','manually_edited':True,'allowed_peers':[]},{'id':'ROBOT-B','cidr':'198.51.100.0/24','name':'External test example','agent_id':None,'subnet_class':'Open','network_role':'INTERNET','manually_edited':True,'allowed_peers':[]}]
    ${rules}=    Evaluate    [{'id':'ROBOT-CROSS','src':'ROBOT-A','dst':'ROBOT-B','protocol':'tcp','port':443,'enabled':True}]
    &{body}=    Create Dictionary    subnets=${subnets}    rules=${rules}
    Call API    projects/${FIXTURE_ID}    PUT    ${body}
    ${denied}=    Call API    projects/${FIXTURE_ID}/validation
    Should Be Equal    ${denied}[model_valid]    ${True}
    Should Be Equal    ${denied}[compliant]    ${False}
    Should Be Equal    ${denied}[data_plane_analyzed]    ${False}
    Go To    ${UI_URL}/policy?project_id=${FIXTURE_ID}
    Wait For Elements State    css=[data-testid="policy-analysis-scope"]    visible
    Scene    ${LAB_TITLE} · 임시 정책 모델에서 Confidential과 Open 연결 위반
    Take Screenshot    ${OUTPUT_DIR}/screenshots/F05_policy_violation    fullPage=True
    ${allowed}=    Evaluate    [{**s,'subnet_class':'Sensitive'} if s['id']=='ROBOT-A' else s for s in $subnets]
    &{body}=    Create Dictionary    subnets=${allowed}    rules=${rules}
    Call API    projects/${FIXTURE_ID}    PUT    ${body}
    ${report}=    Call API    projects/${FIXTURE_ID}/validation
    Should Be Equal    ${report}[compliant]    ${True}
    Should Be Equal    ${report}[analysis_scope]    POLICY_HEADER_SPACE
    Should Be Equal    ${report}[data_plane_analyzed]    ${False}
    Go To    ${UI_URL}/policy/export?project_id=${FIXTURE_ID}
    Wait For Elements State    css=[data-testid="export-policy-file"]    enabled
    Create Directory    ${OUTPUT_DIR}/downloads
    ${download}=    Promise To Wait For Download    saveAs=${OUTPUT_DIR}/downloads/${LAB}-policy.json
    Show And Click    css=[data-testid="export-policy-file"]
    ${file}=    Wait For    ${download}
    Should Be Equal    ${file}[state]    finished
    ${content}=    Get File    ${OUTPUT_DIR}/downloads/${LAB}-policy.json
    ${export}=    Evaluate    json.loads($content)    modules=json
    Should Be Equal    ${export}[analysis_scope]    POLICY_HEADER_SPACE
    Should Be Equal    ${export}[data_plane_analyzed]    ${False}
    Scene    ${LAB_TITLE} · 허용 정책 모델과 JSON 결과 파일
    Take Screenshot    ${OUTPUT_DIR}/screenshots/F05_policy_export    fullPage=True
    Pause For Presenter

F06_Mock_AI_Review_Execution_And_Rejection
    Open Node AI
    Request Mock Advice    ${SITE}[ai_prompt]
    ${command}=    Set Variable    css=[data-testid="ai-command-0"] >> nth=0
    Get Text    ${command}    *=    ${SITE}[ai_command]
    Show And Click    ${command} >> role=button[name="Prober 실행 검토"]
    Wait For Elements State    role=button[name="확인 후 Prober 실행"]    disabled
    Check Checkbox    css=[data-testid="ai-execution-review"] input[type="checkbox"]
    Show And Click    role=button[name="확인 후 Prober 실행"]
    Wait Until Keyword Succeeds    45s    1s    Get Text    ${command} >> css=[data-testid="ai-execution-result"]    *=    실행·결과 확인됨
    ${history}=    Call API    policy/advice/${PROJECT_ID}?agent_id=${SITE}[ai_agent]&limit=1
    ${advice_id}=    Set Variable    ${history}[advices][0][advice_id]
    ${executions}=    Call API    policy/advice/${PROJECT_ID}/${advice_id}/executions
    Should Be Equal    ${executions}[executions][0][result][verified]    ${True}
    Should Be Equal    ${executions}[executions][0][result][changed]    ${False}
    ${json}=    Evaluate    json.dumps($executions,ensure_ascii=False,indent=2)    modules=json
    Create Directory    ${OUTPUT_DIR}/evidence
    Create File    ${OUTPUT_DIR}/evidence/ai-readonly-execution.json    ${json}    encoding=UTF-8
    Scene    ${LAB_TITLE} · 모의 AI 제안을 검토하고 실제 조회 결과 확인
    Take Screenshot    ${OUTPUT_DIR}/screenshots/F06_ai_executed    selector=${command}
    Request Mock Advice    MOCK_MANUAL
    Get Text    ${command}    *=    reload
    Wait For Elements State    ${command} >> role=button[name="Prober 실행 검토"]    disabled
    Get Text    ${command}    *=    Prober 실행 불가
    Scene    ${LAB_TITLE} · 미지원 AI 명령은 실행 차단
    Take Screenshot    ${OUTPUT_DIR}/screenshots/F06_ai_rejected    selector=${command}
    Pause For Presenter

*** Keywords ***
Prepare Feature Lab
    ${site}=    Lab Inventory
    Set Suite Variable    ${SITE}    ${site}
    Set Suite Variable    ${PROJECT_ID}    ${site}[project_id]
    Set Suite Variable    ${LAB_TITLE}    ${site}[title]
    ${agents}=    Evaluate    [a['id'] for a in $site['agents']]
    Set Suite Variable    ${EXPECTED_AGENTS}    ${agents}
    Prepare Lab
    New Context    storageState=${AUTH_STATE}
    New Page    ${UI_URL}/project
    ${body}=    Evaluate    {'name':'ROBOT-${LAB}-'+__import__('uuid').uuid4().hex[:8],'base_url':'http://127.0.0.1:3049/v1','model':'mock-advice-v1','auth_style':'none','enabled':True,'is_default':False,'timeout_seconds':10}
    ${provider}=    Call API    ai/providers    POST    ${body}
    ${id}=    Convert To String    ${provider}[id]
    Set Suite Variable    ${MOCK_PROVIDER_ID}    ${id}
    Close Context

Finish Feature Lab
    TRY
        IF    $MOCK_PROVIDER_ID
            New Context    storageState=${AUTH_STATE}
            New Page    ${UI_URL}/project
            Call API    ai/providers/${MOCK_PROVIDER_ID}    DELETE
            Close Context
        END
        Sleep    2s
        Verify Prober Processes    ${OUTPUT_DIR}/evidence/processes.json
    FINALLY
        Finish Lab
    END

Open Node AI
    Open Node Settings    ${SITE}[ai_agent]
    Show And Click    role=button[name="AI 진단·명령"]
    Wait For Elements State    role=combobox[name="AI 공급자"]    visible
    Select Options By    role=combobox[name="AI 공급자"]    value    ${MOCK_PROVIDER_ID}

Request Mock Advice
    [Arguments]    ${prompt}
    Fill Text    role=textbox[name="AI 추가 질문"]    ${prompt}
    Show And Click    role=button[name="AI 진단·명령 요청"]
    Wait For Elements State    role=button[name="AI 진단·명령 요청"]    enabled
    Wait For Elements State    css=[data-testid="ai-command-0"] >> nth=0    visible
