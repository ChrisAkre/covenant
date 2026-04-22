# Covenant Type System Library

A flexible type inference and validation library for Java that enables static type checking of expressions represented 
as Abstract Syntax Trees (ASTs). Build custom type systems with inheritance, generics, function overloading, and 
advanced type operations like union and intersection types.

## Overview

This library provides a framework for defining custom type systems with rich type relationships and automatically
inferring the types of variables based on their expressions. It's particularly useful for building domain-specific
languages, query engines, configuration validators, or any system that needs compile-time type safety for dynamically
constructed expressions.

## Features

* **Declarative Type System Definition**: Build type systems using a fluent builder API
* **Type Hierarchy**: Subtype relationships and aliasing
* **Structured Types**: Arrays, objects, and scalars with constructors
* **Advanced Type Operations**: Union types, intersection types, negation, optional types
* **Type Inference**: Automatically calculate and verify result types

## Installation

```xml
<dependency>
    <groupId>dev.akre</groupId>
    <artifactId>covenant-types</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
import dev.akre.covenant.types.TestTypeSystemBuilder;
import dev.akre.covenant.types.TestTypeSystem;

// Build the type system
TestTypeSystem system = new TestTypeSystemBuilder()
    .typeAtom("String")
    .typeAtom("Number")
    .typeAtom("Int").satisfies("Number")
    .build();

system.assertThat("Int").satisfies("Number");
system.assertThat("String | Number").satisfies("String | Int | Float"); // if Float is in Number
```

## JSON Type System Example

A complete type system built for JSON is already provided:

```java
import dev.akre.covenant.types.JsonTypeSystem;
import dev.akre.covenant.types.TypeSystem;
import dev.akre.covenant.types.TestTypeSystem;
import dev.akre.covenant.types.TestTypeSystemBuilder;

TypeSystem json = JsonTypeSystem.INSTANCE;

assertTrue(json.type("Int").

satisfies(json.type("Float")));

// You can also extend it!
TestTypeSystem extendedSystem = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
        .typeAlias("FooString", "String & 'foo'")
        .build();

extendedSystem.

assertThat("FooString").

satisfies("String");
```

## Advanced Operations

### Intersections and Unions

```java
TestTypeSystem system = new TestTypeSystemBuilder()
    .atom("Float")
    .atom("Int").satisfies("Float")
    .build();

system.assertThat("Int & Float").satisfies("Int");
system.assertThat("Int | Float").satisfies("Float");
```

### Optionals

```java
TestTypeSystem system = new TestTypeSystemBuilder()
    .atom("String")
    .atom("Null")
    .build();

system.assertThat("String").satisfies("String?");
system.assertThat("Null").satisfies("String?");
```

### Scalars and Positional Constructors

```java
TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
    .atom("Decimal")
    .atom("CurrencyCode")
    .atom("Scalar").constructorBuilder().positional().min(2).build()
    .build();

system.assertThat("Scalar<Decimal, 'USD'>").term(0).satisfies("Decimal");
system.assertThat("Scalar<Decimal, 'USD'>").term(1).satisfies("'USD'");
```

### Quoted Identifiers

```java
TestTypeSystem system = new TestTypeSystemBuilder(JsonTypeSystem.INSTANCE)
    .atom("Object").constructorBuilder().objectPattern().build()
    .typeAlias("QuotedAlias", "'Some Quoted Name'")
    .typeAtom("Some Quoted Name")
    .build();

system.assertThat("Object<'1': Int, 'property with spaces': String>")
    .term("'1'").satisfies("Int")
    .term("property with spaces").satisfies("String");
```
