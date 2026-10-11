grammar OvsTopology;

/* =========================================================================
 *  OvsTopology — Open vSwitch CLI 출력 파서
 *
 *  진입 규칙
 *    showDocument : `ovs-vsctl show`               (브리지/포트/인터페이스 계층)
 *    listDocument : `ovs-vsctl list port`          (포트 속성 레코드)
 *    flowDocument : `ovs-ofctl dump-flows <bridge>` (OpenFlow 규칙)
 *    showVlanDocument : `ovs-vsctl --format=json ...` 대체용 텍스트 확인용
 *
 *  `ovs-vsctl show` 는 들여쓰기로 계층을 표현한다.
 *      Bridge "br0"
 *          Port "eth0"
 *              tag: 141
 *              trunks: [141]
 *              Interface "eth0"
 *                  type: internal
 *  문법에서 들여쓰기 깊이를 강제하면 탭/스페이스 혼용에서 깨진다.
 *  따라서 WS 는 skip 하되, 각 줄 첫 토큰의 charPositionInLine(=들여쓰기 컬럼)을
 *  visitor 가 읽어 계층을 복원한다. WS 를 skip 해도 토큰 위치는 보존된다.
 * ========================================================================= */

showDocument     : showItem* EOF ;
listDocument     : listItem* EOF ;
flowDocument     : flowItem* EOF ;
showVlanDocument : showItem* EOF ;

/* ----------------------- `ovs-vsctl show` ------------------------ */

showItem : bridgeLine | portLine | ifaceLine | attrLine | genericLine | blank ;

bridgeLine : BRIDGE nameToken elem* NEWLINE ;
portLine   : PORT nameToken elem* NEWLINE ;
ifaceLine  : INTERFACE nameToken elem* NEWLINE ;

/* `tag: 141`, `trunks: [141]`, `type: internal`, `ovs_version: "2.17.0"` */
attrLine : ATTRWORD COLON elem* NEWLINE ;

/* 브리지/포트/인터페이스 이름.
 * `br0` 처럼 영문+숫자는 ATTRWORD 로 토큰화되므로 세 토큰을 모두 허용한다.
 * (ATTRWORD 가 WORD 보다 먼저 선언되어 최장일치 동점에서 이긴다.) */
nameToken : QUOTED | ATTRWORD | WORD ;

/* -------------------- `ovs-vsctl list port` ---------------------- */
/*  레코드는 속성 줄의 연속이며 `--` 로 구분된다.
 *      _uuid               : 3945208f-...
 *      name                : "eth1"
 *      tag                 : 141
 *      trunks              : []
 *      --
 */

listItem : listRecord | recordSep | genericLine | blank ;

listRecord : ATTRWORD COLON elem* NEWLINE ;
recordSep  : DASHES NEWLINE ;

/* ------------------ `ovs-ofctl dump-flows` ----------------------- */
/*      cookie=0x0, duration=1234.5s, table=0, n_packets=0, ..., actions=drop
 */

flowItem : flowLine | genericLine | blank ;
flowLine : FLOWTOKEN+ elem* NEWLINE ;

/* ------------------------------ 공통 ----------------------------- */

genericLine : elem+ NEWLINE ;
blank       : NEWLINE ;
elem        : ~NEWLINE ;

/* ------------------------------ 토큰 ----------------------------- */

NEWLINE : '\r'? '\n' ;
WS      : [ \t]+ -> skip ;

BRIDGE    : 'Bridge' ;
PORT      : 'Port' ;
INTERFACE : 'Interface' ;

/* `cookie=0x0`, `n_packets=0`, `actions=drop` 같은 key=value 조각 */
FLOWTOKEN : [a-zA-Z_]+ '=' [^ \t\r\n,]+ ;

ATTRWORD : [a-zA-Z_] [a-zA-Z0-9_.-]* ;

QUOTED : '"' ~["\r\n]* '"' ;
DASHES : '-'+ ;

/* ':' 를 제외해야 `tag: 141` 이 ATTRWORD+COLON+값 으로 쪼개져 attrLine 이 매칭된다. */
WORD  : ~[ \t\r\n:]+ ;
COLON : ':' ;
