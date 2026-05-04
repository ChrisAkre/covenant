grammar JsonPath;

jsonpath : root_identifier (segment)* EOF ;

root_identifier : '$' ;
current_node_identifier : '@' ;

segment
    : child_segment
    | descendant_segment
    ;

child_segment
    : bracket_specifier
    | dot_specifier
    ;

descendant_segment
    : '..' (name_selector | wildcard_selector | bracket_specifier)
    ;

dot_specifier
    : '.' (name_selector | wildcard_selector)
    ;

bracket_specifier
    : '[' selector (',' selector)* ']'
    ;

selector
    : name_selector
    | wildcard_selector
    | slice_selector
    | index_selector
    | filter_selector
    ;

name_selector
    : STRING_LITERAL
    | IDENTIFIER
    ;

wildcard_selector
    : '*'
    ;

index_selector
    : INTEGER
    ;

slice_selector
    : (INTEGER)? ':' (INTEGER)? (':' (INTEGER)?)?
    ;

filter_selector
    : '?(' logical_expr ')'
    ;

logical_expr
    : logical_expr '||' logical_expr
    | logical_expr '&&' logical_expr
    | '!' logical_expr
    | comparison_expr
    | test_expr
    | '(' logical_expr ')'
    ;

test_expr
    : singular_query
    | function_expr
    ;

comparison_expr
    : comparable cmp_op comparable
    ;

cmp_op
    : '==' | '!=' | '<' | '<=' | '>' | '>='
    ;

comparable
    : literal
    | singular_query
    | function_expr
    ;

literal
    : NUMBER
    | STRING_LITERAL
    | 'true'
    | 'false'
    | 'null'
    ;

singular_query
    : (root_identifier | current_node_identifier) (singular_segment)*
    ;

singular_segment
    : '.' (name_selector)
    | '[' (name_selector | index_selector) ']'
    ;

function_expr
    : IDENTIFIER '(' (function_arg (',' function_arg)*)? ')'
    ;

function_arg
    : literal
    | filter_selector
    | logical_expr
    | singular_query
    | function_expr
    ;

// Lexer Rules
STRING_LITERAL : '\'' (~['\\] | '\\' .)* '\'' | '"' (~["\\] | '\\' .)* '"' ;
NUMBER : '-'? [0-9]+ ('.' [0-9]+)? ([eE] [+-]? [0-9]+)? ;
INTEGER : '-'? [0-9]+ ;
IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_-]* ;

WS : [ \t\r\n]+ -> skip ;
