grammar Types;

typeExpression
    : typeDef EOF
    ;

typeDef
    : unionDef
    ;

unionDef
    : intersectionDef ( '|' intersectionDef )*
    ;

intersectionDef
    : functionDef ( '&' functionDef )*
    ;

functionDef
    : genericParams? '(' ( params+=typeDef ( ',' params+=typeDef )* )? ')' '->' ret=typeDef
                                                   # signatureDef
    | primaryDef                                   # basePrimaryDef
    ;

primaryDef
    : primaryDef '?'                               # optionalDef
    | '~' primaryDef                               # negationDef
    | primaryDef ':' (IDENTIFIER | INT_LITERAL)    # pathDef
    | primaryDef '(' (typeDef (',' typeDef)*)? ')' # evaluationDef
    | parameterizedTypeDef                         # parameterizedDef
    | literal                                      # literalDef
    | atomOrAlias                                  # atomOrAliasDef
    | constraint                                   # constraintPassthrough
    | '(' typeDef ')'                              # groupDef
    ;

parameterizedTypeDef
    : IDENTIFIER '<' ( parameter ( ',' parameter )* )? '>'
    ;

parameter
    : ( IDENTIFIER | SYMBOL_LITERAL | '[' constraint ']' ) '?'? ':' typeDef # namedParam
    | typeDef ELLIPSIS?                                                     # positionalParam
    | ELLIPSIS                                                              # spreadParam
    ;

atomOrAlias
    : IDENTIFIER
    ;

literal
    : SYMBOL_LITERAL
    | INT_LITERAL
    | FLOAT_LITERAL
    | STRING_LITERAL
    ;

constraint
    : KEYWORD ( literal | IDENTIFIER | SYMBOL_LITERAL )
    ;

genericParams
    : '<' genericParam ( ',' genericParam )* '>'
    ;

genericParam
    : IDENTIFIER ( ':' typeDef )?
    ;

// Lexer Rules
KEYWORD    : 'gt' | 'lt' | 'gte' | 'lte' | 'eq' | 'neq' | 'matches' | 'nmatches' ;

SYMBOL_LITERAL : '\'' ( ~['\r\n] | '\'\'' )* '\'' ;
INT_LITERAL : '-'? [0-9]+ ;
FLOAT_LITERAL : '-'? [0-9]+ '.' [0-9]+ ;
STRING_LITERAL : '"' ( ~["\r\n] | '""' )* '"' ;

IDENTIFIER : [a-zA-Z][a-zA-Z0-9_-]* ;

ELLIPSIS : '...' ;
WS : [ \t\r\n]+ -> skip ;
