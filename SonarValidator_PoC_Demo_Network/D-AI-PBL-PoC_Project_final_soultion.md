

## 네트워크 장비 현황

- 문제점: Fortigate-Firewall이 네트워크에 연결이 제대로 되지 않음


## 요구사항 

- 포티넷 방화벽 라이선스 문제도 제발 해결되고 EasyRsa로 해야함 
- 포티넷 방화벽은 Trial 쓸꺼임 방화벽에 포티넷 계정 로그인하면 체험판을 자동으로 주는걸로 알고잇음
- 정안되면 포티넷 사이트 들어가서 직접 봐도됨 내 계정
- 포티넷 문제가 해결되면 easyrsa를 Management Console에서 받아서 Fortigate에 API 계정을 만들고 RootCA 인증서를 넣으면됨

## 네트워크 토폴로지 


## 접근방법

- Cisco Router
    - telent localhost:5000
    - Password: Pa129@YX#143


- Fortigate Router
    - telnet localhost:5011
    - ID / PW : admin /Pa129@Y
    - Fortinet 계정 : shseo2023@gmail.com // 9roGPnWyZ4oa1w*

- Management Console
    -  ssh osboxes@192.168.122.32  // id 파일이 이미 있어서 별도의 인증 필요 없음
    -  Management Console에서 방화벽 주소는 Control Plane IP: 10.20.0.2  Port 3


연결: Cisco 라우터 Gi2에 Fortigate Port 1이 직결함.


## 방화벽에 한 설정들

```bash
# Fortigate 초기 ip 설정


config system interface
    edit port1
        set mode static
        set ip 172.128.0.2 255.255.255.0
        set allowaccess ping https ssh
    next
end

show system interface port1

config system interface
    edit port3
        set mode static
        set ip 10.20.0.2 255.255.255.0
        set allowaccess ping https http ssh
    next
end

show system interface port3


# routing Setup (need subnetting)

config router static
edit 1
set dst 0.0.0.0 0.0.0.0
set gateway 172.128.0.1
set device port 1
next

edit 2
set dst 10.20.0.0 255.255.255.0
set gateway 10.20.0.1
set device port3

# licnese 
config system fortigate
set interface port1
end


# add static routing path

config router static



edit 1

set dst 10.0.8.0 255.255.255.0

set gateway 172.18.10.2

set device port2

next



edit 2

set dst 10.0.9.0 255.255.255.0

set gateway 172.18.10.2

set device port2

next



end


# add  nat policy

config firewall policy



edit 1

set name "ARISTA-INTERNET"

set srcintf "port2"

set dstintf "port1"

set srcaddr "all"

set dstaddr "all"

set action accept

set schedule "always"

set service "ALL"

set nat enable

next

# add routing table 

config router static



edit 1

set dst 0.0.0.0 0.0.0.0

set gateway 172.128.0.1

set device port1

next

config router static

edit 10

set dst 10.0.8.0 255.255.255.0

set gateway 172.18.10.2

set device port2

next

end


end


end


```


## Cisco Router 설정 (완벽하지는 않음 누락된 명령어 잇을수 있음)

```bash
CiscoCatalyst 8000v 


configure terminal

interface GigabitEthernet1
ip address 192.168.122.254 255.255.255.0
no shutdown

interface GigabitEthernet2

ip address 172.128.0.1 255.255.255.0
no shutdown

interface GigabitEthernet4
ip address 10.20.0.1 255.255.255.0
no shutdown

# set default route
ip route 0.0.0.0 0.0.0.0 192.168.122.1
end 
write memory 


#NAT Setup

configure terminal
ip access-list standard FORTIGATE_NAT_ACL
permit 172.128.0.0 0.0.0.255
exit

interface GigabitEthernet1
ip nat outside 
exit
end


interface GigabitEternet2
ip nat inside
exit


ip nat inside source list FORTIGATE_NAT_ACL interface GigabitEthernet1 overload
exit
end 

# Cisco Web UI Access

configure terminal
ip access-list standard WEB_ACCESS_ACL
permit 10.20.0.0 0.0.0.255
exit

configure terminal
username admin privilege 15 secret cisco123
end

write memory

# Privilege 15 is similiar to root account
```