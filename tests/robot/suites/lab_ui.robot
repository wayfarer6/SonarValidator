*** Settings ***
Documentation     실제 Lab UI를 사람이 읽을 수 있는 속도로 조작하고 시나리오별 영상을 남깁니다. 정책 푸시·격리·실제 장비 설정은 실행하지 않습니다.
Resource          ../labs/${LAB}.resource
Resource          ../resources/ui.resource
Suite Setup       Prepare Lab
Suite Teardown    Finish Lab
Test Setup        Open Scenario
Test Teardown     Finish Scenario
Test Tags         ${LAB}
Test Timeout      3 minutes

*** Test Cases ***
S01_Project_And_Deployment
    [Tags]    demo    smoke
    FOR    ${agent}    IN    @{EXPECTED_AGENTS}
        Wait Until Keyword Succeeds    120s    3s    Get Text    css=[data-testid="node-${agent}"]    *=    온라인
    END
    Show And Click    css=[data-testid="project-${PROJECT_ID}"] >> role=button[name="Add Agent"]
    Get Text    css=[data-testid="project-${PROJECT_ID}"]    *=    ${MANAGEMENT_SERVER}
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S01_deployment    fullPage=True
    Pause For Presenter

S02_Node_Connection_Modal
    [Tags]    demo    smoke
    IF    not $EXPECTED_AGENTS
        Skip    이 Lab의 EXPECTED_AGENTS를 먼저 설정하세요.
    END
    Open Node Connections    ${EXPECTED_AGENTS}[0]
    Show And Click    role=combobox[name="네트워크 역할"]
    Press Keys    role=combobox[name="네트워크 역할"]    Escape
    Select Options By    role=combobox[name="네트워크 역할"]    value    TRANSIT
    Pause For Presenter
    Select Options By    role=combobox[name="CSO 보안 등급"]    value    Sensitive
    Select Options By    role=combobox[name="허용 대상 추가"]    value    internet
    Get Text    css=[data-testid="peer-selector"]    *=    선택한 1개 대상만 허용
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S02_node_modal    fullPage=True
    Pause For Presenter
    Handle Future Dialogs    accept
    Show And Click    role=button[name="노드 설정 닫기"]
    Wait For Elements State    css=[data-testid="node-connection-modal"]    hidden

S03_Prober_Terminal
    [Timeout]    5 minutes
    [Tags]    demo    terminal
    IF    not $EXPECTED_AGENTS
        Skip    이 Lab의 EXPECTED_AGENTS를 먼저 설정하세요.
    END
    IF    $TERMINAL_AGENT_FILTER
        Should Contain    ${EXPECTED_AGENTS}    ${TERMINAL_AGENT_FILTER}
    END
    Observe Terminal Output
    FOR    ${agent}    IN    @{EXPECTED_AGENTS}
        IF    $TERMINAL_AGENT_FILTER and $agent != $TERMINAL_AGENT_FILTER
            CONTINUE
        END
        FOR    ${cycle}    IN RANGE    ${TERMINAL_CYCLES}
            Reset Terminal Output
            Scene    ${LAB_TITLE} · ${agent} 실제 Guest Shell 명령 응답
            Show And Click    css=[data-testid="node-${agent}"] >> role=button[name="터미널 열기"]
            Wait For Shell Prompt
            Keyboard Input    type    echo ROBOT_TERMINAL_OK_${agent}; id -un    delay=60ms
            Press Keys    css=.xterm-helper-textarea    Enter
            Wait For Function    () => window.__robotTerminal.replace(/\\r/g,'').includes('\\nROBOT_TERMINAL_OK_${agent}\\n')
            Take Screenshot    ${OUTPUT_DIR}/screenshots/S03_${agent}    fullPage=True
            Keyboard Input    type    (trap '' HUP TERM; exec -a sonar-terminal-job sleep 120) & echo ROBOT_JOB_STARTED
            Press Keys    css=.xterm-helper-textarea    Enter
            Wait For Function    () => window.__robotTerminal.replace(/\\r/g,'').includes('\\nROBOT_JOB_STARTED\\n')
            IF    $TERMINAL_STRESS.lower() != 'true'
                Keyboard Input    type    exit
                Press Keys    css=.xterm-helper-textarea    Enter
                Wait For Elements State    text=셸 종료됨    visible
            END
            Pause For Presenter
            Show And Click    css=button[aria-label="터미널 닫기"]
        END
    END

S04_Subnet_Prefix_And_Classification
    [Tags]    demo    regression
    Show And Click    css=[data-testid="project-${PROJECT_ID}"] >> role=link[name="Manage"]
    Wait For Elements State    text=서브넷 등급 설정    visible
    Scene    ${LAB_TITLE} · 수집 대역과 CSO 미분류 확인
    ${project}=    Call API    projects/${PROJECT_ID}
    ${actual}=    Evaluate    [s['cidr'] for s in $project['subnets']]
    FOR    ${cidr}    IN    @{EXPECTED_CIDRS}
        Should Contain    ${actual}    ${cidr}
    END
    FOR    ${subnet}    IN    @{project}[subnets]
        IF    not $subnet['manually_edited']
            Should Be Equal    ${subnet}[subnet_class]    ${None}
        END
    END
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S04_subnet_table    fullPage=True
    Pause For Presenter

S05_Save_And_Reload_Isolated_Fixture
    [Tags]    demo    regression    fixture
    Create Fixture
    Go To    ${UI_URL}/project
    Wait For Elements State    css=[data-testid="project-${FIXTURE_ID}"]    visible
    Scene    ${LAB_TITLE} · 임시 프로젝트에서 저장·재조회 검증
    Show And Click    css=[data-testid="project-${FIXTURE_ID}"] >> role=button[name="${FIXTURE_AGENT} 노드 설정"]
    Show And Click    role=button[name="연결 관리"]
    Wait For Elements State    role=combobox[name="네트워크 역할"]    visible
    Select Options By    role=combobox[name="네트워크 역할"]    value    TRANSIT
    Select Options By    role=combobox[name="허용 대상 추가"]    value    ROBOT-B
    Show And Click    role=button[name="변경 저장"]
    Wait For Elements State    role=status >> text=저장됨    visible
    ${saved}=    Call API    projects/${FIXTURE_ID}
    ${source}=    Evaluate    next(s for s in $saved['subnets'] if s['id']=='ROBOT-A')
    Should Be Equal    ${source}[network_role]    TRANSIT
    Should Contain    ${source}[allowed_peers]    ROBOT-B
    Show And Click    role=button[name="노드 설정 닫기"]
    Reload
    Show And Click    css=[data-testid="project-${FIXTURE_ID}"] >> role=button[name="${FIXTURE_AGENT} 노드 설정"]
    Show And Click    role=button[name="연결 관리"]
    Get Property    role=combobox[name="네트워크 역할"]    value    ==    TRANSIT
    Get Text    css=[data-testid="peer-selector"]    *=    Robot destination
    Pause For Presenter

S07_Bdd_Analysis_Scope_And_Invalid_Model
    [Tags]    demo    regression    bdd    fixture
    Create Fixture
    ${fixture}=    Call API    projects/${FIXTURE_ID}
    ${invalid}=    Evaluate    [{**s, 'cidr': '10.250.10.0/24/garbage'} if s['id']=='ROBOT-A' else s for s in $fixture['subnets']]
    &{invalid_body}=    Create Dictionary    subnets=${invalid}
    Call API    projects/${FIXTURE_ID}    PUT    ${invalid_body}
    ${report}=    Call API    projects/${FIXTURE_ID}/validation
    Should Be Equal    ${report}[analysis_scope]    POLICY_HEADER_SPACE
    Should Be Equal    ${report}[data_plane_analyzed]    ${False}
    Should Be Equal    ${report}[model_valid]    ${False}
    Should Be Equal    ${report}[compliant]    ${False}
    Go To    ${UI_URL}/policy?project_id=${FIXTURE_ID}
    Wait For Elements State    css=[data-testid="policy-analysis-scope"]    visible
    Get Text    css=[data-testid="policy-analysis-scope"]    *=    입력 오류로 검증을 완료할 수 없습니다
    Wait For Elements State    role=button[name="강제 푸시"]    disabled
    Scene    ${LAB_TITLE} · 잘못된 CIDR은 통과하지 않고 강제 전송도 차단
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S07_invalid_model    fullPage=True
    ${valid_subnets}=    Evaluate    $fixture['subnets']
    ${rules}=    Evaluate    [{'id':'ROBOT-ALLOW','src':'ROBOT-A','dst':'ROBOT-B','protocol':'tcp','port':443,'enabled':True}]
    &{valid_body}=    Create Dictionary    subnets=${valid_subnets}    rules=${rules}
    Call API    projects/${FIXTURE_ID}    PUT    ${valid_body}
    ${report}=    Call API    projects/${FIXTURE_ID}/validation
    Should Be Equal    ${report}[model_valid]    ${True}
    Should Be Equal    ${report}[compliant]    ${True}
    Should Be Equal    ${report}[data_plane_analyzed]    ${False}
    Go To    ${UI_URL}/project/editor/${FIXTURE_ID}
    Show And Click    role=button[name="검증 실행"]
    Wait For Elements State    css=[data-testid="policy-analysis-scope"]    visible
    Get Text    css=[data-testid="policy-analysis-scope"]    *=    실제 패킷 도달성은 별도 확인
    Scene    ${LAB_TITLE} · 정책 모델 통과와 실제 전달 경로 검증을 구분
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S07_policy_model_pass    fullPage=True
    Pause For Presenter

S08_Export_One_Json_File
    [Tags]    demo    regression    export    fixture
    Create Fixture
    Go To    ${UI_URL}/policy/export?project_id=${FIXTURE_ID}
    Wait For Elements State    css=[data-testid="export-policy-file"]    enabled
    Get Element Count    role=button[name="CSV 내보내기"]    ==    0
    Create Directory    ${OUTPUT_DIR}/downloads
    ${download}=    Promise To Wait For Download    saveAs=${OUTPUT_DIR}/downloads/policy.json
    Show And Click    css=[data-testid="export-policy-file"]
    ${file}=    Wait For    ${download}
    Should Be Equal    ${file}[state]    finished
    ${content}=    Get File    ${OUTPUT_DIR}/downloads/policy.json
    ${report}=    Evaluate    json.loads($content)    modules=json
    Should Be Equal    ${report}[project_id]    ${FIXTURE_ID}
    Should Be Equal    ${report}[analysis_scope]    POLICY_HEADER_SPACE
    Should Be Equal    ${report}[data_plane_analyzed]    ${False}
    Should Be Equal As Integers    ${report}[subnet_count]    2
    Scene    ${LAB_TITLE} · 파일 출력 하나로 검증 범위와 결과를 보존
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S08_one_file_export    fullPage=True
    Pause For Presenter

S09_Current_Uml_Diagrams
    [Tags]    demo    regression    docs
    Go To    ${DOCS_URL}/docs/architecture/device-extensions
    Wait For Function    () => document.querySelectorAll('svg[aria-roledescription]').length === 3
    ${valid}=    Evaluate JavaScript    ${None}    () => !/Syntax error|mermaid error/i.test(document.body.innerText)
    Should Be True    ${valid}
    ${diagrams}=    Evaluate JavaScript    ${None}    () => [...document.querySelectorAll('svg[aria-roledescription]')].map(svg => new XMLSerializer().serializeToString(svg))
    Create Directory    ${OUTPUT_DIR}/diagrams
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S09_current_uml    fullPage=True
    @{names}=    Create List    prober-strategies    backend-strategies    telemetry-sequence
    FOR    ${index}    IN RANGE    3
        ${svg}=    Set Variable    ${diagrams}[${index}]
        Create File    ${OUTPUT_DIR}/diagrams/${names}[${index}].svg    ${svg}    encoding=UTF-8
        ${page}=    Evaluate    base64.b64encode(('<!doctype html><html><head><meta charset="UTF-8"></head><body style="margin:0;background:white">'+$svg+'</body></html>').encode()).decode()    modules=base64
        Go To    data:text/html;base64,${page}
        Take Screenshot    ${OUTPUT_DIR}/diagrams/${names}[${index}]    selector=css=svg[aria-roledescription]
        Pause For Presenter
    END
    Scene    ${LAB_TITLE} · 현재 코드의 전략·인터페이스·옵저버 UML
    Pause For Presenter

S10_Concurrency_Ownership
    [Tags]    demo    regression    docs    concurrency
    Go To    ${DOCS_URL}/docs/architecture/prober-runtime
    Wait For Function    () => document.querySelectorAll('svg[aria-roledescription]').length === 1
    ${valid}=    Evaluate JavaScript    ${None}    () => !/Syntax error|mermaid error/i.test(document.body.innerText)
    Should Be True    ${valid}
    Get Text    css=main    contains    request_stop
    Get Text    css=main    contains    AgentSessionRegistry.send
    Create Directory    ${OUTPUT_DIR}/diagrams
    ${svg}=    Evaluate JavaScript    ${None}    () => new XMLSerializer().serializeToString(document.querySelector('svg[aria-roledescription]'))
    Create File    ${OUTPUT_DIR}/diagrams/concurrency-ownership.svg    ${svg}    encoding=UTF-8
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S10_concurrency_ownership    fullPage=True
    ${page}=    Evaluate    base64.b64encode(('<!doctype html><html><head><meta charset="UTF-8"></head><body style="margin:0;background:white">'+$svg+'</body></html>').encode()).decode()    modules=base64
    Go To    data:text/html;base64,${page}
    Take Screenshot    ${OUTPUT_DIR}/diagrams/concurrency-ownership    selector=css=svg[aria-roledescription]
    Scene    ${LAB_TITLE} · 스레드별 소유권·중지 요청·join·DB 종료 순서
    Pause For Presenter

S11_Per_Node_Collection_Interval
    [Tags]    demo    regression    interval
    IF    $LAB != 'lab1'
        Skip    설정 변경과 원상복원 대상은 검증된 Lab1 두 노드입니다.
    END
    &{originals}=    Create Dictionary
    FOR    ${agent}    IN    @{EXPECTED_AGENTS}
        ${settings}=    Call API    agents/${agent}/monitor-settings?project_id=${PROJECT_ID}
        Set To Dictionary    ${originals}    ${agent}=${settings}[monitor_interval_seconds]
    END
    TRY
        &{targets}=    Create Dictionary    Lab1-R1=2    Lab1-R2=4
        FOR    ${agent}    ${seconds}    IN    &{targets}
            Scene    ${agent} · 노드별 수집 주기 ${seconds}초 설정
            Open Node Settings    ${agent}
            Wait For Elements State    role=spinbutton[name="수집 주기 값"]    visible
            Select Options By    role=combobox[name="수집 주기 단위"]    value    seconds
            Fill Text    role=spinbutton[name="수집 주기 값"]    ${seconds}
            Show And Click    role=button[name="수집 주기 저장"]
            Wait Until Keyword Succeeds    30s    1s    Get Text    css=[data-testid="monitor-apply-status"]    *=    Prober 적용 확인됨
            Wait Until Keyword Succeeds    30s    1s    Check Monitor Applied    ${agent}    ${seconds}
            Take Screenshot    ${OUTPUT_DIR}/screenshots/S11_${agent}    fullPage=True
            Pause For Presenter
            Show And Click    role=button[name="노드 설정 닫기"]
        END
        FOR    ${agent}    ${seconds}    IN    &{targets}
            Wait Until Keyword Succeeds    20s    1s    Check Collected Interval    ${agent}    ${seconds}
        END
    FINALLY
        FOR    ${agent}    ${seconds}    IN    &{originals}
            ${current}=    Call API    agents/${agent}/monitor-settings?project_id=${PROJECT_ID}
            ${body}=    Evaluate    {'monitor_interval_seconds': int($seconds), 'revision': int($current['revision'])}
            Call API    agents/${agent}/monitor-settings?project_id=${PROJECT_ID}    PUT    ${body}
        END
    END

S12_Offline_Settings_And_Validation
    [Tags]    demo    regression    interval    fixture
    Create Fixture
    Go To    ${UI_URL}/project
    Wait For Elements State    css=[data-testid="project-${FIXTURE_ID}"]    visible
    Scene    임시 오프라인 노드 · 주기 저장·유효성·재조회
    Open Node Settings    ${FIXTURE_AGENT}    ${FIXTURE_ID}
    Wait For Elements State    role=spinbutton[name="수집 주기 값"]    visible
    Fill Text    role=spinbutton[name="수집 주기 값"]    0
    Get Property    role=button[name="수집 주기 저장"]    disabled    ==    ${True}
    Fill Text    role=spinbutton[name="수집 주기 값"]    1.5
    Get Property    role=button[name="수집 주기 저장"]    disabled    ==    ${True}
    Select Options By    role=combobox[name="수집 주기 단위"]    value    minutes
    Fill Text    role=spinbutton[name="수집 주기 값"]    1
    Show And Click    role=button[name="수집 주기 저장"]
    Wait Until Keyword Succeeds    10s    1s    Get Text    css=[data-testid="monitor-desired"]    ==    1분
    Get Text    css=[data-testid="monitor-apply-status"]    *=    Prober 수집 연결 대기
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S12_offline_saved    fullPage=True
    Show And Click    role=button[name="노드 설정 닫기"]
    Reload
    Open Node Settings    ${FIXTURE_AGENT}    ${FIXTURE_ID}
    Get Property    role=spinbutton[name="수집 주기 값"]    value    ==    1
    Get Property    role=combobox[name="수집 주기 단위"]    value    ==    minutes
    ${settings}=    Call API    agents/${FIXTURE_AGENT}/monitor-settings?project_id=${FIXTURE_ID}
    &{request}=    Create Dictionary    url=${API_URL}/api/v1/agents/${FIXTURE_AGENT}/monitor-settings?project_id=${FIXTURE_ID}    revision=${settings}[revision]
    ${invalid}=    Evaluate JavaScript    ${None}
    ...    async r => (await fetch(r.url,{method:'PUT',credentials:'include',headers:{'Content-Type':'application/json'},body:JSON.stringify({monitor_interval_seconds:1.5,revision:r.revision})})).status
    ...    arg=${request}
    Should Be Equal As Integers    ${invalid}    400
    ${unauthorized}=    Evaluate JavaScript    ${None}    async url => (await fetch(url,{credentials:'omit'})).status    arg=${request}[url]
    Should Be Equal As Integers    ${unauthorized}    401
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S12_reloaded    fullPage=True
    Pause For Presenter
S13_Monitor_Settings_Protocol
    [Tags]    demo    regression    docs    interval
    Go To    ${DOCS_URL}/docs/features/node-monitor-settings
    Wait For Function    () => document.querySelectorAll('svg[aria-roledescription]').length === 1
    ${valid}=    Evaluate JavaScript    ${None}    () => !/Syntax error|mermaid error/i.test(document.body.innerText)
    Should Be True    ${valid}
    Create Directory    ${OUTPUT_DIR}/diagrams
    ${svg}=    Evaluate JavaScript    ${None}    () => new XMLSerializer().serializeToString(document.querySelector('svg[aria-roledescription]'))
    Create File    ${OUTPUT_DIR}/diagrams/monitor-settings-sequence.svg    ${svg}    encoding=UTF-8
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S13_monitor_settings_protocol    fullPage=True
    ${page}=    Evaluate    base64.b64encode(('<!doctype html><html><head><meta charset="UTF-8"></head><body style="margin:0;background:white">'+$svg+'</body></html>').encode()).decode()    modules=base64
    Go To    data:text/html;base64,${page}
    Take Screenshot    ${OUTPUT_DIR}/diagrams/monitor-settings-sequence    selector=css=svg[aria-roledescription]
    Pause For Presenter

S17_Monitor_Save_Locks_Draft
    [Tags]    regression    interval
    Open Node Settings    ${EXPECTED_AGENTS}[0]
    ${original}=    Call API    agents/${EXPECTED_AGENTS}[0]/monitor-settings?project_id=${PROJECT_ID}
    ${mock}=    Evaluate    {**$original,'monitor_interval_seconds':31}
    Evaluate JavaScript    ${None}
    ...    value => {const native=window.fetch;window.__monitorNativeFetch=native;window.fetch=(...args)=>String(args[0]).includes('/monitor-settings')&&args[1]?.method==='PUT'?new Promise(resolve=>{window.__finishMonitorSave=()=>resolve(new Response(JSON.stringify(value),{status:200,headers:{'Content-Type':'application/json'}}));}):native(...args);}
    ...    arg=${mock}
    TRY
        Select Options By    role=combobox[name="수집 주기 단위"]    value    seconds
        Fill Text    role=spinbutton[name="수집 주기 값"]    31
        Click    role=button[name="수집 주기 저장"]
        Wait For Elements State    role=spinbutton[name="수집 주기 값"]    disabled
        Wait For Elements State    role=combobox[name="수집 주기 단위"]    disabled
        Evaluate JavaScript    ${None}    () => window.__finishMonitorSave()
        Wait For Elements State    role=spinbutton[name="수집 주기 값"]    enabled
        ${unchanged}=    Call API    agents/${EXPECTED_AGENTS}[0]/monitor-settings?project_id=${PROJECT_ID}
        Should Be Equal As Integers    ${unchanged}[revision]    ${original}[revision]
        Should Be Equal As Integers    ${unchanged}[monitor_interval_seconds]    ${original}[monitor_interval_seconds]
        Scene    저장 중 입력 잠금 · 응답 뒤 초안 덮어쓰기 방지
        Take Screenshot    ${OUTPUT_DIR}/screenshots/S17_save_locked    fullPage=True
    FINALLY
        Evaluate JavaScript    ${None}    () => {window.fetch=window.__monitorNativeFetch;window.__finishMonitorSave?.();}
    END

S18_Core_Feature_Uml
    [Tags]    demo    regression    docs
    Go To    ${DOCS_URL}/docs/architecture/core-uml
    Wait For Function    () => document.querySelectorAll('svg[aria-roledescription]').length === 4
    Get Text    css=main    contains    일부 클래스와 호출만 발췌한 UML
    ${valid}=    Evaluate JavaScript    ${None}    () => !/Syntax error|mermaid error/i.test(document.body.innerText)
    Should Be True    ${valid}
    ${diagrams}=    Evaluate JavaScript    ${None}    () => [...document.querySelectorAll('svg[aria-roledescription]')].map(svg => new XMLSerializer().serializeToString(svg))
    Create Directory    ${OUTPUT_DIR}/diagrams
    @{names}=    Create List    isolation-classes    isolation-sequence    prober-event-sequence    single-export-sequence
    FOR    ${index}    IN RANGE    4
        ${svg}=    Set Variable    ${diagrams}[${index}]
        Create File    ${OUTPUT_DIR}/diagrams/${names}[${index}].svg    ${svg}    encoding=UTF-8
        ${page}=    Evaluate    base64.b64encode(('<!doctype html><html><head><meta charset="UTF-8"></head><body style="margin:0;background:white">'+$svg+'</body></html>').encode()).decode()    modules=base64
        Go To    data:text/html;base64,${page}
        Take Screenshot    ${OUTPUT_DIR}/diagrams/${names}[${index}]    selector=css=svg[aria-roledescription]
        Pause For Presenter
    END
    Scene    격리·수집 이벤트·파일 출력 UML · 현재 설계의 일부만 발췌
