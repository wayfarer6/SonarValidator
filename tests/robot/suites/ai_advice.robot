*** Settings ***
Documentation     모의 AI HTTP 공급자와 실제 Lab1 Prober 경로 조회. 장비 설정은 변경하지 않습니다.
Resource          ../labs/${LAB}.resource
Resource          ../resources/ui.resource
Suite Setup       Prepare AI Lab
Suite Teardown    Finish AI Lab
Test Setup        Open Scenario
Test Teardown     Finish Scenario
Test Timeout      3 minutes
Test Tags         ${LAB}    ai

*** Variables ***
${MOCK_PROVIDER_ID}    ${EMPTY}

*** Test Cases ***
S14_AI_Readonly_Prober_Execution
    Generate Mock Advice    MOCK_DIAGNOSTIC
    ${command}=    Set Variable    css=[data-testid="ai-command-0"] >> nth=0
    Get Text    ${command}    *=    show ip route
    Show And Click    ${command} >> role=button[name="Prober 실행 검토"]
    Wait For Elements State    role=button[name="확인 후 Prober 실행"]    disabled
    Check Checkbox    css=[data-testid="ai-execution-review"] input[type="checkbox"]
    Scene    모의 AI 제안 · 실제 Lab1-R1에서 경로 조회 실행
    Show And Click    role=button[name="확인 후 Prober 실행"]
    Wait Until Keyword Succeeds    40s    1s    Get Text    ${command} >> css=[data-testid="ai-execution-result"]    *=    실행·결과 확인됨
    Get Text    ${command} >> css=[data-testid="ai-execution-result"]    *=    EEM snapshot
    ${advice}=    Latest Mock Advice
    ${history}=    Call API    policy/advice/${PROJECT_ID}/${advice}[advice_id]/executions
    Should Be Equal    ${history}[executions][0][status]    SUCCEEDED
    Should Be Equal    ${history}[executions][0][result][verified]    ${True}
    ${json}=    Evaluate    json.dumps($history,ensure_ascii=False,indent=2)    modules=json
    Create File    ${OUTPUT_DIR}/readonly-execution.json    ${json}    encoding=UTF-8
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S14_ai_readonly    fullPage=True
    Pause For Presenter

S15_AI_Configuration_Preview_Only
    Generate Mock Advice    MOCK_CONFIG
    ${command}=    Set Variable    css=[data-testid="ai-command-0"] >> nth=0
    Get Text    ${command}    *=    description AI_PREVIEW_ONLY
    Get Text    ${command}    *=    부팅 설정으로 저장하는 작업은 별도
    Scene    설정 변경 제안 · 명령과 영향만 검토하고 적용하지 않음
    Show And Click    ${command} >> role=button[name="Prober 실행 검토"]
    Wait For Elements State    role=button[name="확인 후 Prober 실행"]    disabled
    ${advice}=    Latest Mock Advice
    ${history}=    Call API    policy/advice/${PROJECT_ID}/${advice}[advice_id]/executions
    Should Be Empty    ${history}[executions]
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S15_ai_preview    fullPage=True
    Show And Click    role=button[name="취소"]

S16_AI_Unsupported_Command_Rejected
    Generate Mock Advice    MOCK_MANUAL
    ${command}=    Set Variable    css=[data-testid="ai-command-0"] >> nth=0
    Get Text    ${command}    *=    reload
    Get Text    ${command}    *=    Prober 실행 불가
    Wait For Elements State    ${command} >> role=button[name="Prober 실행 검토"]    disabled
    ${advice}=    Latest Mock Advice
    ${url}=    Set Variable    ${API_URL}/api/v1/policy/advice/${PROJECT_ID}/${advice}[advice_id]/executions
    ${status}=    Evaluate JavaScript    ${None}
    ...    async url => (await fetch(url,{method:'POST',credentials:'include',headers:{'Content-Type':'application/json'},body:JSON.stringify({proposal_index:0,request_id:crypto.randomUUID(),confirmed:true})})).status
    ...    arg=${url}
    Should Be Equal As Integers    ${status}    409
    ${history}=    Call API    policy/advice/${PROJECT_ID}/${advice}[advice_id]/executions
    Should Be Empty    ${history}[executions]
    Scene    미지원 AI 명령 · 화면과 서버에서 모두 실행 차단
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S16_ai_blocked    fullPage=True

S18_AI_Execution_Sequence_Diagram
    Go To    ${DOCS_URL}/docs/features/ai-advice
    Wait For Function    () => document.querySelectorAll('svg[aria-roledescription]').length === 1
    ${valid}=    Evaluate JavaScript    ${None}    () => !/Syntax error|mermaid error/i.test(document.body.innerText)
    Should Be True    ${valid}
    Get Text    css=main    *=    UNCONFIRMED
    Create Directory    ${OUTPUT_DIR}/diagrams
    ${svg}=    Evaluate JavaScript    ${None}    () => new XMLSerializer().serializeToString(document.querySelector('svg[aria-roledescription]'))
    Create File    ${OUTPUT_DIR}/diagrams/ai-execution-sequence.svg    ${svg}    encoding=UTF-8
    Take Screenshot    ${OUTPUT_DIR}/screenshots/S18_ai_sequence    fullPage=True
    ${page}=    Evaluate    base64.b64encode(('<!doctype html><html><head><meta charset="UTF-8"></head><body style="margin:0;background:white">'+$svg+'</body></html>').encode()).decode()    modules=base64
    Go To    data:text/html;base64,${page}
    Take Screenshot    ${OUTPUT_DIR}/diagrams/ai-execution-sequence    selector=css=svg[aria-roledescription]
    Pause For Presenter

*** Keywords ***
Prepare AI Lab
    IF    $LAB != 'lab1'
        Skip    실제 프로그램 검증은 Lab1만 수행합니다.
    END
    Prepare Lab
    New Context    storageState=${AUTH_STATE}
    New Page    ${UI_URL}/project
    ${body}=    Evaluate    {'name':'ROBOT-AI-'+__import__('uuid').uuid4().hex[:8],'base_url':'http://127.0.0.1:3049/v1','model':'mock-advice-v1','auth_style':'none','enabled':True,'is_default':False,'timeout_seconds':10}
    ${provider}=    Call API    ai/providers    POST    ${body}
    ${id}=    Convert To String    ${provider}[id]
    Set Suite Variable    ${MOCK_PROVIDER_ID}    ${id}
    Close Context

Finish AI Lab
    IF    $MOCK_PROVIDER_ID
        New Context    storageState=${AUTH_STATE}
        New Page    ${UI_URL}/project
        Call API    ai/providers/${MOCK_PROVIDER_ID}    DELETE
        Close Context
    END
    Finish Lab

Generate Mock Advice
    [Arguments]    ${prompt}
    Open Node Settings    Lab1-R1
    Show And Click    role=button[name="AI 진단·명령"]
    Wait For Elements State    role=combobox[name="AI 공급자"]    visible
    Select Options By    role=combobox[name="AI 공급자"]    value    ${MOCK_PROVIDER_ID}
    Fill Text    role=textbox[name="AI 추가 질문"]    ${prompt}
    Show And Click    role=button[name="AI 진단·명령 요청"]
    Wait For Elements State    role=button[name="AI 진단·명령 요청"]    enabled
    Wait For Elements State    css=[data-testid="ai-command-0"] >> nth=0    visible
    Get Text    css=[data-testid="ai-advice-panel"]    *=    [모의 공급자]

Latest Mock Advice
    ${body}=    Call API    policy/advice/${PROJECT_ID}?agent_id=Lab1-R1&limit=1
    Should Be Equal As Integers    ${body}[total]    1
    RETURN    ${body}[advices][0]
