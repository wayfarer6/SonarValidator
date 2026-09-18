grammar LinuxVM;

// --- 파서 룰 (Parser Rules) ---

result : item* EOF ;

item : NUMBER ':' ID ':'                // NamePattern (예: 2: en3:)
     | 'inet' IP_ADDR ('/' NUMBER)?     // IpPattern (예: inet 192.168.1.100 / 24)
     | .                                // Ignore (위 패턴 아니면 버림)
     ;


// --- 렉서 룰 (Lexer Rules) ---

INET    : 'inet' ;

// 수정됨: 뒤에 있던 ('/' [0-9]+)? 를 제거하고 순수하게 192.168.0.1 형태만 매칭합니다.
IP_ADDR : [0-9]+ '.' [0-9]+ '.' [0-9]+ '.' [0-9]+ ; 

NUMBER  : [0-9]+ ;
ID      : [a-zA-Z0-9_]+ ; // en3, lo, mtu 등

WS      : [ \t\r\n]+ -> skip ; // 공백, 탭, 줄바꿈 무시

// 매칭되지 않는 찌꺼기 문자열(<, >, 등)을 하나씩 삼키는 마법의 룰
ANY_CHAR : . ;