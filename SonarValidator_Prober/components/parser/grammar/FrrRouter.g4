grammar FrrRouter;

/* =========================================================================
 *  FrrRouter — FRRouting / Cisco IOS-XE 라우터 CLI 출력 파서
 *
 *  진입 규칙
 *    routeDocument  : `show ip route`
 *    ifaceDocument  : `show ip interface brief`
 *    detailDocument : `show interface <name>`
 *
 *  FRR 과 Cisco 는 출력이 95% 이상 유사하므로 한 문법으로 처리하고,
 *  차이점(프로토콜 코드 집합, 요약 줄 유무)은 visitor 가 흡수한다.
 *
 *  라우트 한 줄 형식(FRR):
 *      O>* 10.20.111.0/24 [110/200] via 10.99.10.5, eth0, weight 1, 00:17:25
 *      O   10.99.10.0/24 [110/100] is directly connected, eth0, weight 1, 00:17:35
 *  Cisco:
 *      S*    0.0.0.0/0 [1/0] via 192.168.122.1
 *      C     10.20.0.0/24 is directly connected, GigabitEthernet4
 * ========================================================================= */

routeDocument  : routeItem* EOF ;
ifaceDocument  : ifaceItem* EOF ;
detailDocument : detailItem* EOF ;

/* -------------------------- show ip route ------------------------ */

routeItem : routeLine | subnetSummary | genericLine | blank ;

/* 코드(+ selected '*' / backup '&' 등) 뒤에 목적지가 오는 실제 경로 줄 */
routeLine   : routeCode+ destination elem* NEWLINE ;
routeCode   : ROUTECODE ;
destination : DEFAULT | ADDR ;

/* Cisco 의 `10.0.0.0/8 is variably subnetted, 2 subnets, 2 masks` */
subnetSummary : ADDR IS VARIABLY SUBMITTED elem* NEWLINE ;

/* ------------------- show ip interface brief --------------------- */

ifaceItem : briefHeader | briefEntry | genericLine | blank ;

briefHeader : INTERFACE elem* NEWLINE ;

/* 열 개수가 벤더/버전마다 다르므로(IP 없음/OK?·Method 있음 등)
 * 첫 열(인터페이스명)만 고정하고 나머지는 briefField* 로 받는다.
 * status/protocol 판정은 visitor 가 STATUSWORD 순서로 해석한다.        */
briefEntry : ifname addrOrUnassigned? briefField* elem* NEWLINE ;

addrOrUnassigned : ADDR | UNASSIGNED ;
briefField       : METHOD | STATUSWORD ;

/* ---------------------- show interface <name> -------------------- */

detailItem : ifaceHeader | detailAttr | genericLine | blank ;

ifaceHeader : INTERFACE ifname IS elem* NEWLINE ;
detailAttr  : ATTRWORD elem* NEWLINE ;

/* ------------------------------ 공통 ----------------------------- */

ifname      : IFNAME | PORTNAME | IDENT ;
genericLine : elem+ NEWLINE ;
blank       : NEWLINE ;
elem        : ~NEWLINE ;

/* ------------------------------ 토큰 ----------------------------- */

NEWLINE : '\r'? '\n' ;
WS      : [ \t]+ -> skip ;

/* FRR/Cisco 가 실제로 쓰는 라우트 코드.
 *
 * 코드와 선택 마커(`*` selected, `>` FIB, `&` backup)가 붙어 나오므로
 * (`O>*`, `S>*`, `C>*`) 반드시 "코드+마커 전체"를 한 토큰으로 잡아야 한다.
 * 조각으로 나누면 3글자 catch-all(WORD)과 최장일치 동점이 되어
 * WORD 가 이기고 routeLine 이 genericLine 으로 떨어진다.
 *
 * 허용 코드: FRR K C S R O I B E N A D L T / Cisco L IA N1 N2 E1 E2 su L1 L2 o P a U H G M m i p s
 */
ROUTECODE : ROUTELETTER [0-9]? [*>&]*
          | [*>&]+ ;

fragment ROUTELETTER
    : 'K' | 'C' | 'S' | 'R' | 'O' | 'I' | 'B' | 'E' | 'N' | 'A' | 'D'
    | 'L' | 'T' | 'P' | 'U' | 'H' | 'G' | 'M' | 'm' | 'o' | 'a' | 'l'
    | 'i' | 'p' | 'n' | 's' | 'u'
    ;

DEFAULT : 'default' ;

ADDR : IPV4 ( SLASH [0-9]+ )? | IPV6 ( SLASH [0-9]+ )? ;

CODES      : 'Codes:' ;
GATEWAY    : 'Gateway' ;
IS         : 'is' ;
VARIABLY   : 'variably' ;
SUBMITTED  : 'subnetted' ;
INTERFACE  : 'Interface' | 'interface' ;
UNASSIGNED : 'unassigned' ;
METHOD     : 'YES' | 'NO' | 'NVRAM' | 'unset' | 'manual' ;
STATUSWORD : 'up' | 'down' | 'administratively' | 'deleted' ;

IFNAME   : [a-zA-Z] [a-zA-Z0-9/._:@-]* ;
/* Ethernet1 / GigabitEthernet1/0/1 처럼 숫자가 섞인 인터페이스명 */
PORTNAME : [a-zA-Z]+ [0-9]+ ( '/' [0-9]+ )* ;
IDENT    : [a-zA-Z_] [a-zA-Z0-9_.-]* ;

ATTRWORD : [a-zA-Z_] [a-zA-Z0-9_.:-]* ;

COLON : ':' ;
SLASH : '/' ;

/* ':' 를 제외해 `eth0 is up, line protocol is up` 같은 줄에서
 * ifaceHeader(IFNAME ...) 매칭이 유지되도록 한다. */
WORD : ~[ \t\r\n:]+ ;

fragment IPV4  : OCTET '.' OCTET '.' OCTET '.' OCTET ;
fragment OCTET : [0-9]+ ;

fragment IPV6
    : [0-9a-fA-F]* COLON COLON [0-9a-fA-F:]*
    | [0-9a-fA-F]* COLON [0-9a-fA-F]+ COLON [0-9a-fA-F:]*
    ;
