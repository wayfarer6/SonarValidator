grammar IpAddr;

/* =========================================================================
 *  IpAddr — ip(8) 출력 파서 (NIC / 주소 / 라우트 정보 수집)
 *
 *  진입 규칙
 *    document      : `ip a` / `ip addr show`
 *    briefDocument : `ip -br addr show`
 *    routeDocument : `ip route show`
 *
 *  설계 원칙
 *    1) 구조가 안정적인 라인은 전용 규칙(ifaceHeader/ifaceAttr/briefEntry/routeEntry)으로
 *       명시해 파스 트리에서 바로 필드를 꺼낼 수 있게 한다.
 *    2) 형식이 가변적인 라인은 genericLine(elem+ NEWLINE)으로 흡수해
 *       파싱 실패(=정보 누락)를 막는다.
 *    3) 전용 규칙을 genericLine 보다 먼저 배치해 우선권을 준다.
 *
 *  입력은 개행으로 끝나야 한다(래퍼가 보정).
 * ========================================================================= */

document      : item* EOF ;
briefDocument : item* EOF ;
routeDocument : item* EOF ;

/* ARP/이웃 테이블
 *   `ip neigh show` (Linux)
 *     10.0.9.1 dev ens3 lladdr 0c:2d:07:65:99:f3 REACHABLE
 *   `show arp` (Arista)
 *     172.18.10.1       2:31:51  0cae.21dd.0001  Ethernet1
 *   `show ip arp` (Cisco)
 *     Protocol  Address    Age (min)  Hardware Addr   Type   Interface
 *     Internet  10.20.0.4  -          0c2d.0765.99f3  ARPA   GigabitEthernet4
 */
arpDocument : (arpEntry | genericLine | blank)* EOF ;

/* 선두 주소 + 나머지 필드. 세부 해석은 visitor 가 담당한다. */
arpEntry : arpAddress elem* NEWLINE ;
arpAddress : ADDR | IFNAME ;

item : ifaceHeader
     | ifaceAttr
     | briefEntry
     | routeEntry
     | genericLine
     | blank
     ;

/* -------------------- `ip a` / `ip addr show` -------------------- *
 *   2: eth1.131@eth1: <BROADCAST,...> mtu 1500 qdisc noqueue state UP qlen 1000
 *       link/ether 02:42:7c:24:78:01 brd ff:ff:ff:ff:ff:ff
 *       inet 10.10.131.1/24 scope global eth1.131
 *          valid_lft forever preferred_lft forever
 * ----------------------------------------------------------------- */

ifaceHeader : INDEX COLON ifname COLON elem* NEWLINE ;
ifaceAttr   : attrLead elem* NEWLINE ;
attrLead    : LINK | INET | INET6 | LIFETIME ;

/* -------------------- `ip -br addr show` ------------------------- *
 *   eth0    UP    10.40.121.10/24 fe80::42:2fff:fe7e:d600/64
 * ----------------------------------------------------------------- */

briefEntry : IFNAME linkState briefAddr* elem* NEWLINE ;
linkState  : UP | DOWN | UNKNOWN ;
briefAddr  : ADDR | MAC ;

/* -------------------- `ip route show` ---------------------------- *
 *   default via 10.99.10.1 dev eth0 proto ospf metric 20
 *   10.99.10.0/24 dev eth0 proto kernel scope link src 10.99.10.4
 * ----------------------------------------------------------------- */

routeEntry : routeHead elem* NEWLINE ;
/* 라우트 줄의 첫 토큰 = "목적지 자리"
 *
 * 여기에 IFNAME 을 넣으면 아무 단어나 라우트 줄로 인정된다. `hello world`
 * 같은 텍스트도 라우트로 파싱돼 parsed:true / 건수가 부풀려지고, 소비자는
 * "조회했는데 경로 없음" 대신 "경로 N건" 으로 읽는다. 목적지 자리를 커널이
 * 실제로 내는 토큰으로 좁히면 잡음은 genericLine 으로 빠져 무시된다. */
routeHead  : DEFAULT | ROUTETYPE ADDR | ADDR ;

/* ------------------------------ 공통 ----------------------------- */

ifname      : IFNAME ;
genericLine : elem+ NEWLINE ;
blank       : NEWLINE ;
elem        : ~NEWLINE ;

/* ------------------------------ 토큰 ----------------------------- */

NEWLINE : '\r'? '\n' ;
WS      : [ \t]+ -> skip ;

INDEX : [0-9]+ ;

/* MAC 은 ADDR(IEEE 802 표기)보다 먼저 선언해야 최장일치 동점에서 이긴다. */
MAC  : [0-9a-fA-F] [0-9a-fA-F] ( COLON [0-9a-fA-F] [0-9a-fA-F] )* COLON [0-9a-fA-F] [0-9a-fA-F] ;
ADDR : IPV4 ( SLASH [0-9]+ )? | IPV6 ( SLASH [0-9]+ )? ;

/* 키워드류는 IFNAME 보다 먼저 선언한다. */
DEFAULT  : 'default' ;
LINK     : 'link' ( SLASH [a-zA-Z]+ )? ;
INET     : 'inet' ;
INET6    : 'inet6' ;
LIFETIME : 'valid_lft' | 'preferred_lft' ;
UP       : 'UP' ;
DOWN     : 'DOWN' ;
UNKNOWN  : 'UNKNOWN' ;

/* `ip route` 의 경로 타입 키워드. 목적지 앞에 온다.
 *
 * 이 목록이 곧 "라우트 줄의 첫 토큰이 될 수 있는 단어" 다. IFNAME 을 routeHead
 * 에 두는 대신 전용 토큰으로 좁히면 라우트가 아닌 텍스트가 라우트로 오인되지
 * 않는다. IFNAME 과 문자집합이 같으므로 최장일치 동점에서 이기도록 반드시
 * IFNAME 보다 먼저 선언한다. (뒤에 두면 `blackhole` 이 IFNAME 으로 렉싱돼
 * routeHead 가 매칭되지 않고 조용히 0건이 된다.) */
ROUTETYPE : 'blackhole' | 'unreachable' | 'prohibit' | 'throw'
          | 'broadcast' | 'local' | 'multicast' | 'anycast' | 'nat' ;

IFNAME : [a-zA-Z_] [a-zA-Z0-9_.@-]* ;

COLON : ':' ;
SLASH : '/' ;

/* 마지막 catch-all.
 * ':' 를 제외해야 `1:` 이 INDEX+COLON 으로, `eth1.131@eth1:` 이
 * IFNAME+COLON 으로 쪼개져 ifaceHeader 가 매칭된다. IP/MAC 은 전용 토큰이 처리한다. */
WORD : ~[ \t\r\n:]+ ;

fragment IPV4  : OCTET '.' OCTET '.' OCTET '.' OCTET ;
fragment OCTET : [0-9]+ ;

/* `::1`, `fe80::42:2fff:fe7e:d600` 같은 축약형/전체형을 모두 받는다. */
fragment IPV6
    : [0-9a-fA-F]* COLON COLON [0-9a-fA-F:]*
    | [0-9a-fA-F]* COLON [0-9a-fA-F]+ COLON [0-9a-fA-F:]*
    ;
