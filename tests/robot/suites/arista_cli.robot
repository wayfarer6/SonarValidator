*** Settings ***
Documentation    Lab2 Arista 직접 CLI와 오프라인 Prober 수집 검증. 상시 Agent를 등록하지 않습니다.
Library    Process
Library    OperatingSystem
Library    Collections
Suite Setup    Check Test Directory
Test Timeout    3 minutes

*** Variables ***
${ARISTA_SSH}       %{SONAR_ARISTA_SSH=admin@192.168.150.3}
${TEST_DIR}         %{SONAR_ARISTA_TEST_DIR=/mnt/flash/sonar-cli-check}
${EXPECTED_EOS}     %{SONAR_ARISTA_VERSION=4.30.2F}
${EXPORT_RUNS}      %{SONAR_ARISTA_EXPORT_RUNS=20}

*** Test Cases ***
A01_Cli_And_Failure_Status
    ${version}=    Run FastCli    show version
    Should Be Equal As Integers    ${version.rc}    0
    Should Contain    ${version.stdout}    ${EXPECTED_EOS}
    ${bad}=    Run FastCli    show sonar-invalid-command
    Should Not Be Equal As Integers    ${bad.rc}    0
    Should Contain    ${bad.stdout}    Invalid input

A02_Repeated_Native_Prober_Collection
    ${command}=    Catenate    SEPARATOR=${SPACE}
    ...    SONAR_CONFIG_PATH=${TEST_DIR}/default.conf
    ...    SONAR_TEMPLATE_PATH=${TEST_DIR}/template.sqlite
    ...    SONAR_DATA_DIR=${TEST_DIR}/data
    ...    ${TEST_DIR}/prober --export ${TEST_DIR}/snapshot.json
    FOR    ${run}    IN RANGE    ${EXPORT_RUNS}
        ${collected}=    Run EOS    ${command}
        Should Be Equal As Integers    ${collected.rc}    0
        ${read}=    Run EOS    cat ${TEST_DIR}/snapshot.json
        Should Be Equal As Integers    ${read.rc}    0
        ${document}=    Evaluate    json.loads($read.stdout)    modules=json
        Should Be Equal    ${document}[schema]    sonar.offline.snapshot
        ${body}=    Set Variable    ${document}[payload]
        ${vlans}=    Evaluate    [v['vlan_id'] for v in $body['vlan_status']['vlans']]
        Should Contain    ${vlans}    ${20}
        Length Should Be    ${body}[trunk_status][ports]    12
        ${ports}=    Evaluate    [p['name'] for p in $body['trunk_status']['ports']]
        Should Contain    ${ports}    Et12
        Should Be Equal    ${body}[nic_status][interfaces][0][name]    Management1
        Should Be Equal    ${body}[nic_status][interfaces][0][addresses][0][address]    192.168.150.3
        Should Be Equal As Integers    ${body}[nic_status][interfaces][0][addresses][0][prefix_len]    24
        Dictionary Should Not Contain Key    ${body}[route_status]    gateway_of_last_resort
        Length Should Be    ${body}[route_status][routes]    1
    END

A03_Long_Output_Without_Paging
    ${commands}=    Evaluate    ['show interfaces switchport'] * 20
    ${result}=    Run FastCli    @{commands}
    Should Be Equal As Integers    ${result.rc}    0
    ${bytes}=    Evaluate    len($result.stdout.encode())
    Should Be True    ${bytes} > 100000
    ${tails}=    Evaluate    $result.stdout.count('Name: Et12')
    Should Be Equal As Integers    ${tails}    20
    Should Not Contain    ${result.stdout}    --More--

A04_Temporary_Vlan_Create_Read_Remove
    ${before}=    Run FastCli    show vlan brief
    Should Be Equal As Integers    ${before.rc}    0
    Should Not Contain    ${before.stdout}    3999
    TRY
        ${create}=    Run FastCli    configure terminal    vlan 3999    name SONAR_CLI_TEST    end
        Should Be Equal As Integers    ${create.rc}    0
        ${check}=    Run FastCli    show vlan brief
        Should Contain    ${check.stdout}    SONAR_CLI_TEST
    FINALLY
        ${cleanup}=    Run FastCli    configure terminal    no vlan 3999    end
        Should Be Equal As Integers    ${cleanup.rc}    0
    END
    ${after}=    Run FastCli    show vlan brief
    Should Not Contain    ${after.stdout}    3999
    Should Contain    ${after.stdout}    LAB2_DATA

*** Keywords ***
Check Test Directory
    Should Match Regexp    ${TEST_DIR}    ^/mnt/flash/[a-zA-Z0-9_-]+$

Run FastCli
    [Arguments]    @{commands}
    ${script}=    Evaluate    chr(10).join($commands)
    ${invocation}=    Evaluate    'FastCli -p 15 -M -c ' + shlex.quote($script) + ' 2>&1'    modules=shlex
    ${result}=    Run EOS    ${invocation}
    RETURN    ${result}

Run EOS
    [Arguments]    ${script}
    ${payload}=    Evaluate    base64.b64encode($script.encode()).decode()    modules=base64
    ${command}=    Evaluate    'enable' + chr(10) + 'bash /bin/bash -c ' + shlex.quote('printf %s ' + $payload + ' | base64 -d | /bin/bash')    modules=shlex
    Create Directory    ${OUTPUT_DIR}/commands
    ${id}=    Evaluate    uuid.uuid4().hex[:10]    modules=uuid
    ${result}=    Run Process    ssh    -o    BatchMode\=yes    -o    StrictHostKeyChecking\=yes    -o    ConnectTimeout\=5    ${ARISTA_SSH}    ${command}    stdout=${OUTPUT_DIR}/commands/${id}.out    stderr=${OUTPUT_DIR}/commands/${id}.err    timeout=60s
    RETURN    ${result}
