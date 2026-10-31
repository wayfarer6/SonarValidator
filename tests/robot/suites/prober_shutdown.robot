*** Settings ***
Documentation     Lab1 전용: 실제 Prober SIGTERM과 EEM 복구를 검증합니다. 정책 적용은 하지 않습니다.
Resource          ../labs/${LAB}.resource
Resource          ../resources/ui.resource
Library           Process
Suite Setup       Prepare Lab
Suite Teardown    Finish Lab
Test Setup        Open Scenario
Test Teardown     Finish Scenario
Test Timeout      4 minutes

*** Test Cases ***
S06_Active_Shell_SIGTERM_And_EEM_Recovery
    IF    $LAB != 'lab1'
        Skip    SSH 대상이 정의된 Lab1에서만 실행합니다.
    END
    &{targets}=    Create Dictionary    Lab1-R1=192.168.148.181    Lab1-R2=192.168.148.182
    FOR    ${agent}    ${ip}    IN    &{targets}
        Scene    ${agent} · 터미널 실행 중 SIGTERM과 EEM 복구
        Show And Click    css=[data-testid="node-${agent}"] >> role=button[name="터미널 열기"]
        Wait For Elements State    css=.xterm-helper-textarea    attached
        Focus    css=.xterm-helper-textarea
        Keyboard Input    type    (trap '' HUP TERM; exec -a sonar-terminal-job sleep 120) & echo SHUTDOWN_JOB_READY    delay=30ms
        Press Keys    css=.xterm-helper-textarea    Enter
        Sleep    1s
        ${check}=    Run Process    ssh    -o    BatchMode\=yes    -o    ConnectTimeout\=5    -p    2222    guestshell@${ip}    pgrep -f '^sonar-terminal-job 120$'    timeout=10s
        Should Be Equal As Integers    ${check.rc}    0
        ${stop}=    Set Variable    pid=$(pgrep -f '^/home/guestshell/sonar/Installer/sonar_validator_prober$'); test -n "$pid" || exit 1; kill -TERM "$pid"; for n in $(seq 1 100); do if ! kill -0 "$pid" 2>/dev/null; then break; fi; sleep 0.1; done; ! kill -0 "$pid" 2>/dev/null && ! pgrep -f '^sonar-terminal-job 120$'
        ${stopped}=    Run Process    ssh    -o    BatchMode\=yes    -o    ConnectTimeout\=5    -p    2222    guestshell@${ip}    ${stop}    timeout=15s
        Should Be Equal As Integers    ${stopped.rc}    0
        Show And Click    css=button[aria-label="터미널 닫기"]
        ${restore}=    Set Variable    for n in $(seq 1 90); do if pgrep -f '^/home/guestshell/sonar/Installer/sonar_validator_prober$'; then exit 0; fi; sleep 1; done; exit 1
        ${restored}=    Run Process    ssh    -o    BatchMode\=yes    -o    ConnectTimeout\=5    -p    2222    guestshell@${ip}    ${restore}    timeout=95s
        Should Be Equal As Integers    ${restored.rc}    0
        Reload
        Wait Until Keyword Succeeds    30s    2s    Get Text    css=[data-testid="node-${agent}"]    *=    온라인
        Scene    ${agent} · 셸·자식 작업 정리 후 EEM 자동 복원 확인
        Take Screenshot    ${OUTPUT_DIR}/screenshots/S06_${agent}    fullPage=True
    END
