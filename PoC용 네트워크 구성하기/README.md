# 개요

- 모의로 만든 네트워크로 안에 들어있는거는 별로 없고 네트워크 프로젝트 테스트용임 

## 설명 

- 각 존은 Confidential, Sensitive, Open으로 되어 있고
- 각 존에 있는 스위치에 물려있는 각 포트는 하나의 VLAN 으로,
각 서브넷으로 나누어 져야 하고 (Subnet 크기는 /24임)
- ip 대역대는 임의로 정하면됨 (내부망용임 다들)

- 각 서버, 스위치, 라우터, 방화벽은 모두 poc를 위해 /etc에
설정된 스크립트 등이 있으니 읽어보고 활용해야함.

- 각 라우터간 통신을 위한 라우팅은 OSPF 로 해야함.

- 아마 VLAN Trunk가 필요할 수도 있는데 이부분도 알아서 하고 출력에 기제 하셈

- 그리고 나중에는 네트워크 장비들이 허브를 통해 management와 연결되어있는데 제어평면과 데이터 평면으로 너가 나누어줘야 함.

- 연결 방법

Node,Console
AFCCS,telnet localhost:5026
ATICS,telnet localhost:5022
C4I-Network-Router,telnet localhost:5019
DMZ-Router,telnet localhost:5040
Firewall,telnet localhost:5037
Gateway-Router,telnet localhost:5015
Hub1,none
Internet,none
KNCCS,telnet localhost:5024
Management-Console,vnc localhost:5901
NAT1,none
Public-Web-Server,telnet localhost:5000
Survillance-Network-Router,telnet localhost:5013
Switch-0,telnet localhost:5042
Switch-1,telnet localhost:5044
Switch-2,telnet localhost:5046
Switch-3,telnet localhost:5048
Switch-4,telnet localhost:5050
TOD-Cam,telnet localhost:5028
UAV,telnet localhost:5030
VDI-1,telnet localhost:5032
VDI-2,telnet localhost:5034
VDI-Router,telnet localhost:5017


## 출력 

- 설정한 IP 대역대, 서브넷 마스크 값, 포트별 할당된 ip
- 서버별 nic 현황을 마크다운 표 형태로 다 출력해줘

- 컨테이너 스위치 라우터에 쓴 네트워크 명령어는 각 기기별로 별도의 md 파일 만들어서 따로 파일로 만들어서 여기 폴더에 넣을것.

