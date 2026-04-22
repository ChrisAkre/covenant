# Covenant Type System Design Document

## 1. Core Philosophy & Architecture

Covenant is a type system designed specifically for expression-oriented languages. It serves as a type checker for the
host language, identifying type errors and supplying strict guarantees of runtime behavior. It is built on a pure,
stateless, unidirectional type engine based on set theory and boolean algebra, ensuring the system remains practical,
provable, and extensible. Types are abstractions decoupled from implementation details; they are modeled mathematically
as boundaries defining sets of constraints on values and other types. Covenant proves subtyping constraints by
calculating $A <: B$ through the verification of disjointness: $A \cap \neg B = \emptyset$ (meaning the intersection
of $A$ and the complement of $B$ is completely empty).

The core constraints and goals of this system are:

* **Zero Type Annotations:** Whole-expression type checking depends exclusively on the type definitions of the inputs,
  requiring no additional type annotations within expressions or lambdas.
* **100% Expression-Based:** All expressions to be type-checked must be reducible to Static Single Assignment (SSA) form
  within a Directed Acyclic Graph (DAG).
* **Turing-Incomplete:** All expression evaluations must be decidable with guaranteed termination. Type checking of
  unbounded loops and recursive functions is intentionally unsupported.
* **Type Signatures on Built-ins Only:** All built-in functions of the host language possess a predefined, static type
  signature. All other functions are defined entirely as compositions or intersections of built-in or previously defined
  functions, which are then evaluated to infer the final type signature.
* **Strict Schemas:** All inputs must be strictly typed. Loose boundaries (e.g., a JSON schema allowing
  `additionalProperties: true`) are treated as $\top$ (the universal bound) and mandate explicit type guarding
  (narrowing) to satisfy constraints.
* **Tabula Rasa Builder:** The type system initializes as a blank slate containing only $\top$ (Top) and $\bot$ (
  Bottom). All language-specific nominal types, structural types, hierarchies, value constraints, functions, and
  templates must be explicitly registered via a fluent Builder API.

### 1.1 The Type Definition Data Model

The type definition data model is a closed universe of immutable, referentially transparent structural nodes. It
consists of:

* **Terminal Literals:** Top, Bottom, Nominals, Symbols, Value Constraints, and Generic Types.
* **Algebraic Containers**: Unions (Logical OR), Intersections (Logical AND), and Negations (Complements).
* **Type Functions:** Type templates and Function signatures (Signatures).

Type definitions encapsulate the rules governing their immediate boundaries via a strict interface for constraint
narrowing, widening, inversion, and directional subsumption. These node-level operations evaluate exclusively against
related types within the same domain. They operate independently of global state, delegating the complex
logic of canonicalization and composition across orthogonal sets entirely to the type system.

### 1.2 The Type System Builder

The Type System Builder acts as the stateful registry responsible for defining the universe of types, constraints,
inheritance hierarchies, and type templates. It establishes the relational boundaries of the system, including the
enforcement of closed-world assumptions. The Builder resolves type expressions into physical graph nodes and serves as
the central query interface for existing types by name, alias, or structural role (e.g., Top, Bottom, Null). Crucially,
it manages the environment layer, exposing the pure mathematical graph to the compiler through a fluent API that wraps
stateless type definitions with convenience methods for structural composition and validation.

### 1.3 The Type Expression Parser

The Type Parser operates as a text-based frontend for the type system, translating host-language type expressions into
an Abstract Syntax Tree (AST). It provides a supplementary interface to the fluent Builder API for the interpretation of
annotations and externalized type definitions. The resulting AST is subsequently resolved into concrete type definitions
by evaluating it against a system-provided context of global and local type bindings.

### 1.4 The CDNF Normalizer

The Normalizer operates as the primary algebraic reduction mechanism of the type system. Upon the
intersection, union, or negation of types, it evaluates the resulting graph into Canonical Disjunctive Normal
Form (CDNF). The normalization procedure applies De Morgan's laws, distributes Cartesian products, and enforces strict
subsumption rules across mixed-polarity types. Additionally, the Normalizer governs exhaustiveness checking; it formally
detects when bounded domains (such as sealed hierarchies or booleans) have been entirely negated, subsequently
collapsing the resultant intersection to $\bot$ (Bottom).

### 1.5 The Unidirectional Unifier

The Unifier resolves generic constraints and higher-order function invocations without mutating the underlying type
graph. It operates as a strictly unidirectional, three-phase data transformer: aligning arguments to parameters,
extracting type bindings via structural traversal, and substituting bound type variable references with concrete types.
By managing covariance and contravariance entirely through positional extraction, the Unifier remains a pure, stateless
algorithmic utility completely free from side effects.

## 2. Type System Semantics

Covenant evaluates types fundamentally as sets of values and the mathematical boundaries that contain them. The system
relies on a small collection of orthogonal primitives that, when combined algebraically, are capable of modeling
complex, high-level domain logic without requiring dedicated, special-case syntax for features like enums or branded
types.

The semantics of the type system are divided into four core paradigms: Nominal Typing, Structural Typing, Value Typing,
and Algebraic Composition.

### 2.1 Nominal Typing (Atoms & Symbols)

Nominal types evaluate the explicitly declared *identity* of a value rather than its shape or contents. In Covenant,
nominal typing is driven by two primitives: **Atoms** and **Symbols**.

* **Atoms** define universally disjoint sets. An atom like `String` and an atom like `Int` share no overlapping values.
  Furthermore, domain-specific atoms defined by the host language (e.g., `Planets`, `Deities`) act as disjoint
  collections.
* **Symbols** are type system-specific identity tags. They contain no data but represent a unique mathematical mark that
  can be associated with data.

**Mapping to Host Language Concepts:**

By intersecting an Atom with a Symbol, Covenant naturally models **Enums**. For example, the intersection
`Planets & 'JUPITER'` creates a strictly isolated nominal identity. Because the base atoms are disjoint,
`Planets & 'JUPITER'` is mathematically incapable of satisfying `Deities & 'JUPITER'`, preventing domain leakage.

### 2.2 Structural Typing (Templates & Generics)

Structural types evaluate the physical shape and layout of data. Instead of checking a declared name, the structural
engine verifies the presence and types of properties, fields, or positional elements. This encompasses **Objects**, *
*Arrays**, and their **Generic Templates**.

Structural evaluation relies on the mathematical principle of subsumption: a structural type satisfies another if it
provides *at least* the required guarantees of the target constraint without violating any of them.

**Mapping to Host Language Concepts:**

* **Tuples:** Modeled as structurally constrained arrays with finite, positional boundaries (e.g.,
  `Array<Int, String, String?>`).
* **Lists:** Can be modeled as structurally constrained arrays with variadic parameters (e.g., `Array<Float...>`).

### 2.3 Value Typing (Literals & Constraints)

Value constraints narrow a type down to exact literal values or specific bounded ranges. Instead of defining the broader
category of data, they define the exact physical state or permissible boundary the data must reside in. This includes
literal strings (`"Bob"`), literal booleans (`true`), literal integers (`5`), and relational constraints (`gt`, `lt`,
`gte`, `lte`, `eq`, `neq`).

**Mapping to Host Language Concepts:**

* **Bounded Numeric Types:** Modeled by intersecting a numeric atom with relational constraints. A strictly positive
  integer is algebraically expressed as `Int & gt 0`. A valid percentage domain can be rigidly defined via
  `Int & gte 0 & lte 100`.
* **Branded Types (Refinements):** A branded type is the intersection of a structural primitive and a nominal symbol.
  The type `"Bob" & 'User'` structurally satisfies `String` (allowing it to be used in regex or string concatenation),
  but a naked `"Bob"` cannot satisfy `"Bob" & 'User'`. This allows the host language to enforce strict domain validation
  boundaries on raw data primitives.

### 2.4 Algebraic Composition (Type Algebra)

Algebraic composition allows complex application logic or data types to be described by combining base types.

* **Intersections (Logical AND - `&`):** A restrictive operation. The resulting type only contains values that exist in
  all intersected sets ($A \cap B$). Intersections are used to apply traits, brand primitives, define enums, and combine
  structural requirements. If an intersection results in a contradiction (e.g., `String & Int`), the algebra statically
  collapses the branch to $\bot$ (Bottom).
* **Unions (Logical OR - `|`):** An expansive operation. The resulting type contains values from any of the unioned
  sets ($A \cup B$). Unions are used to define overloads, optionality, and error boundaries (e.g., `Result | Error`).
* **Negation (Logical NOT - `~`):** An exclusionary operation. It represents the complement of a set. Negation is
  heavily utilized by the host language's control flow analysis to narrow types within branch logic (e.g., inferring
  that if `x` is `String | Int`, and `is String(x)` is false, the remaining type is exactly `(String | Int) & ~String`,
  which simplifies to `Int`).

## 3. Internal Type DSL (Grammar)

Covenant uses a type definition DSL as a convenience mechanism for the host language to provide function type
definitions. It is also used for debugging for logs and errors

### Core Expressions

```ebnf
TypeExpression  ::= TypeDef
```

### Algebraic Composition

```ebnf
TypeDef         ::= UnionDef
UnionDef        ::= IntersectionDef ( "|" IntersectionDef )* 
IntersectionDef ::= FunctionDef ( "&" FunctionDef )* 
```

### Functions & Primary Types

```ebnf
FunctionDef     ::= [ GenericParams ] "(" [ TypeDef ( "," TypeDef )* ] ")" "->" PrimaryDef 
                  | PrimaryDef 
PrimaryDef      ::= PrimaryDef "?"                                   /* Optional suffix */ 
                  | "~" PrimaryDef                                   /* Negation prefix */ 
                  | PrimaryDef ":" ( Identifier | IntLiteral )       /* Path evaluation */ 
                  | PrimaryDef "(" [ TypeDef ( "," TypeDef )* ] ")"  /* Type evaluation */ 
                  | ParameterizedTypeDef                             /* Generic instantiation */ 
                  | Literal
                  | Identifier                                       /* Atom or Alias */ 
                  | Constraint                                       /* Narrows possible values */ 
                  | "(" TypeDef ")"                                  /* Grouping */ 
```

### Parameters & Generics

```ebnf
ParameterizedTypeDef ::= Identifier "<" [ Parameter ( "," Parameter )* ] ">" 
Parameter            ::= NamedParam | PositionalParam | SpreadParam 
  NamedParam         ::= ( Identifier | SymbolLiteral | "[" Constraint "]" ) ":" TypeDef [ "?" ] 
  PositionalParam    ::= TypeDef [ "..." ] 
  SpreadParam        ::= "..." 
GenericParams        ::= "<" GenericParam ( "," GenericParam )* ">" 
GenericParam         ::= Identifier [ ":" TypeDef ] 
```

### Lexical Definitions

```ebnf
Literal       ::= SymbolLiteral | IntLiteral | FloatLiteral | StringLiteral 
Constraint           ::= Keyword ( Literal | Identifier | SymbolLiteral ) 
Keyword       ::= "gt" | "lt" | "gte" | "lte" | "eq" | "neq" | "matches" 
SymbolLiteral ::= "'" <any character except newline> "'" 
StringLiteral ::= '"' <any character except newline> '"' 
IntLiteral    ::= [ "-" ] <digit>+ 
FloatLiteral  ::= [ "-" ] <digit>+ "." <digit>+ 
Identifier    ::= <letter> ( <letter> | <digit> | "_" | "-" )* 
```


