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

/* 코드(+ selected '*' / backup '&' 등) 뒤에 목적지가 오는 실제 경로 줄.
 *
 * 꼬리를 elem* 로 통째로 넘기면 "via 다음이 게이트웨이다" 라는 구조가
 * 파스 트리에서 사라진다. 그러면 visitor 가 옆 토큰을 눈으로 세는 수밖에
 * 없고, 한 칸만 어긋나도 interface_name 이 "via" 가 된다.
 * 아래처럼 꼬리를 의미 단위로 승격하면 구조가 파스 트리에 남는다. */
routeLine     : routeCode+ destination metricBracket? ( COMMA? routeTail )* NEWLINE ;
routeCode     : ROUTECODE ;
/* `default` 키워드와 `0.0.0.0/0` 주소는 벤더 표기 차이일 뿐 같은 뜻이다.
 * 전용 토큰으로 확정하면 visitor 가 "0.0.0.0/0 인가" 를 비교하지 않는다. */
destination   : DEFAULT | DEFAULTADDR | ADDR ;
/* `[110/200]` 거리/메트릭. 값 분해는 visitor 가 한다. */
metricBracket : METRICBRACKET ;

/* 라우트 줄 꼬리. 한 항목이 한 의미를 갖도록 분리한다.
 *   `is directly connected, eth0`
 *   `via 10.99.10.1, eth0`
 *   `weight 1`
 *   `00:17:20`                                                    */
routeTail         : IS? directlyConnected | viaHop | weightField | routeExtras ;
directlyConnected : DIRECTLY CONNECTED COMMA? interfaceTail? ;
viaHop            : VIA routeValue COMMA? interfaceTail? ;
interfaceTail     : ifname ;
weightField       : WEIGHT routeValue ;
/* 벤더가 덧붙이는 나머지 조각(업타임 등). VIA/WEIGHT 같은 키워드는
 * 위 대안들이 먼저 가져가므로 삼켜지지 않는다. */
routeExtras       : WORD | ADDR | ifname ;
/* VIA 뒤의 게이트웨이. 주소로 확정되지 않는 표기도 받는다. */
routeValue        : ADDR | WORD ;

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
detailAttr  : keyToken elem* NEWLINE ;

/* 상세 출력의 키 단어. 전용 토큰과 일반 단어를 모두 받아
 * 토큰 선언 순서가 규칙 매칭을 깨지 않게 한다. */
keyToken    : ATTRWORD | METRICBRACKET | VIA | DIRECTLY | CONNECTED | WEIGHT ;

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
 *
 * ⚠️ 2글자 코드 `IA` 를 `ROUTELETTER [0-9]?` 만으로는 잡지 못한다.
 *    `ROUTELETTER [0-9]?` 는 1글자(+선택적 숫자)라서 `E1`/`N1`/`L2` 는 되지만
 *    `IA` 는 **2글자 ATTRWORD 에 최장일치로 진다.** 그러면
 *    `O IA 10.10.128.0/21 ...` 에서 routeCode+ 가 `O` 뒤에 `IA` 를 받지 못해
 *    destination(ADDR) 앞에서 매칭이 깨지고 routeLine 전체가 genericLine 으로
 *    떨어져 **조용히 0건**이 된다. (실측: Cisco OSPF inter-area 경로가 전부 누락)
 *    ROUTECODE 는 ATTRWORD 보다 먼저 선언되므로, 2글자로 잡아 주면 동점에서 이긴다.
 */
ROUTECODE : ( ROUTELETTER [0-9]? | 'IA' | 'ia' ) [*>&]*
          | [*>&]+ ;

fragment ROUTELETTER
    : 'K' | 'C' | 'S' | 'R' | 'O' | 'I' | 'B' | 'E' | 'N' | 'A' | 'D'
    | 'L' | 'T' | 'P' | 'U' | 'H' | 'G' | 'M' | 'm' | 'o' | 'a' | 'l'
    | 'i' | 'p' | 'n' | 's' | 'u'
    ;

DEFAULT : 'default' ;
/* 기본 경로의 주소 표기. ADDR 과 길이가 같으므로 반드시 먼저 선언해야
 * 최장일치 동점에서 이긴다. */
DEFAULTADDR : '0.0.0.0/0' | '::/0' ;

ADDR : IPV4 ( SLASH [0-9]+ )? | IPV6 ( SLASH [0-9]+ )? ;

CODES      : 'Codes:' ;
GATEWAY    : 'Gateway' ;
IS         : 'is' ;
VARIABLY   : 'variably' ;
/* `subnetted, 2 subnets, 2 masks` → 쉼표를 토큰이 흡수해
 * subnetSummary 가 실제로 매칭되게 한다. */
SUBMITTED  : 'subnetted' ','? ;
INTERFACE  : 'Interface' | 'interface' ;
UNASSIGNED : 'unassigned' ;
METHOD     : 'YES' | 'NO' | 'NVRAM' | 'unset' | 'manual' ;
STATUSWORD : 'up' | 'down' | 'administratively' | 'deleted' ;

/* `show ip route` 의 키워드. 라우트 줄 꼬리의 "항목 이름"이므로
 * 아래 routeTail 규칙에서 단독으로 쓰인다.
 *
 * IFNAME/ATTRWORD 와 문자집합이 겹치므로 반드시 그들보다 먼저 선언해야
 * 한다. 뒤에 선언하면 ANTLR 의 최장일치 동점 규칙에서 먼저 선언된 IFNAME
 * 이 이겨 `via` 가 인터페이스명으로 토큰화되고, visitor 는 via 를 놓친다. */
VIA       : 'via' ;
DIRECTLY  : 'directly' ;
CONNECTED : 'connected' ;
WEIGHT    : 'weight' ;
/* 거리/메트릭 묶음 `[110/200]`. 통째로 토큰화하면 분해만 하면 된다. */
METRICBRACKET : '[' ~[\]\r\n]* ']' ;

IFNAME   : [a-zA-Z] [a-zA-Z0-9/._:@-]* ;
/* Ethernet1 / GigabitEthernet1/0/1 처럼 숫자가 섞인 인터페이스명 */
PORTNAME : [a-zA-Z]+ [0-9]+ ( '/' [0-9]+ )* ;
IDENT    : [a-zA-Z_] [a-zA-Z0-9_.-]* ;

ATTRWORD : [a-zA-Z_] [a-zA-Z0-9_.:-]* ;

COLON : ':' ;
SLASH : '/' ;
/* 라우트 줄의 열 구분자. WORD 가 쉼표를 삼키면 `eth0,` 이 한 덩어리가 되어
 * 인터페이스명과 구분자가 파스 트리에서 붙어 버린다. 그래서 쉼표는 전용
 * 토큰으로 떼어 두고 WORD 에서는 제외한다. */
COMMA : ',' ;

/* ':' 와 ',' 를 제외해 `eth0 is up, line protocol is up` 같은 줄에서
 * ifaceHeader(IFNAME ...) 매칭이 유지되고, 쉼표가 열 구분자로 남는다. */
WORD : ~[ \t\r\n:,]+ ;

fragment IPV4  : OCTET '.' OCTET '.' OCTET '.' OCTET ;
fragment OCTET : [0-9]+ ;

fragment IPV6
    : [0-9a-fA-F]* COLON COLON [0-9a-fA-F:]*
    | [0-9a-fA-F]* COLON [0-9a-fA-F]+ COLON [0-9a-fA-F:]*
    ;
