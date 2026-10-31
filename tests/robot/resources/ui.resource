*** Settings ***
Library    Browser    auto_closing_level=TEST    timeout=20s
Library    OperatingSystem
Library    Collections
Library    String

*** Variables ***
${UI_URL}          %{SONAR_UI_URL=http://localhost:5173}
${API_URL}         %{SONAR_API_URL=http://localhost:3000}
${DOCS_URL}        %{SONAR_DOCS_URL=http://localhost:3001}
${USERNAME}        %{SONAR_UI_USERNAME=admin}
${PASSWORD}        %{SONAR_UI_PASSWORD=}
${HEADLESS}        ${True}
${SLOW_MO}         0ms
${STEP_PAUSE}      0s
${TERMINAL_AGENT_FILTER}  %{SONAR_TERMINAL_AGENT=}
${TERMINAL_CYCLES}  %{SONAR_TERMINAL_CYCLES=1}
${TERMINAL_STRESS}  %{SONAR_TERMINAL_STRESS=false}
${FIXTURE_ID}      ${EMPTY}
${FIXTURE_AGENT}   ${EMPTY}

*** Keywords ***
Prepare Lab
    IF    not $PROJECT_ID
        Skip    ${LAB_TITLE}: SonarValidator 프로젝트가 아직 설정되지 않았습니다. 네트워크 구성과 프로그램 검증을 구분합니다.
    END
    IF    not $PASSWORD
        Fail    SONAR_UI_PASSWORD 환경변수를 설정하세요.
    END
    New Browser    chromium    headless=${HEADLESS}    slowMo=${SLOW_MO}
    New Context    viewport={"width":1600,"height":1000}
    New Page    ${UI_URL}/signin
    Fill Text    css=input[placeholder="admin"]    ${USERNAME}
    Fill Secret    css=input[type="password"]    $PASSWORD
    Click    role=button[name="Sign in"]
    Wait For Elements State    text=Default Administrator    visible
    ${state}=    Save Storage State    ${OUTPUT_DIR}/.auth.json
    Set Suite Variable    ${AUTH_STATE}    ${state}
    Close Context

Open Scenario
    ${slug}=    Replace String    ${TEST_NAME}    ${SPACE}    _
    Set Test Variable    ${SCENARIO_SLUG}    ${slug}
    Create Directory    ${OUTPUT_DIR}/videos/${LAB}/${slug}
    Create Directory    ${OUTPUT_DIR}/screenshots
    New Context    storageState=${AUTH_STATE}    viewport={"width":1600,"height":1000}    acceptDownloads=${True}
    ...    recordVideo={"dir":"${OUTPUT_DIR}/videos/${LAB}/${slug}","size":{"width":1600,"height":1000}}
    New Page    ${UI_URL}/project
    Wait For Elements State    css=[data-testid="project-${PROJECT_ID}"]    visible
    Scene    ${LAB_TITLE} · ${TEST_NAME}

Finish Scenario
    Run Keyword And Ignore Error    Take Screenshot    ${OUTPUT_DIR}/screenshots/${SCENARIO_SLUG}    fullPage=True
    TRY
        Clean Fixture
    FINALLY
        Close Context
    END

Finish Lab
    Run Keyword And Ignore Error    Close Browser    ALL
    Run Keyword And Ignore Error    Remove File    ${OUTPUT_DIR}/.auth.json

Scene
    [Arguments]    ${caption}
    Evaluate JavaScript    ${None}
    ...    (label) => { let e=document.getElementById('robot-scene'); if(!e){e=document.createElement('aside');e.id='robot-scene';document.body.appendChild(e);} e.textContent=label;e.style.cssText='position:fixed;bottom:12px;left:320px;right:24px;z-index:1000000;background:#102b4e;color:white;padding:10px 18px;border-radius:8px;font:18px sans-serif;pointer-events:none;'; }
    ...    arg=${caption}
    Pause For Presenter

Pause For Presenter
    Sleep    ${STEP_PAUSE}

Show And Click
    [Arguments]    ${selector}
    Highlight Elements    ${selector}    duration=1200ms    color=#2563eb
    Pause For Presenter
    Click    ${selector}
    Pause For Presenter

Open Node Connections
    [Arguments]    ${agent}
    Show And Click    css=[data-testid="project-${PROJECT_ID}"] >> role=button[name="${agent} 연결 관리"]
    Wait For Elements State    css=[data-testid="node-connection-modal"]    visible
    Wait For Elements State    role=combobox[name="서브넷 선택"]    visible

Call API
    [Arguments]    ${path}    ${method}=GET    ${data}=${None}
    &{request}=    Create Dictionary    url=${API_URL}/api/v1/${path}    method=${method}    data=${data}
    ${result}=    Evaluate JavaScript    ${None}
    ...    async (r) => { const v=await fetch(r.url,{signal:AbortSignal.timeout(15000),method:r.method,credentials:'include',headers:{'Content-Type':'application/json'},body:r.data===null?undefined:JSON.stringify(r.data)}); const text=await v.text(); let data;try{data=JSON.parse(text);}catch{data=text;} return {status:v.status,data}; }
    ...    arg=${request}
    Should Be True    200 <= ${result}[status] < 300    API ${method} ${path}: ${result}[status]
    RETURN    ${result}[data]

Create Fixture
    ${nonce}=    Evaluate    __import__('uuid').uuid4().hex[:10]
    Set Test Variable    ${FIXTURE_AGENT}    ROBOT-${nonce}
    &{meta}=    Create Dictionary    name=Robot UI temporary ${nonce}    description=Automatic test fixture; removed at teardown
    ${created}=    Call API    projects    POST    ${meta}
    Set Test Variable    ${FIXTURE_ID}    ${created}[project_id]
    &{registration}=    Create Dictionary    agent_id=${FIXTURE_AGENT}    project_id=${FIXTURE_ID}    device_type=ROUTER    node_type=Router    expected_ip=198.51.100.10
    Call API    agents/expected    POST    ${registration}
    ${subnets}=    Evaluate    [{'id':'ROBOT-A','cidr':'10.250.10.0/24','name':'Robot source','agent_id':$FIXTURE_AGENT,'subnet_class':'Sensitive','network_role':'SERVICE','manually_edited':True,'allowed_peers':[]},{'id':'ROBOT-B','cidr':'10.250.20.0/24','name':'Robot destination','agent_id':None,'subnet_class':'Sensitive','network_role':'SERVICE','manually_edited':True,'allowed_peers':[]}]
    &{body}=    Create Dictionary    subnets=${subnets}
    Call API    projects/${FIXTURE_ID}    PUT    ${body}

Clean Fixture
    IF    $FIXTURE_AGENT
        Call API    agents/expected/${FIXTURE_AGENT}?project_id=${FIXTURE_ID}    DELETE
    END
    IF    $FIXTURE_ID
        Call API    projects/${FIXTURE_ID}    DELETE
    END
