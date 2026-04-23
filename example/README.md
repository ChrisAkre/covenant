# Subscript-JS: A Host Language Example

This directory contains a proof-of-concept JavaScript typechecker built entirely on the Covenant algebraic type engine.

**This is not a production linter or a complete JavaScript parser.** It is a minimal implementation designed to
demonstrate how a language creator integrates a host language's Abstract Syntax Tree (AST) with Covenant's stateless
unification engine.

## The Goal

To mathematically prove that a JavaScript function correctly transforms an input JSON schema into an expected output
JSON schema, without executing the code.

Because Covenant operates purely on set theory (Unions, Intersections, Negations) and does not understand variables or
mutation, this example demonstrates how a host language bridges the gap between stateful syntax and pure math.

## How It Works: The Pipeline

The typechecker (`JsCovenantChecker`) evaluates JavaScript through a strict four-step pipeline.

### 1. Defining the Axioms (The Builder)

In order to use Covenant, you must define the TypeSystem for your language. The `JavaSubscriptTypeSystem` initializes
Covenant's type system builder with the fundamental laws of our Javascript subset. It defines standard atoms (`String`,
`Number`, `Bool`) and registers JavaScript operators as strongly-typed function signatures.

* *Example:* The `+` operator is mapped to an overloaded `plus` function:
  `(Number, Number) -> Number & (String, String) -> String`.

### 2. Lexing and Parsing (ANTLR)

The host language source is parsed into a standard AST using an ANTLR grammar.

### 3. AST Traversal & SSA Transformation

The `JsEvaluatorVisitor` walks the AST. Because Covenant requires immutability, the visitor translates mutable
JavaScript variables (`let x = 1; x = x + 2`) into Static Single Assignment (SSA) form on the fly using a versioned
`Environment` stack.

When control flow branches merge (e.g., the end of an `if` statement), the visitor resolves the Phi nodes by generating
a mathematical **Union** of the divergent variable states.

### 4. The Final Proof

As the visitor encounters `return` statements, it aggregates the resolved types into a single, comprehensive Union
constraint. The final step queries Covenant: `isAssignableTo(FinalUnionType, OutputSchema)`. If the union mathematically
fits inside the bounds of the expected output, the script is proven safe.

## Key Demonstrations

This example highlights two powerful Covenant features that require zero custom type-tracking logic to implement:

### 1. Flow-Sensitive Typing (Control Flow Narrowing)

When the AST visitor encounters a strict equality check (`if (user.status === "inactive")`), it leverages Covenant's
boolean algebra to mathematically narrow the environment.

* Inside the `true` branch, the identifier is shadowed with `currentType.intersect("inactive")`.
* Inside the `false` branch, the identifier is shadowed with `currentType.intersect(negate("inactive"))`.
  Covenant's Normalizer automatically resolves these intersections against the original schemas to prove exhaustiveness.

### 2. Nested Structural Constraints

When narrowing deep property paths (`if (user.profile.status === "inactive")`), the typechecker does not manually mutate
the nested object. Instead, it walks up the AST to build a brand new structural constraint:
`Object<profile: Object<status: "inactive", ...>, ...>`

It then fires a single intersection at the root `user` identifier. Covenant handles the deep merging, structural
subsumption, and contradiction detection entirely behind the scenes.

## Running the Tests

To see the typechecker evaluate schemas, apply constraints, and catch return-type violations, run the test suite:

```bash
# From the repository root
mvn test
```