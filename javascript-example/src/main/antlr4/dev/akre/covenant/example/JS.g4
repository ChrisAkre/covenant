grammar JS;

program: arrowFunction EOF;

arrowFunction: '(' parameterList? ')' '=>' block;

parameterList: identifier (',' identifier)*;

statement
    : variableDeclaration
    | assignment
    | ifStatement
    | returnStatement
    | expressionStatement
    | block
    ;

block: '{' statement* '}';

assignment
    : identifier '=' expression ';'
    ;

variableDeclaration
    : ('let' | 'const') identifier '=' expression ';'
    ;

ifStatement
    : 'if' '(' expression ')' statement ('else' statement)?
    ;

returnStatement
    : 'return' expression? ';'
    ;

expressionStatement
    : expression ';'
    ;

expression
    : <assoc=right> expression ('**') expression
    | expression ('*' | '/') expression
    | expression ('+' | '-') expression
    | expression ('>=' | '<=' | '>' | '<') expression
    | expression ('===' | '!==') expression
    | expression '&&' expression
    | expression '||' expression
    | <assoc=right> expression '?' expression ':' expression
    | identifier '(' argumentList? ')'
    | expression '.' identifier
    | '(' expression ')'
    | literal
    | identifier
    ;

argumentList: expression (',' expression)*;

literal
    : StringLiteral
    | NumberLiteral
    | BooleanLiteral
    | NullLiteral
    ;

identifier: Identifier;

StringLiteral: '"' ~["]* '"' | '\'' ~[']* '\'';
NumberLiteral: [0-9]+ ('.' [0-9]+)?;
BooleanLiteral: 'true' | 'false';
NullLiteral: 'null';
Identifier: [a-zA-Z_] [a-zA-Z0-9_]*;

WS: [ \t\r\n]+ -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> channel(HIDDEN);