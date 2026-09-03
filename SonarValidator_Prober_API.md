# SonarValidator Prober API

이 문서는 `SonarValidator_Prober`의 주요 HPP 선언을 기준으로 현재 클래스, 구조체, 메서드와 소스 경로를 정리한 문서입니다.

## 프로젝트 경로

- 애플리케이션 진입점: `SonarValidator_Prober/main.cpp`
- 설정: `SonarValidator_Prober/prober_config.hpp`, `prober_config.cpp`
- 초기화: `SonarValidator_Prober/init.hpp`, `init.cpp`
- 관리 서비스: `SonarValidator_Prober/management_service.hpp`, `management_service.cpp`
- 데이터베이스: `SonarValidator_Prober/database/database_service.hpp`, `database_service.cpp`
- 라우팅: `SonarValidator_Prober/router/routing_table.hpp`, `routing_table.cpp`
- 스위치: `SonarValidator_Prober/switch/switch.hpp`, `switch.cpp`
- 방화벽: `SonarValidator_Prober/firewall/firewall.hpp`, `firewall.cpp`
- Telemetry 통신: `SonarValidator_Prober/telemetry/telemetry_service.hpp`, `telemetry_service.cpp`
- 네트워크 값 타입: `SonarValidator_Prober/network.hpp`

## ProberConfig

헤더: `SonarValidator_Prober/prober_config.hpp`

에이전트의 시스템 및 중앙 서버 접속 설정을 보관합니다.

### 타입

- `DeviceType`: `kSwitch`, `kVirtualMachine`, `kFirewall`, `kRouter`

### 메서드

- `ProberConfig(...)`: 에이전트명, 커널, 배포판, 장치 유형, 메모리, 서버 IPv4, 포트로 설정을 생성합니다.
- `GetAgentName()`, `GetKernelName()`, `GetDistributionName()`: 기본 문자열 설정을 반환합니다.
- `GetDeviceType()`, `GetMemorySizeBytes()`, `GetServerIpv4()`, `GetServerPort()`: 장치, 메모리, 서버 설정을 반환합니다.
- `SetAgentName(...)`, `SetKernelName(...)`, `SetDistributionName(...)`: 기본 문자열 설정을 변경합니다.
- `SetDeviceType(...)`, `SetMemorySizeBytes(...)`, `SetServerIpv4(...)`, `SetServerPort(...)`: 장치 및 서버 설정을 변경합니다.
- `GetArchitecture()`, `SetArchitecture(...)`: 시스템 아키텍처를 조회하거나 변경합니다.
- `DetectKernelName()`, `DetectDistributionName()`, `DetectMemorySizeBytes()`, `DetectArchitecture()`: 로컬 시스템 정보를 탐지합니다.
- `DetectServerIpv4()`, `DetectServerPort()`, `DetectDeviceType()`: 서버 정보와 장치 유형을 탐지합니다.

## AppInitializer

헤더: `SonarValidator_Prober/init.hpp`

실행에 필요한 데이터 디렉터리, 설정, SQLite 데이터베이스를 준비하는 정적 유틸리티입니다.

- `EnsureDataDirectory(path)`: 데이터 디렉터리를 준비합니다.
- `InitializeConfig(path, config)`: 설정 파일을 읽어 `ProberConfig`를 초기화합니다.
- `InitializeDatabase(database_path, config, database_handle)`: SQLite 데이터베이스를 초기화하고 핸들을 반환합니다.
- `PrepareRuntime(data_directory, config_file_path, sqlite_db_path, sqlite_template_path, config, database_handle)`: 런타임 준비 단계를 묶어 수행합니다.

## DatabaseService 및 DatabaseQueue

헤더: `SonarValidator_Prober/database/database_service.hpp`

비동기 작업 큐를 사용해 SQLite 상태와 설정을 저장합니다.

### 보조 타입

- `DbHandler`: `sqlite3*`를 닫는 삭제자입니다.
- `DbHandle`: `DbHandler`를 사용하는 `std::unique_ptr<sqlite3>` 별칭입니다.
- `DatabaseResult`: SQL 작업 결과 벡터와 실행한 SQL 문자열을 보관합니다.
- `DatabaseTask`: SQLite 작업 함수와 결과를 받을 `std::promise`를 묶습니다.

### DatabaseService 메서드

- `DatabaseService(database_path)`: 데이터베이스 서비스와 저장 위치를 설정합니다.
- `~DatabaseService()`: 서비스 자원을 정리합니다.
- `Start()`: 데이터베이스 작업 처리를 시작합니다.
- `UpdateStatus(value)`: 에이전트 상태를 저장합니다.
- `SaveConfig(key, value)`: 설정 키와 값을 저장합니다.
- `Worker(stop_token)`: 내부 작업자 스레드에서 큐 작업을 처리합니다. (`private`)

### DatabaseQueue 메서드

- `DatabaseQueue()`: 비어 있는 작업 큐를 생성합니다.
- `Push(task)`: 작업을 큐에 추가합니다.
- `Pop(stop_token, task)`: 중단 토큰을 관찰하며 작업을 꺼냅니다.
- `Close()`: 큐를 닫고 대기 중인 소비자가 종료할 수 있게 합니다.

## RoutingTable

헤더: `SonarValidator_Prober/router/routing_table.hpp`

라우팅 연결 정보와 경로 목록을 관리하며 FRR/Cisco 출력 파싱 및 JSON 변환을 제공합니다.

### RouteEntry

`prefix`, `next_hop`, `protocol`, `metric`, `interface_name`으로 한 라우트를 표현합니다.

### 메서드

- `RoutingTable()`: 라우팅 테이블을 생성합니다.
- `GetConnectionId()`, `GetConnectedIp()`, `GetConnectedNodeId()`, `GetRoutingProtocol()`: 연결 및 프로토콜 값을 반환합니다.
- `SetConnectionId(...)`, `SetConnectedIp(...)`, `SetConnectedNodeId(...)`, `SetRoutingProtocolValue(...)`: 연결 및 프로토콜 값을 설정합니다.
- `AddRoute(route)`, `AddRoute(prefix, next_hop, protocol, metric, interface_name)`: 라우트를 추가합니다.
- `GetRoutes()`, `GetRouteCount()`, `ClearRoutes()`: 라우트 목록을 조회하거나 비웁니다.
- `ParsingRoutingTabe()`: 파싱된 라우팅 테이블 문자열을 반환합니다.
- `GetRoutingTabe(router_id)`: 라우터 ID를 기준으로 라우팅 정보를 가져옵니다.
- `ParseFrrTable(raw_output)`, `ParseCiscoTable(raw_output)`: FRR 또는 Cisco 형식의 출력을 파싱합니다.
- `RemoveConnection()`, `AddConnection()`: 연결 정보를 제거하거나 추가합니다.
- `ToJson()`: 현재 테이블을 `nlohmann::json`으로 변환합니다.
- `SetRoutingProtocol()`: 라우팅 프로토콜을 설정합니다.

### 뷰 클래스

- `RoutingTableView::Render(table)`: 라우팅 테이블 표시 형식을 정의하는 순수 가상 메서드입니다.
- `FrrRoutingTableView::Render(table)`: FRR 형식으로 렌더링합니다.
- `CiscoRoutingTableView::Render(table)`: Cisco 형식으로 렌더링합니다.

## Switch 및 토폴로지 파서

헤더: `SonarValidator_Prober/switch/switch.hpp`

스위치의 포트, 브리지, 라우팅 정보와 벤더별 토폴로지 파싱을 관리합니다.

### 타입

- `PortInfo`: 포트명, 인터페이스명, access/trunk VLAN 목록, 내부 포트 여부를 보관합니다.
- `BridgeInfo`: 브리지명과 연결 포트 목록을 보관합니다.
- `SwitchVendor`: `OpenVSwitch`, `CiscoCatalyst8000v`를 구분합니다.

### 파서

- `TopologyParser::parse(raw_output)`: 토폴로지 파서의 공통 인터페이스입니다.
- `OpenVSwitchTopologyParser::parse(raw_output)`: Open vSwitch 출력을 파싱합니다.
- `CiscoTopologyParser::parse(raw_output)`: Cisco 출력물을 파싱합니다.

### Switch 메서드

- `Switch(name)`, `~Switch()`: 스위치를 생성하고 정리합니다.
- `getName()`, `setName(name)`: 스위치 이름을 조회하거나 설정합니다.
- `loadTopology(raw_output, vendor)`: 벤더에 맞는 토폴로지 파서를 선택합니다.
- `addRoute(destination, next_hop)`, `updateRoutingTable(destination, next_hop)`: 라우트를 추가하거나 갱신합니다.
- `addPort(destination, port)`, `updatePort(destination, port)`: 포트 정보를 추가하거나 갱신합니다.
- `printRoutes()`, `printPorts()`: 라우트와 포트를 출력합니다.
- `parseCiscoSwitchTopology(raw_output)`, `parseOpenVSwitchTopology(raw_output)`: 벤더별 토폴로지를 직접 파싱합니다.
- `getBridges()`: 파싱된 브리지 목록을 반환합니다.

## Firewall

헤더: `SonarValidator_Prober/firewall/firewall.hpp`

nftables 규칙 출력에서 테이블, 체인, 규칙 구조를 추출합니다.

### 타입

- `NftRule`: 원문, statement, match, action, connection state를 보관합니다.
- `NftChain`: family, name, type, hook, priority, policy와 규칙 목록을 보관합니다.
- `NftTable`: family, name과 체인 목록을 보관합니다.

### 메서드

- `parseNftablesRuleset(raw_output)`: nftables 원시 출력물을 파싱합니다.
- `tables()`: 파싱된 테이블 목록을 반환합니다.
- `tableCount()`: 테이블 개수를 반환합니다.

## TelemetryService

헤더: `SonarValidator_Prober/telemetry/telemetry_service.hpp`

Boost.Asio/Beast WebSocket으로 중앙 서버와 단일 세션 통신을 담당합니다. `CommunicationService`는 `TelemetryService`의 별칭입니다.

- `TelemetryService()`, `TelemetryService(host, port, target)`: 기본 또는 지정 서버로 서비스를 생성합니다.
- `~TelemetryService()`: 네트워크 자원을 정리합니다.
- `connect()`: 서버에 연결합니다.
- `sendText(message)`: 문자열 메시지를 전송합니다.
- `receiveText()`: 문자열 메시지를 수신합니다.
- `sendRequest(request, target)`: 지정 대상에 요청을 전송합니다.
- `initialize(host, port, target)`: 내부 연결 설정을 초기화합니다. (`private`)

## ManagementService

헤더: `SonarValidator_Prober/management_service.hpp`

관리 서버와 WebSocket으로 정책을 주고받고 장치별 상태를 확인합니다.

- `ManagementService()`, `ManagementService(host, port, target)`: 관리 서비스를 생성합니다.
- `~ManagementService()`: 네트워크 및 스레드 자원을 정리합니다.
- `connect()`: 관리 서버에 연결합니다.
- `sendText(message)`, `receiveText()`: 메시지를 송수신합니다.
- `applyPolicy(policy_name, payload)`: 정책을 적용합니다.
- `fetchPolicy(policy_name, payload)`: 정책을 조회해 payload에 기록합니다.
- `CheckSwitchStatus()`, `CheckRouterStatus()`, `CheckFirewallStatus()`: 장치 유형별 상태를 확인합니다.

## 네트워크 기본 타입

헤더: `SonarValidator_Prober/network.hpp`

- `VLan(vlan_id)`, `getVLANID()`: VLAN 식별자를 생성하고 조회합니다.
- `Subnet(subnet_id)`: 서브넷 식별자를 생성합니다.
- `NIC(nic_id)`: 네트워크 인터페이스 식별자를 생성합니다.

## 빌드 경로 반영 사항

- CMake 실행 파일 목록을 `database/`, `router/`, `switch/`, `firewall/`, `telemetry/` 실제 경로로 변경했습니다.
- 루트 서비스 include를 하위 헤더 경로로 변경했습니다.
- `switch.hpp`의 router 및 network include를 헤더 위치 기준 상대 경로로 변경했습니다.
- `telemetry_service_test`에 구현 파일을 연결해 링크 오류를 해결했습니다.
- `SonarValidator_Prober/.vscode/tasks.json`의 database 구현 파일 경로를 최상위 워크스페이스 기준으로 변경했습니다.
