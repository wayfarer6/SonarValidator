grammar NftablesRule;

/* =========================================================================
 *  NftablesRule — nft(8) 룰셋 출력 파서
 *
 *  진입 규칙
 *    rulesetDocument : `nft list ruleset`
 *    chainDocument   : `nft -a list chain <family> <table> <chain>`
 *
 *  출력 구조
 *      table inet filter {          <- tableBlock
 *          chain forward {          <- chainBlock
 *              type filter hook forward priority 0; policy drop;
 *              ip saddr 1.2.3.0/24 ip daddr 5.6.7.0/24 drop   <- rule
 *          }
 *      }
 *
 *  중괄호 중첩은 규칙(tableBlock/chainBlock)으로 표현한다.
 * ========================================================================= */

rulesetDocument : rulesetItem* EOF ;
chainDocument   : chainItem* EOF ;

rulesetItem : tableBlock | genericLine | blank ;
chainItem   : chainAttr | ruleLine | genericLine | blank ;

/* ----------------------------- table ----------------------------- */

tableBlock : TABLE familyBlock tableName OPEN_BRACE NEWLINE bodyBlock* CLOSE_BRACE NEWLINE? ;
bodyBlock  : chainBlock | genericLine | blank ;
familyBlock : nameToken ;
tableName   : nameToken ;

/* ----------------------------- chain ----------------------------- */

chainBlock : CHAIN chainName OPEN_BRACE NEWLINE chainBody* CLOSE_BRACE NEWLINE? ;
chainBody  : chainAttr | ruleLine | genericLine | blank ;
chainName  : nameToken ;

/* `type filter hook forward priority filter; policy accept;`
 * 세미콜론이 2개 이상 올 수 있으므로 elem* 로 전부 흘려보낸다. */
chainAttr : ATTRKEY elem* NEWLINE ;
ATTRKEY   : 'type' | 'hook' | 'priority' | 'policy' | 'device' | 'comment' ;

/* family/table/chain 이름.
 * 'ip'(MATCHKEY), 'type'(ATTRKEY), 'log'(MATCHKEY) 처럼 다른 키워드 토큰과
 * 겹치는 이름이 실제로 존재하므로 전부 허용한다. DASHED_IDENT 를 앞에 둬
 * `my-chain` 같은 이름이 IDENT 로 쪼개지지 않게 한다. */
nameToken : DASHED_IDENT | IDENT | MATCHKEY | ACTION | ATTRKEY | TABLE | CHAIN | NUMBER ;

/* ----------------------------- rule ------------------------------ */

/* `ip saddr 1.2.3.0/24 ip daddr 5.6.7.0/24 drop`
 *   `oifname "eth0" masquerade`
 *   `ip saddr 1.2.3.0/24 counter packets 5 bytes 300 log prefix "NFT_DROP: " drop`
 * 매칭 키워드/값/수식어가 불규칙하게 섞이므로 평탄한 토큰 목록으로 받고,
 * (키, 값) 짝과 action 해석은 visitor 가 담당한다. 알 수 없는 조각은
 * RULEWORD 가 흡수해 파싱 실패로 정보가 통째로 사라지는 것을 막는다.
 *                                                                        */
ruleLine : rulePiece+ NEWLINE ;
rulePiece : MATCHKEY | ACTION | QUOTED | ADDR | NUMBER | DASHED_IDENT | IDENT | RULEWORD ;
MATCHKEY     : 'ip' | 'ip6' | 'ct' | 'tcp' | 'udp' | 'icmp' | 'iif' | 'oif'
             | 'iifname' | 'oifname' | 'saddr' | 'daddr' | 'state' | 'dport' | 'sport'
             | 'protocol' | 'meta' | 'mark' | 'log' | 'counter' | 'prefix';
ACTION       : 'accept' | 'drop' | 'reject' | 'return' | 'jump' | 'goto'
             | 'masquerade' | 'redirect' | 'dnat' | 'snat' | 'set';

/* ------------------------------ 공통 ----------------------------- */

genericLine : elem+ NEWLINE ;
blank       : NEWLINE ;

/* 중괄호는 tableBlock/chainBlock 의 경계 표시이므로 elem 에서 제외해야
 * 닫는 '}' 가 안쪽 블록의 genericLine 으로 흡수되어 중첩이 깨지지 않는다. */
elem        : ~(NEWLINE | OPEN_BRACE | CLOSE_BRACE) ;

/* ------------------------------ 토큰 ----------------------------- */

NEWLINE : '\r'? '\n' ;
WS      : [ \t]+ -> skip ;

TABLE : 'table' ;
CHAIN : 'chain' ;

OPEN_BRACE  : '{' ;
CLOSE_BRACE : '}' ;
SEMICOLON   : ';' ;
SLASH       : '/' ;
COLON       : ':' ;

ADDR         : IPV4 ( SLASH [0-9]+ )? | IPV6 ( SLASH [0-9]+ )? ;
NUMBER       : [0-9]+ ;
DASHED_IDENT : [a-zA-Z_] [a-zA-Z0-9_.-]* '-' [a-zA-Z0-9_.-]+ ;
QUOTED       : '"' ~["\r\n]* '"' ;
IDENT        : [a-zA-Z_] [a-zA-Z0-9_]* ;
/* 규칙 조각의 catch-all.
 * '{' '}' ';' 는 블록 경계/구분자라 제외하고,
 * '"' 도 제외해 QUOTED 가 `"eth0"` 을 온전히 잡게 한다. */
RULEWORD     : ~[ \t\r\n;{}"]+ ;

fragment IPV4  : OCTET '.' OCTET '.' OCTET '.' OCTET ;
fragment OCTET : [0-9]+ ;

fragment IPV6
    : [0-9a-fA-F]* COLON COLON [0-9a-fA-F:]*
    | [0-9a-fA-F]* COLON [0-9a-fA-F]+ COLON [0-9a-fA-F:]*
    ;
