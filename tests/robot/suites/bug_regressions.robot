*** Settings ***
Documentation     데모 제거·지연 저장·짧은 오류와 상세 모달 회귀 검사. 임시 프로젝트만 수정하고 실제 장비 정책은 적용하지 않습니다.
Resource          ../labs/${LAB}.resource
Resource          ../resources/ui.resource
Suite Setup       Prepare Lab
Suite Teardown    Finish Lab
Test Setup        Open Scenario
Test Teardown     Finish Scenario
Test Timeout      3 minutes
Test Tags         regression

*** Test Cases ***
B01_Real_Capabilities_And_Removed_Demo_Routes
    ${capabilities}=    Call API    cli/targets
    Dictionary Should Not Contain Key    ${capabilities}    example
    Dictionary Should Contain Key    ${capabilities}    targets
    Go To    ${UI_URL}/calendar
    Wait For Elements State    text=찾을 수 없는 페이지 입니다.    visible
    Scene    데모 경로는 제거하고 실제 제품 기능만 유지
    Take Screenshot    ${OUTPUT_DIR}/screenshots/B01_removed_demo    fullPage=True

B02_Connection_Draft_Is_Locked_During_Save
    Create Fixture
    Go To    ${UI_URL}/project
    Wait For Elements State    css=[data-testid="project-${FIXTURE_ID}"]    visible
    Open Node Settings    ${FIXTURE_AGENT}    ${FIXTURE_ID}
    Show And Click    role=button[name="연결 관리"]
    Fill Text    role=textbox[name="서브넷 이름"]    Regression saved subnet
    Evaluate JavaScript    ${None}
    ...    (id) => {const original=window.fetch.bind(window);window.__releaseSave=null;window.fetch=async(input,options)=>{if(String(input).includes('/projects/'+id)&&options?.method==='PUT')await new Promise(resolve=>window.__releaseSave=resolve);return original(input,options);};}
    ...    arg=${FIXTURE_ID}
    Show And Click    role=button[name="변경 저장"]
    Wait For Elements State    role=textbox[name="서브넷 이름"]    disabled
    Show And Click    role=button[name="노드 설정 닫기"]
    Wait For Elements State    css=[data-testid="node-settings-modal"]    visible
    Scene    저장 응답을 기다리는 동안 연결 입력과 닫기 보호
    Take Screenshot    ${OUTPUT_DIR}/screenshots/B02_pending_save    selector=css=[data-testid="node-settings-modal"]
    Evaluate JavaScript    ${None}    () => {window.__releaseSave();}
    Wait For Elements State    css=[data-testid="node-connection-modal"] >> text=저장됨    visible
    Get Property    role=textbox[name="서브넷 이름"]    value    ==    Regression saved subnet
    Show And Click    role=button[name="노드 설정 닫기"]
    ${project}=    Call API    projects/${FIXTURE_ID}
    Should Be Equal    ${project}[subnets][0][name]    Regression saved subnet

B03_Deployment_Cancel_Returns_To_Owning_Project
    Go To    ${UI_URL}/project/create?project_id=${PROJECT_ID}
    Wait For Elements State    css=[data-testid="agent-deploy-card"]    visible
    Get Text    css=[data-testid="deployment-server"]    *=    3000
    Show And Click    css=[data-testid="agent-deploy-card"] >> role=button[name="닫기 ✕"]
    Wait For Elements State    role=button[name="관리 서버 설정"]    visible
    Get Url    *=    /project/editor/${PROJECT_ID}
    Scene    다운로드 화면을 직접 열어도 소속 프로젝트로 복귀
    Take Screenshot    ${OUTPUT_DIR}/screenshots/B03_deployment_return    fullPage=True

B04_Compact_Errors_And_Log_Detail_Modal
    Go To    ${UI_URL}/log
    Wait For Elements State    css=[data-testid="device-log-refresh"]    visible
    ${mock}=    Catenate    SEPARATOR=${SPACE}
    ...    () => {
    ...      const nl=String.fromCharCode(10); window.__errorOriginalFetch=window.fetch.bind(window);
    ...      window.fetch=async(input,options)=>{
    ...        const url=new URL(typeof input==='string'?input:input.url,location.href);
    ...        if(url.pathname==='/api/v1/logs'&&(options?.method??'GET')==='GET'){
    ...          return new Response(JSON.stringify({message:'오류 원문 '+('상세 응답 '.repeat(60)),trace:'<img src=x onerror="window.__errorScriptExecuted=true">'+nl+(('stack line'+nl).repeat(30))+'REQUEST_DETAIL_TAIL',password:'demo-secret-never-show'}),{status:500,headers:{'Content-Type':'application/json'}});
    ...        }
    ...        return window.__errorOriginalFetch(input,options);
    ...      };
    ...    }
    Evaluate JavaScript    ${None}    ${mock}
    TRY
        Show And Click    css=[data-testid="device-log-refresh"]
        Wait For Elements State    css=[data-testid="device-log-error"]    visible
        Get Text    css=[data-testid="device-log-error"]    *=    서버에서 요청을 처리하지 못했습니다.
        ${body}=    Get Text    css=body
        Should Not Contain    ${body}    REQUEST_DETAIL_TAIL
        Show And Click    css=[role="tab"][aria-controls="requests-log-panel"]
        Get Element Count    css=[data-testid="request-error-row"]    ==    1
        Scene    오류 목록은 한 줄 요약과 상세 보기 버튼
        Take Screenshot    ${OUTPUT_DIR}/screenshots/B04_request_summary    fullPage=True
        Show And Click    css=[data-testid="request-error-row"] >> text=상세 보기
        Wait For Elements State    role=dialog    visible
        ${details}=    Get Text    css=[data-testid="log-detail-content"]
        Should Contain    ${details}    REQUEST_DETAIL_TAIL
        Should Not Contain    ${details}    demo-secret-never-show
        Get Element Count    css=[data-testid="log-detail-content"] img    ==    0
        ${executed}=    Evaluate JavaScript    ${None}    () => window.__errorScriptExecuted===true
        Should Not Be True    ${executed}
        Scene    세부 원문은 모달에서 확인하고 비밀 값은 가림
        Take Screenshot    ${OUTPUT_DIR}/screenshots/B04_request_detail    selector=role=dialog
        Show And Click    role=dialog >> role=button[name="닫기"]
        Show And Click    role=button[name="기록 비우기"]
        Wait For Elements State    text=기록된 요청 오류가 없습니다.    visible
        ${device_mock}=    Catenate    SEPARATOR=${SPACE}
        ...    () => {
        ...      const nl=String.fromCharCode(10);
        ...      window.fetch=async(input,options)=>{
        ...        const url=new URL(typeof input==='string'?input:input.url,location.href);
        ...        if(url.pathname==='/api/v1/logs'&&(options?.method??'GET')==='GET'){
        ...          const log={id:987654321,agent_id:'UI-Fixture',project_id:null,product:'Fixture',logged_at:new Date().toISOString(),collected_at:null,severity:'error',severity_num:3,facility:null,message_id:'DEMO-ERROR',message:'장비 연결 요청을 처리하지 못했습니다. '+('긴 설명 '.repeat(40)),raw:'장비 오류 원문'+nl+(('technical detail'+nl).repeat(40))+'DEVICE_DETAIL_TAIL',source:'ui-test',repeat_count:1,highlighted:false,note:null};
        ...          return new Response(JSON.stringify({total:1,returned:1,truncated:false,limit:200,filters:{},logs:[log]}),{headers:{'Content-Type':'application/json'}});
        ...        }
        ...        return window.__errorOriginalFetch(input,options);
        ...      };
        ...    }
        Evaluate JavaScript    ${None}    ${device_mock}
        Show And Click    css=[role="tab"][aria-controls="device-log-panel"]
        Show And Click    css=[data-testid="device-log-refresh"]
        Wait For Elements State    css=[data-testid="device-log-row"]    visible
        ${row}=    Get Text    css=[data-testid="device-log-row"]
        Should Not Contain    ${row}    DEVICE_DETAIL_TAIL
        Scene    장비 로그도 목록에는 짧게 표시
        Take Screenshot    ${OUTPUT_DIR}/screenshots/B04_device_summary    fullPage=True
        Show And Click    css=[data-testid="device-log-detail-987654321"]
        Get Text    css=[data-testid="log-detail-content"]    *=    DEVICE_DETAIL_TAIL
        Scene    장비 로그 상세 원문과 장비 정보를 모달로 확인
        Take Screenshot    ${OUTPUT_DIR}/screenshots/B04_device_detail    selector=role=dialog
        Show And Click    role=dialog >> role=button[name="닫기"]
    FINALLY
        Evaluate JavaScript    ${None}    () => {if(window.__errorOriginalFetch)window.fetch=window.__errorOriginalFetch;}
    END

B05_Isolation_Modal_Fits_Viewport_And_Protects_Pending_Request
    Go To    ${UI_URL}/agent
    ${row}=    Set Variable    css=tr:has-text("${EXPECTED_AGENTS}[0]")
    Show And Click    ${row} >> role=button[name="격리 설정"]
    Set Viewport Size    800    400
    ${dialog}=    Set Variable    role=dialog[name="격리 설정"]
    Wait For Elements State    ${dialog}    visible
    ${fits}=    Evaluate JavaScript    ${dialog}    (element) => {const r=element.getBoundingClientRect();return r.top>=0 && r.bottom<=innerHeight && r.left>=0 && r.right<=innerWidth;}
    Should Be True    ${fits}
    Select Options By    role=combobox[name="격리 범위"]    value    ${EMPTY}
    Evaluate JavaScript    ${None}
    ...    () => {window.__modalFetch=window.fetch.bind(window);window.fetch=async(input,options)=>{if(String(input).includes('/quarantine/')&&options?.method==='POST'){await new Promise(resolve=>window.__finishModalRequest=resolve);return new Response(JSON.stringify({message:'UI test request cancelled'}),{status:409,headers:{'Content-Type':'application/json'}});}return window.__modalFetch(input,options);};}
    TRY
        Show And Click    ${dialog} >> role=button[name="격리 요청"]
        Wait For Elements State    ${dialog} >> role=button[name="모달 닫기"]    disabled
        Press Keys    ${dialog}    Escape
        Wait For Elements State    ${dialog}    visible
        Evaluate JavaScript    ${None}    () => document.querySelector('.modal > div')?.click()
        Wait For Elements State    ${dialog}    visible
        Take Screenshot    ${OUTPUT_DIR}/screenshots/B05_bounded_busy_dialog    selector=${dialog}
        Evaluate JavaScript    ${None}    () => window.__finishModalRequest()
        Wait For Elements State    ${dialog} >> role=button[name="모달 닫기"]    enabled
        Get Text    ${dialog} >> role=alert    *=    UI test request cancelled
        Press Keys    ${dialog}    Escape
        Wait For Elements State    ${dialog}    detached
    FINALLY
        Evaluate JavaScript    ${None}    () => {window.__finishModalRequest?.();if(window.__modalFetch)window.fetch=window.__modalFetch;}
        Set Viewport Size    1600    1000
    END

B06_Late_Subnet_Response_Cannot_Replace_Another_Node
    Skip If    '${LAB}' != 'lab1'    Two Cisco agents in one project exercise cross-node response ordering.
    Go To    ${UI_URL}/agent
    Evaluate JavaScript    ${None}
    ...    () => {window.__subnetFetch=window.fetch.bind(window);let first=true;window.fetch=async(input,options)=>{if(first&&String(input).includes('/network/topology/')){first=false;const response=await window.__subnetFetch(input,options);await new Promise(resolve=>window.__releaseFirstTopology=resolve);return response;}return window.__subnetFetch(input,options);};}
    TRY
        Show And Click    css=tr:has-text("Lab1-R1") >> role=button[name="격리 설정"]
        Wait For Function    () => typeof window.__releaseFirstTopology==='function'
        Show And Click    role=dialog[name="격리 설정"] >> role=button[name="모달 닫기"]
        Show And Click    css=tr:has-text("Lab1-R2") >> role=button[name="격리 설정"]
        Wait For Elements State    role=combobox[name="격리 범위"]    enabled
        ${options}=    Get Text    role=combobox[name="격리 범위"]
        Should Contain    ${options}    Lab1-R2
        Evaluate JavaScript    ${None}    () => window.__releaseFirstTopology()
        Sleep    300ms
        ${options}=    Get Text    role=combobox[name="격리 범위"]
        Should Contain    ${options}    Lab1-R2
        Should Not Contain    ${options}    Lab1-R1
        Take Screenshot    ${OUTPUT_DIR}/screenshots/B06_latest_node_scope    selector=role=dialog[name="격리 설정"]
        Show And Click    role=dialog[name="격리 설정"] >> role=button[name="모달 닫기"]
    FINALLY
        Evaluate JavaScript    ${None}    () => {window.__releaseFirstTopology?.();if(window.__subnetFetch)window.fetch=window.__subnetFetch;}
    END
