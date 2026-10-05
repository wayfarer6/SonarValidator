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
/* 첫 토큰은 주소이거나, 주소 앞에 오는 패밀리 단어다.
 *
 * 중요: 여기에 IFNAME 을 허용하면 안 된다. 허용하면 Cisco `show ip arp`
 * 헤더 줄(`Protocol Address Age (min) Hardware Addr Type Interface`)의 첫
 * 토큰 `Protocol` 이 IFNAME 으로 매칭돼 헤더가 데이터 행으로 오인된다.
 * 첫 토큰을 이 두 종류로 좁히면 헤더는 genericLine 으로 떨어지므로
 * visitor 가 헤더를 걸러내는 코드를 쓸 필요가 없다.
 *   `ip neigh show`     10.0.9.1 dev ens3 lladdr ...        -> ADDR
 *   `show ip arp`       Internet 10.20.0.4 - 0c2d... ARPA  -> INETWORD
 */
arpAddress : addr | INETWORD ;

item : ifaceHeader
     | ifaceAttr
     | lifetimeAttr
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

/* 헤더 꼬리의 `mtu 65536 qdisc noqueue state UNKNOWN qlen 1000` 을
 * 키/값 짝으로 남긴다. elem* 로 넘기면 짝이 파스 트리에서 사라져
 * visitor 가 옆 토큰을 세다가 한 칸 어긋난다(`mtu` 값이 `mtu` 가 된다). */
ifaceHeader      : INDEX COLON ifname COLON ifaceHeaderPart* NEWLINE ;
/* 키/값 짝을 남기되, 키를 모르는 조각은 통째로 흘려보내
 * 정보 누락(=파싱 실패)을 막는다. */
ifaceHeaderPart  : ifaceHeaderField | elem ;
ifaceHeaderField : ifaceHeaderKey ifaceHeaderValue ;
ifaceHeaderKey   : MTU | QDISC | STATE | QLEN | GROUP | NETNSID ;
/* 값은 숫자/단어 어느 쪽이든 온다(표기 차이일 뿐이다). 한 토큰을 그대로 받는다. */
ifaceHeaderValue : elem ;
ifaceAttr   : attrLead elem* NEWLINE ;
attrLead    : LINK | INET | INET6 ;

/* -------------------- 주소 생명주기 ------------------------------ *
 *   valid_lft forever preferred_lft forever
 *
 * LIFETIME 을 attrLead 에 두면 첫 키(valid_lft)가 선두 토큰으로 먹혀
 * elem 목록에서 사라진다. 그러면 값(forever)만 남아 짝이 깨지고,
 * visitor 가 `forever` 를 키로 쓰게 된다.
 * 키/값 짝을 규칙으로 남겨 두면 visitor 는 짝을 그대로 꺼내기만 한다.
 * ----------------------------------------------------------------- */
lifetimeAttr : lifetimePair+ NEWLINE ;
lifetimePair : LIFETIME elem ;

/* -------------------- `ip -br addr show` ------------------------- *
 *   eth0    UP    10.40.121.10/24 fe80::42:2fff:fe7e:d600/64
 * ----------------------------------------------------------------- */

briefEntry : IFNAME linkState briefAddr* elem* NEWLINE ;
linkState  : UP | DOWN | UNKNOWN ;
/* 주소와 MAC 의 구분이 문법 레벨에서 끝난다. visitor 는 토큰 종류만 본다. */
briefAddr  : addr | MAC | MACDOTTED ;

/* -------------------- `ip route show` ---------------------------- *
 *   default via 10.99.10.1 dev eth0 proto ospf metric 20
 *   10.99.10.0/24 dev eth0 proto kernel scope link src 10.99.10.4
 * ----------------------------------------------------------------- */

routeEntry   : routeHead routeField* NEWLINE ;
/* 라우트 줄의 첫 토큰 = "목적지 자리"
 *
 * 중요: 여기에 IFNAME 을 넣으면 아무 단어나 라우트 줄로 인정된다. 예전에는
 * 그렜더니 `hello world` 같은 텍스트도 라우트 2건으로 파싱됐다. 문법상으론
 * `parsed:true` 이고 소비자는 "조회했는데 경로가 없다" 로 오해한다. 잠음(오삼)을
 * 문법 수준에서 지우려면 목적지 자리를 커널이 실제로 내는 토큰으로 좁혀야 한다.
 *
 * 커널 `ip route show` / `ip -6 route` 가 목적지 자리에 내는 것은 다음 뿐이다.
 *   default | 0.0.0.0/0 | ::/0          → 기본 경로
 *   <주소>/<prefix>                    → 일반 경로
 *   blackhole|unreachable|prohibit ... → 타입 키워드 + 주소
 * 그 외는 라우트 줄이 아니므로 genericLine 으로 흘러가 조용히 무시된다.
 */
routeHead    : DEFAULT | DEFAULTADDR | blackholeDestination | addr ;
/* `blackhole 10.0.0.0/8` 처럼 타입 키워드 뒤에 목적지가 오는 형태.
 * 목적지 자리를 문법에서 확정하면 visitor 가 head 의 종류를 묻지 않는다. */
blackholeDestination : ROUTETYPE addr ;

/* 라우트 줄 꼬리를 의미 단위로 분리한다.
 *   `via 10.99.10.1` `dev eth0` `proto kernel` `metric 20`
 *   `scope link` `src 10.99.10.2` `table 100` `linkdown`          */
routeField    : routeVia | routeDev | routeProto | routeMetric
              | routeSrc | routeScope | routeTable | routeLinkdown
              | elem ;
routeVia      : VIA addr ;
routeDev      : DEV ifname ;
routeProto    : PROTO protoName ;
routeMetric   : METRIC INDEX ;
routeScope    : SCOPE scopeName ifname? ;
routeSrc      : SRC addr ;
routeTable    : TABLE INDEX ;
routeLinkdown : LINKDOWN ;
/* `proto kernel`/`proto ospf`/`proto static`. 주소/키 토큰을 받지 않아
 * 뒤따르는 필드를 삼키지 않는다. */
protoName     : IFNAME | WORD | SCOPEWORD | GLOBAL ;
/* `scope host` / `scope link` / `scope global` */
scopeName     : SCOPEWORD | GLOBAL ;

/* 주소는 패밀리별로 토큰이 갈리므로 `family` 를 visitor 가 추측할 필요가 없다. */
addr : ADDR4 | ADDR6 ;

/* ------------------------------ 공통 ----------------------------- */

ifname      : IFNAME ;
genericLine : elem+ NEWLINE ;
blank       : NEWLINE ;
elem        : ~NEWLINE ;

/* ------------------------------ 토큰 ----------------------------- */

NEWLINE : '\r'? '\n' ;
WS      : [ \t]+ -> skip ;

INDEX : [0-9]+ ;

/* `2:31:51`(시:분:초) 형태의 이웃 age.
 * ADDR 의 IPv6 대안(`[0-9a-fA-F]* ':' ...`)과 길이가 같으므로
 * 반드시 ADDR 보다 먼저 선언해야 AGE 로 토큰화된다. */
AGE : [0-9]+ COLON [0-9]+ COLON [0-9]+ ;

/* MAC 은 ADDR(IEEE 802 표기)보다 먼저 선언해야 최장일치 동점에서 이긴다. */
MAC  : [0-9a-fA-F] [0-9a-fA-F] ( COLON [0-9a-fA-F] [0-9a-fA-F] )* COLON [0-9a-fA-F] [0-9a-fA-F] ;

/* Cisco / Arista 의 점 표기 하드웨어 주소. `0c2d.0765.99f3`, `0cae.21dd.0001`
 * IPV4 의 4옥텟 표기와 모양이 다르므로(4자리 16진 3덩이) 서로 충돌하지 않는다.
 * 이 토큰이 있으면 visitor 가 "되돌리기 어려운 MAC 처럼 생겼는가" 를
 * 눈으로 판단할 필요가 없다. */
MACDOTTED : HEX4 DOT HEX4 DOT HEX4 ;
fragment DOT  : '.' ;
fragment HEX4 : [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] ;

/* 주소도 패밀리별 토큰으로 분리한다. `family` 판정이 파스 트리에서 끝나므로
 * visitor 는 ADDR4/ADDR6 의 타입만 보고 inet/inet6 를 정한다.
 * prefix 길이(`/24`)는 주소 토큰에 포함되며, 분해는 순수 문자열 작업이다. */
ADDR4 : IPV4 ( SLASH [0-9]+ )? ;
ADDR6 : IPV6 ( SLASH [0-9]+ )? ;

/* 키워드류는 IFNAME 보다 먼저 선언한다. */
DEFAULT  : 'default' ;
/* 기본 경로의 주소 표기. ADDR4 와 길이가 같으므로 반드시 먼저 선언한다. */
DEFAULTADDR : '0.0.0.0/0' | '::/0' ;
LINK     : 'link' ( SLASH [a-zA-Z]+ )? ;
INET     : 'inet' ;
INET6    : 'inet6' ;
LIFETIME : 'valid_lft' | 'preferred_lft' ;
UP       : 'UP' ;
DOWN     : 'DOWN' ;
UNKNOWN  : 'UNKNOWN' ;

/* `ip a` 헤더의 `<BROADCAST,MULTICAST,UP,LOWER_UP>`.
 * 전용 토큰으로 두면 visitor 가 텍스트 앞뒤 문자를 보고 플래그인지
 * 판단할 필요 없이 타입만 확인한다. */
FLAGS : '<' ~[<>\r\n]* '>' ;

/* ------------------------------------------------------------------ *
 *  키워드 토큰
 *
 *  아래 토큰들은 모두 `elem`(~NEWLINE) 으로 위임돼 있던 단어들이다.
 *  전용 토큰으로 승격하면 visitor 가 `elem.getStart().getType()` 만 보고
 *  "이 조각이 무엇인지" 판단하고 옆 조각을 값으로 취할 수 있다.
 *  (문자열을 눈으로 훑는 휴리스틱이 필요 없어진다.)
 *
 *  IFNAME 과 문자집합/길이가 같은 단어들이므로 반드시 IFNAME 보다
 *  먼저 선언해야 최장일치 동점에서 키워드가 이긴다.
 * ------------------------------------------------------------------ */

/* `ip a` 헤더의 key value 쌍 */
MTU     : 'mtu' ;
QDISC   : 'qdisc' ;
STATE   : 'state' ;
QLEN    : 'qlen' ;
GROUP   : 'group' ;
NETNSID : 'link-netnsid' ;

/* `link/ether ... brd ...` 줄과 주소 줄의 key */
BRD         : 'brd' ;
SCOPE       : 'scope' ;
PEER        : 'peer' ;
PROMISCUITY : 'promiscuity' ;

/* `scope global eth1.131` 에서 인터페이스명(dev)이 붙는 scope 값은
 * global 뿐이다. host/link/nowhere 는 dev 를 출력하지 않는다.
 * 이 사실을 토큰으로 선언하면 visitor 가 "다음 IFNAME 이 dev 인가" 를
 * 추측하지 않아도 된다. */
GLOBAL    : 'global' ;
SCOPEWORD : 'host' | 'link' | 'nowhere' | 'site' ;

/* 주소에 붙는 플래그. IFNAME 과 문자집합이 같으므로 먼저 선언해야
 * 이들이 인터페이스명으로 오인되지 않는다.
 *   `inet6 ... scope global dynamic mngtmpaddr noprefixroute`
 * 여기서 dev 는 없고 플래그만 온다. */
ADDRFLAG : 'dynamic' | 'mngtmpaddr' | 'noprefixroute' | 'temporary'
         | 'secondary' | 'deprecated' | 'nodad' | 'stable-privacy' ;

/* `ip route show` 의 key */
VIA      : 'via' ;
DEV      : 'dev' ;
PROTO    : 'proto' ;
METRIC   : 'metric' ;
SRC      : 'src' ;
TABLE    : 'table' ;
LINKDOWN : 'linkdown' ;

/* `ip route` 의 경로 타입 키워드. 목적지 앞에 온다.
 *
 * 이 목록이 곧 "라우트 줄의 첫 토큰이 될 수 있는 단어" 다. IFNAME 을 여기에
 * 두는 대신 전용 토큰으로 좁히면, 라우트가 아닌 텍스트가 라우트로 오인되지
 * 않는다. IFNAME 과 문자집합이 같으므로 반드시 IFNAME 보다 먼저 선언한다. */
ROUTETYPE : 'blackhole' | 'unreachable' | 'prohibit' | 'throw'
          | 'broadcast' | 'local' | 'multicast' | 'anycast' | 'nat' ;

/* 이웃(ARP) 테이블 어휘 */
LLADDR : 'lladdr' ;
NUD    : 'REACHABLE' | 'STALE' | 'DELAY' | 'FAILED' | 'INCOMPLETE'
       | 'NOARP' | 'PERMANENT' | 'NONE' ;
/* 주소 앞에 오는 패밀리 단어 (Cisco/FRR 표기) */
INETWORD : 'Internet' | 'I' | 'ip' | 'ipv6' | 'IPv6' | 'Incomplete' ;
/* 하드웨어 주소 종류 컬럼 (`ARPA`/`Dynamic` 등). visitor 가 바로 분류한다. */
ARPTYPE  : 'ARPA' | 'SNAP' | 'PROBE' | 'STATIC' | 'Dynamic' | 'dynamic' ;

IFNAME : [a-zA-Z_] [a-zA-Z0-9_.@-]* ;

COLON : ':' ;
SLASH : '/' ;
/* ARP 인터페이스 목록의 열 구분자 (`Vlan8, Ethernet2`). WORD 가 쉼표를
 * 삼키면 인터페이스명과 붙어 한 덩어리가 되므로 전용 토큰으로 뗀다. */
COMMA : ',' ;

/* 마지막 catch-all.
 * ':' 와 ',' 를 제외해야 `1:` 이 INDEX+COLON 으로, `eth1.131@eth1:` 이
 * IFNAME+COLON 으로, `Vlan8, Ethernet2` 가 IFNAME COMMA IFNAME 으로
 * 쪼개진다. IP/MAC 은 전용 토큰이 처리한다. */
WORD : ~[ \t\r\n:,]+ ;

fragment IPV4  : OCTET '.' OCTET '.' OCTET '.' OCTET ;
fragment OCTET : [0-9]+ ;

/* `::1`, `fe80::42:2fff:fe7e:d600` 같은 축약형/전체형을 모두 받는다. */
fragment IPV6
    : [0-9a-fA-F]* COLON COLON [0-9a-fA-F:]*
    | [0-9a-fA-F]* COLON [0-9a-fA-F]+ COLON [0-9a-fA-F:]*
    ;
