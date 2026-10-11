grammar OvsTopology;

/* =========================================================================
 *  OvsTopology — Open vSwitch CLI 출력 파서
 *
 *  진입 규칙
 *    showDocument     : `ovs-vsctl show`               (브리지/포트/인터페이스 계층)
 *    listDocument     : `ovs-vsctl list port`          (포트 속성 레코드)
 *    flowDocument     : `ovs-ofctl dump-flows <bridge>` (OpenFlow 규칙)
 *    showVlanDocument : 텍스트 형식 VLAN 확인용
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
 *
 *  ---------------------------------------------------------------------
 *  속성 값을 규칙으로 구조화한 이유
 *  ---------------------------------------------------------------------
 *  `trunks: [141, 142]` 는 VLAN 번호 목록, `tag: 141` 은 단일 번호,
 *  `name: "br0"` 은 문자열이다. 예전에는 대괄호와 쉼표를 WORD 가 통째로
 *  삼켜 `[141,` 같은 토큰이 만들어졌고, visitor 가 문자열에서 숫자를
 *  긁어내야 했다.
 *
 *  이제 attrValue 규칙이
 *    vlanList    : LBRACKET ( NUMBER ( COMMA NUMBER )* )? RBRACKET
 *    scalarValue : valueToken+
 *    valueToken  : NUMBER | QUOTED | ATTRWORD | TAG | TRUNKS | ... | WORD
 *  을 구분하므로, VLAN 번호는 NUMBER 토큰 목록 그대로 나오고
 *  단일 값은 unquote 만 하면 된다.
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
attrLine : attrKey COLON attrValue NEWLINE ;

/* 속성 값. 대괄호 목록이면 VLAN 번호 목록, 아니면 스칼라다.
 * 값이 아예 없을 수 있으므로(예: `flood_vlans:`) optional 로 둔다. */
attrValue : vlanList | scalarValue? ;

/* `[141]`, `[141, 142]`, `[]` */
vlanList    : LBRACKET ( NUMBER ( COMMA NUMBER )* )? RBRACKET ;

/* `internal`, `"eth0"`, `2.17.0`, `true`, `normal`
 *
 * 이름이 키워드와 겹치는 값(`name: "trunks"`)이 실제로 있으므로
 * 승격된 키워드 토큰도 값으로 허용한다. */
scalarValue : valueToken+ ;
valueToken  : NUMBER | QUOTED | ATTRWORD | TAG | TRUNKS | VLANMODE | TYPEWORD
            | NAMEWORD | FAILMODE | STPENABLE | WORD ;

/* 속성 키. 승격된 키워드 토큰과 일반 단어를 모두 받아
 * 토큰 선언 순서가 규칙 매칭을 깨지 않게 한다. */
attrKey : ATTRWORD | TAG | TRUNKS | VLANMODE | TYPEWORD | NAMEWORD | FAILMODE | STPENABLE ;

/* 브리지/포트/인터페이스 이름.
 * `br0` 처럼 영문+숫자는 ATTRWORD 로 토큰화되므로 여러 토큰을 허용한다. */
nameToken : QUOTED | ATTRWORD | WORD ;

/* -------------------- `ovs-vsctl list port` ---------------------- */
/*  레코드는 속성 줄의 연속이며 `--` 또는 빈 줄로 구분된다.
 *      _uuid               : 3945208f-...
 *      name                : "eth1"
 *      tag                 : 141
 *      trunks              : []
 *      --
 */

listItem : listRecord | recordSep | genericLine | blank ;

listRecord : attrKey COLON attrValue NEWLINE ;
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

/* `ovs-vsctl` 가 실제로 쓰는 속성 키.
 *
 * attrLine 이 attrKey COLON attrValue 형태이므로 `tag:`, `trunks:` 같은 키는
 * 이미 "키" 로 분류되어 있다. 여기서 더 나아가 어떤 키인지까지 토큰으로
 * 확정해 두면 visitor 는 타입만 보고 분기한다. */
TAG       : 'tag' ;
TRUNKS    : 'trunks' ;
VLANMODE  : 'vlan_mode' ;
TYPEWORD  : 'type' ;
NAMEWORD  : 'name' ;
FAILMODE  : 'fail_mode' ;
STPENABLE : 'stp_enable' ;

QUOTED : '"' ~["\r\n]* '"' ;
DASHES : '-'+ ;

/* 대괄호/쉼표/숫자.
 *
 * 대괄호와 쉼표를 WORD 가 삼키면 vlanList 가 절대 매칭되지 않는다.
 * NUMBER 와 WORD 의 일치 길이가 같을 때(예: `141`)는 선언 순서가 승자를
 * 정하므로 NUMBER 를 WORD 보다 먼저 둔다. */
LBRACKET : '[' ;
RBRACKET : ']' ;
COMMA    : ',' ;
NUMBER   : [0-9]+ ;

/* 남은 catch-all. `_uuid` 의 `3945208f-...`, `ovs_version` 의 `2.17.0` 등.
 * ':' 를 제외해야 `tag: 141` 이 attrKey+COLON+값 으로 쪼개진다.
 * 대괄호/쉼표/괄호도 제외해 위의 전용 토큰이 살아나게 한다.
 * (ANTLR 문자집합 안에서는 '[' 를 이스케이프하지 않는다.) */
WORD  : ~[ \t\r\n:,()[\]]+ ;
COLON : ':' ;