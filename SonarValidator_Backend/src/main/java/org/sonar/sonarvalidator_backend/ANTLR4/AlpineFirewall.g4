grammar AlpineFirewall;

/*
item*: item 규칙이 0번 이상 반복될 수 있음을 의미합니다.

EOF: 파일의 끝(End Of File)을 의미합니다. 더 이상 읽을 데이터가 없어야 규칙이 종료됩니다.

 */

result : item* EOF;

item : NUMBER ':' ID ':'
    | 'inet' IP_ADDR ('/' NUMBER)?
    | .
    ;

// 렉서 룰

INET    : 'inet' ;
IP_ADDR : [0-9]+ '.' [0-9]+ '.' [0-9]+ '.' [0-9]+ ;
NUMBER  : [0-9]+;
ID      : [a-zA-Z0-9_]+; // ens,lo,mtu 등
 