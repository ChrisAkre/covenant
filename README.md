### Intro

[!WARNING]
This is not an application, and it is not a programming language. It is a pure, stateless algebraic type engine.

Q. What can I do with it?  
A. Given a set of input types and an expected output constraint, you can mathematically prove the type safety of
decidable programs.

Q. What?  
A. You can type check expressions.

Q. Why?  
A. Maybe you are a language designer and you want to provide strict compile-time guarantees without writing a
unification engine from scratch. Or maybe you are embedding an existing scripting language into your application, and
you want to mathematically verify user-provided expressions before they ever hit the runtime.

Q. How would I do that?  
A. Glad you asked. Take a look at the [JavaScript Typechecker Example](example/README.md).

If you need a practical type validation system to embed in your application, or if you're just really into type algebra,
continue reading.

-----

# Covenant

**A Pure, Stateless Type Engine for Expression-Oriented Languages**

Covenant is practical type system based on designed to act as the mathematical core for expression-oriented host
languages. Built entirely on set theory and boolean algebra, it evaluates types as strict constraints on values and
shapes, bypassing the runtime baggage of object-oriented hierarchies and nominal subtyping limitations.

By acting as a pure data transformer, Covenant provides language creators with a mathematically sound, decidable, and
predictable type checker capable of modeling complex domain logic—such as enums, branded types, and flow-sensitive
narrowing—without requiring special-case syntax.

---

## Core Philosophy

* **100% Expression-Based:** Designed exclusively for reducible, expression-based ASTs structured as Directed Acyclic
  Graphs (DAGs) in Static Single Assignment (SSA) form.
* **Turing-Incomplete & Decidable:** Covenant guarantees compilation termination. It fundamentally rejects infinite
  recursive structures in favor of strict, statically verifiable boundaries.
* **Zero Internal Annotations:** Whole-expression type checking depends exclusively on the inputs. No additional
  annotations are required within expressions or lambdas.
* **Tabula Rasa:** The engine boots with only two concepts: $\top$ (Top) and $\bot$ (Bottom). Every atom, literal, and
  structural template must be explicitly defined by the host language via the Builder API.
* **Stateless & Unidirectional:** Types are immutable structural nodes. The unifier handles covariance, contravariance,
  and generic substitution through strict positional extraction without ever mutating the underlying type graph.

---

## Architecture: Axioms vs. Proofs

Covenant enforces a strict boundary between the **Integrator** (the language creator) and the **Author** (the end-user
writing application logic).

### 1. The Builder (Defining the Axioms)

Language creators use Covenant's fluent Builder API to populate the root environment. This defines the standard library
and fundamental truths of the host language, including nominal atoms (e.g., `String`, `Int`), structural templates (
e.g., `Result<T, E>`), and built-in function overloads.

### 2. The Evaluator (Writing the Proofs)

Application authors do not define types; they compose expressions. As authors write application logic (such as `switch`
or `guard` statements), the host language evaluator queries Covenant to mathematically prove whether the user's composed
graph satisfies the required inputs.

---

## Type System Semantics

Covenant relies on a small collection of orthogonal primitives that reduce into Canonical Disjunctive Normal Form (
CDNF).

### 1. Value Typing (Literals & Constraints)

Narrows types to exact physical states or bounds.

* Supports literals (`"Bob"`, `true`, `5`) and relational limits (`gt 0`, `lte 100`).

### 2. Nominal Typing (Atoms & Symbols)

Evaluates explicitly declared identity.

* **Atoms:** Disjoint root sets (e.g., `String`, `Int`).
* **Symbols:** Zero-runtime-cost mathematical tags.
  Custom nominal types like enums can be created from these primitives. For example, intersecting an atom with a
  symbol (`Planets & 'JUPITER'`) safely and purely models closed **Enums**.

### 3. Structural Typing (Templates & Generics)

Evaluates physical layout and subsumption rather than declared names.

* Supports positional variadic tuples and both open and closed property maps. The can be used to model structs, arrays,
  objects, lists, etc.

### 4. Algebraic Composition

The foundation of Covenant's reasoning.

* **Intersections (`&`):** Narrows constraints. If mathematically impossible (`String & Int`), evaluates to $\bot$.
* **Unions (`|`):** Expands constraints. Automatically distributed across function applications.
* **Negation (`~`):** The complement of a set. Crucial for Flow-Sensitive Typing, allowing the host language to
  perfectly narrow types after conditional branches (e.g., `(String | Int) & ~String` simplifies statically to `Int`).