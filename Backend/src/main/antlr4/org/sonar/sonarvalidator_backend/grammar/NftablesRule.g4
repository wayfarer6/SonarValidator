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
 *
 *  ---------------------------------------------------------------------
 *  규칙 어휘를 전용 토큰으로 승격한 이유
 *  ---------------------------------------------------------------------
 *  nft 규칙은 `ip saddr 1.2.3.0/24 oifname "eth0" counter log prefix "X" drop`
 *  처럼 키/값/수식어/동작이 불규칙하게 섞인다. 예전에는 visitor 가 이 줄을
 *  다시 단어로 쪼개 "이 단어가 ip 인가 saddr 인가" 를 문자열로 물었다.
 *
 *  이제는
 *    PREFIX    : ip ip6 tcp udp ct meta icmp icmpv6   (두 토큰 키의 앞자)
 *    SUBKEY    : saddr daddr state dport sport ...    (두 토큰 키의 뒷자)
 *    IFACEKEY  : iif oif iifname oifname              (단독 키)
 *    RULEKEY   : handle comment prefix                (단독 키)
 *    MODIFIER  : counter log                          (수식어)
 *    METRICKEY : packets bytes                        (카운터 값)
 *    ACTION    : accept drop reject ...               (동작)
 *  로 분류되므로 visitor 는 cursor.type() 만 보고 분기한다.
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
 * 세미콜론이 2개 이상 올 수 있으므로 elem* 로 전부 흘려보낸다.
 * 값(`filter`, `accept`)이 ACTION/PREFIX 등으로 토큰화될 수 있으므로
 * 여기서는 종류를 묻지 않고 (키, 값) 짝만 만든다. */
chainAttr : ATTRKEY elem* NEWLINE ;
ATTRKEY   : 'type' | 'hook' | 'priority' | 'policy' | 'device' | 'comment' ;

/* family/table/chain 이름.
 * 'ip'(PREFIX), 'drop'(ACTION), 'policy'(ATTRKEY), 'log'(MODIFIER) 처럼
 * 다른 키워드 토큰과 이름이 겹치는 실제 체인/테이블명이 존재하므로
 * 전부 허용한다. DASHED_IDENT 를 앞에 둬 `my-chain` 이 쪼개지지 않게 한다. */
nameToken : DASHED_IDENT | IDENT | NUMBER
          | PREFIX | SUBKEY | IFACEKEY | RULEKEY | MODIFIER | METRICKEY | ACTION
          | TABLE | CHAIN | ATTRKEY ;

/* ----------------------------- rule ------------------------------ */

/* `ip saddr 1.2.3.0/24 ip daddr 5.6.7.0/24 drop`
 *   `oifname "eth0" masquerade`
 *   `ip saddr 1.2.3.0/24 counter packets 5 bytes 300 log prefix "NFT_DROP: " drop`
 * 매칭 키워드/값/수식어가 불규칙하게 섞이므로 평탄한 토큰 목록으로 받고,
 * (키, 값) 짝과 action 해석은 visitor 가 담당한다. 알 수 없는 조각은
 * RULEWORD 가 흡수해 파싱 실패로 정보가 통째로 사라지는 것을 막는다.
 *                                                                        */
ruleLine : rulePiece+ NEWLINE ;
rulePiece : PREFIX | SUBKEY | IFACEKEY | RULEKEY | MODIFIER | METRICKEY | ACTION
          | HASH | QUOTED | ADDR | NUMBER | DASHED_IDENT | IDENT | RULEWORD ;

/* 두 토큰이 하나의 키를 이루는 접두어. (`ip saddr`, `tcp dport`, `ct state`) */
PREFIX    : 'ip' | 'ip6' | 'tcp' | 'udp' | 'ct' | 'meta' | 'icmp' | 'icmpv6' ;
/* 접두어 뒤에 붙는 하위 키. */
SUBKEY    : 'saddr' | 'daddr' | 'state' | 'dport' | 'sport' | 'protocol'
          | 'type' | 'code' | 'l4proto' | 'mark' ;
/* 단독으로 키가 되는 인터페이스 매칭. */
IFACEKEY  : 'iif' | 'oif' | 'iifname' | 'oifname' ;
/* 단독으로 키가 되는 나머지. (`handle 5`, `comment "x"`, `log prefix "x"`) */
RULEKEY   : 'handle' | 'comment' | 'prefix' ;
/* 규칙 수식어. */
MODIFIER  : 'counter' | 'log' ;
/* 수식어 뒤에 숫자가 따라오는 카운터 값. (`counter packets 5 bytes 300`) */
METRICKEY : 'packets' | 'bytes' ;
/* 규칙 동작. `jump`/`goto` 는 다음 조각이 대상 체인명이다. */
ACTION    : 'accept' | 'drop' | 'reject' | 'return' | 'jump' | 'goto'
          | 'masquerade' | 'redirect' | 'dnat' | 'snat' | 'set' ;

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
/* `nft -a` 가 붙이는 핸들 주석 표시. 전용 토큰이어야 RULEWORD 가
 * `#` 를 삼키지 않고, visitor 가 핸들 조각을 알아볼 수 있다. */
HASH        : '#' ;

ADDR         : IPV4 ( SLASH [0-9]+ )? | IPV6 ( SLASH [0-9]+ )? ;
NUMBER       : [0-9]+ ;
DASHED_IDENT : [a-zA-Z_] [a-zA-Z0-9_.-]* '-' [a-zA-Z0-9_.-]+ ;
QUOTED       : '"' ~["\r\n]* '"' ;
IDENT        : [a-zA-Z_] [a-zA-Z0-9_]* ;
/* 규칙 조각의 catch-all.
 * '{' '}' ';' '#' 는 블록 경계/구분자/주석이라 제외하고,
 * '"' 도 제외해 QUOTED 가 `"eth0"` 을 온전히 잡게 한다.
 * 위의 키워드 토큰들과 문자집합이 겹치므로 반드시 마지막에 선언한다. */
RULEWORD     : ~[ \t\r\n;{}"#]+ ;

fragment IPV4  : OCTET '.' OCTET '.' OCTET '.' OCTET ;
fragment OCTET : [0-9]+ ;

fragment IPV6
    : [0-9a-fA-F]* COLON COLON [0-9a-fA-F:]*
    | [0-9a-fA-F]* COLON [0-9a-fA-F]+ COLON [0-9a-fA-F:]*
    ;
