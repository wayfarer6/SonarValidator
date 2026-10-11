grammar SwitchTopology;

/* =========================================================================
 *  SwitchTopology — Cisco IOS-XE / Arista EOS 스위치 CLI 출력 파서
 *
 *  진입 규칙
 *    runningDocument : `show running-config`
 *    vlanDocument    : `show vlan brief`
 *    briefDocument   : `show ip interface brief`
 *    portDocument    : `show interfaces switchport`
 *
 *  Cisco 와 Arista 는 명령 체계가 95% 이상 동일하므로 한 문법을 공유하고,
 *  차이(마스크 표기 `255.255.255.0` vs `/24`, ACL 문법, 포트명 규칙)는
 *  visitor 가 흡수한다.
 *
 *  `show interfaces switchport` 는 벤더/플랫폼마다 키 이름이 다르다.
 *      Name: Ethernet1
 *      Switchport: Enabled
 *      Administrative Mode: static access
 *      Access Mode VLAN: 99 (TRANSIT)
 *      Trunking VLANs Enabled: 111,112
 *  따라서 "KEY: value" 형태를 attrLine 하나로 받고 해석은 visitor 가 한다.
 * ========================================================================= */

runningDocument : configLine* EOF ;
vlanDocument    : vlanItem* EOF ;
briefDocument   : briefItem* EOF ;
portDocument    : portItem* EOF ;

/* ----------------------- show running-config --------------------- */

configLine : elem+ NEWLINE ;

/* ------------------------- show vlan brief ----------------------- */
/*      VLAN Name                             Status    Ports
 *      ---- -------------------------------- --------- --------------------
 *      1    default                          active    Et4, Et5
 *      8    VLAN8                            active    Cpu, Et2
 *                                                      Et10, Et11       <- 이어짐
 */

vlanItem : vlanHeader | vlanEntry | vlanSeparator | genericLine | blank ;

vlanHeader    : VLAN elem* NEWLINE ;
vlanEntry     : vlanId vlanName vlanStatus portList? elem* NEWLINE ;
vlanSeparator : DASHES elem* NEWLINE ;

/* 이름/포트 자리는 토큰 종류가 뒤섞인다.
 *   `default`  -> IFNAME (IFNAME 이 IDENT 보다 먼저 선언됨)
 *   `VLAN8`    -> PORTNAME
 *   `Cpu`      -> IFNAME
 *   `Et10`     -> PORTNAME
 * 따라서 여러 토큰을 모두 허용하는 nameToken 으로 받는다. */
vlanId     : NUMBER ;
vlanName   : nameToken ;
vlanStatus : STATUSWORD ;
portList   : portToken ( COMMA portToken )* ;
portToken  : PORTNAME | IFNAME | IDENT | NUMBER ;
nameToken  : IDENT | IFNAME | PORTNAME | ATTRWORD | NUMBER ;

/* ---------------------- show ip interface brief ------------------ */
/*      Interface              IP-Address      OK? Method Status   Protocol
 *      GigabitEthernet1       192.168.122.254 YES NVRAM  up       up
 *      GigabitEthernet3       unassigned      YES NVRAM  down     down
 */

briefItem : briefHeader | briefEntry | genericLine | blank ;

briefHeader : INTERFACE elem* NEWLINE ;
briefEntry  : ifname addrOrUnassigned? briefField* elem* NEWLINE ;

addrOrUnassigned : ADDR | UNASSIGNED ;
briefField       : STATUSWORD ;

/* ------------------ show interfaces switchport ------------------- */
/*      Name: Ethernet1
 *      Switchport: Enabled
 *      Access Mode VLAN: 99 (TRANSIT)
 *      Trunking VLANs Enabled: 111,112
 *  또는 IOS 스타일
 *      interface Ethernet1
 */

portItem : portEntry | genericLine | blank ;

/* `Name:`, `Access Mode VLAN:`, `Administrative Trunking Encapsulation:` 등
 * 키는 1~4개 단어로 이루어지므로 keyWord+ COLON 형태로 받는다. */
portEntry : INTERFACE ifname elem* NEWLINE
          | keyWord+ COLON elem* NEWLINE ;

/* ------------------------------ 공통 ----------------------------- */

/* IFNAME 과 ATTRWORD 는 같은 문자집합이라 어느 쪽으로 토큰화될지
 * 선언 순서에 달렸다. 두 토큰을 모두 허용해 순서 의존성을 제거한다. */
ifname      : IFNAME | ATTRWORD | PORTNAME | IDENT ;

/* 키를 이루는 단어. IFNAME/ATTRWORD 는 같은 문자집합이라
 * 어느 쪽으로 토큰화될지 선언 순서에 달렸으므로 둘 다 허용한다. */
keyWord     : ATTRWORD | IFNAME | IDENT | VLAN | INTERFACE | STATUSWORD | NUMBER ;
genericLine : elem+ NEWLINE ;
blank       : NEWLINE ;
elem        : ~NEWLINE ;

/* ------------------------------ 토큰 ----------------------------- */

NEWLINE : '\r'? '\n' ;
WS      : [ \t]+ -> skip ;

/* `VLAN` 헤더 토큰. `VLAN8` 은 더 길게 매칭되는 IDENT 가 이긴다. */
VLAN      : 'VLAN' ;
INTERFACE : 'interface' | 'Interface' ;

ADDR       : IPV4 ( SLASH [0-9]+ )? | IPV6 ( SLASH [0-9]+ )? ;
NUMBER     : [0-9]+ ;
PORTNAME   : [a-zA-Z]+ [0-9]+ ( '/' [0-9]+ )* ;
UNASSIGNED : 'unassigned' ;
DASHES     : '-'+ ;

/* 상태/응답 컬럼. 같은 길이의 IDENT 보다 먼저 선언해 우선권을 준다. */
STATUSWORD : 'up' | 'down' | 'administratively' | 'deleted'
           | 'active' | 'act' | 'suspended'
           | 'YES' | 'NO' | 'NVRAM' | 'unset' | 'manual' ;

/* ':' 를 포함하지 않는다. 포함하면 `Name:` 이 IFNAME 한 토큰이 되어
 * portEntry(ATTRWORD COLON ...) 매칭이 깨진다. (vlanEntry 도 동일) */
IFNAME   : [a-zA-Z] [a-zA-Z0-9/._-]* ;
/* ':' 를 포함하지 않는다. 포함하면 `Name:` 이 한 토큰이 되어
 * portEntry(ATTRWORD COLON ...) 매칭이 깨진다. */
ATTRWORD : [a-zA-Z_] [a-zA-Z0-9_.-]* ;
IDENT    : [a-zA-Z_] [a-zA-Z0-9_.-]* ;

COMMA  : ',' ;
COLON  : ':' ;
SLASH  : '/' ;
LPAREN : '(' ;
RPAREN : ')' ;

fragment IPV4  : OCTET '.' OCTET '.' OCTET '.' OCTET ;
fragment OCTET : [0-9]+ ;

fragment IPV6
    : [0-9a-fA-F]* COLON COLON [0-9a-fA-F:]*
    | [0-9a-fA-F]* COLON [0-9a-fA-F]+ COLON [0-9a-fA-F:]*
    ;
