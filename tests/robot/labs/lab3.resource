*** Variables ***
${LAB_TITLE}           Lab3 · 방화벽 뒤의 두 내부망
${PROJECT_ID}          %{SONAR_LAB3_PROJECT_ID=PRJ-LAB3}
${MANAGEMENT_SERVER}   192.168.151.10
@{EXPECTED_AGENTS}     Lab3-Ubuntu-A    Lab3-Ubuntu-B
@{EXPECTED_CIDRS}      10.30.10.0/24    10.30.20.0/24
