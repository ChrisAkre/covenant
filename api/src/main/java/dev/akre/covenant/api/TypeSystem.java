package dev.akre.covenant.api;

import java.util.Map;

/**
 * The core engine that manages atoms, hierarchies, and type analysis.
 */
public interface TypeSystem {

    Type type(String name) throws java.util.NoSuchElementException;

    java.util.Optional<Type> find(String name);

    <T extends Type> T expression(String expression);

    Type.TypeFunction typeFunction(String name) throws java.util.NoSuchElementException;

    boolean isAssignableTo(Type self, Type other);

    Type adopt(Type type);

    Type top();

    Type nil();

    Type bottom();

    Type.TemplateType template(String object);

    Map<String, Type> types();
}
