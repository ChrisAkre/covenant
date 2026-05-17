package dev.akre.covenant.types;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.api.TypeParameter;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.introspect.BasicClassIntrospector;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedClassResolver;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser that translates a Java type (via Jackson 3 JavaType/reflection) into a Covenant TypeDef.
 */
public class JavaTypeParser {
    private final AbstractTypeSystem system;
    private final ObjectMapper mapper;
    private final BasicClassIntrospector introspector;

    public JavaTypeParser(AbstractTypeSystem system) {
        JavaTypeSystem.checkContract(system);
        this.system = system;
        this.mapper = JsonMapper.builder().build();
        this.introspector = new BasicClassIntrospector().forOperation(mapper.serializationConfig());
    }

    public Type parse(java.lang.reflect.Type type) {
        return parse(mapper.constructType(type));
    }

    public Type parse(JavaType type) {
        if (type == null) {
            return system.top();
        }

        if (type.isTypeOrSubTypeOf(String.class) || type.isTypeOrSubTypeOf(CharSequence.class)) {
            return system.type("String");
        }

        if (type.isTypeOrSubTypeOf(Number.class) || isPrimitiveNumber(type)) {
            if (type.isTypeOrSubTypeOf(Integer.class) || type.isTypeOrSubTypeOf(Long.class) || type.isTypeOrSubTypeOf(Short.class) || type.isTypeOrSubTypeOf(Byte.class) || type.hasRawClass(int.class) || type.hasRawClass(long.class) || type.hasRawClass(short.class) || type.hasRawClass(byte.class)) {
                return system.type("Int");
            }
            if (type.isTypeOrSubTypeOf(Float.class) || type.isTypeOrSubTypeOf(Double.class) || type.hasRawClass(float.class) || type.hasRawClass(double.class)) {
                return system.type("Float");
            }
            return system.type("Number");
        }

        if (type.isTypeOrSubTypeOf(Boolean.class) || type.hasRawClass(boolean.class)) {
            return system.type("Bool");
        }

        if (type.isEnumType()) {
            return parseEnum(type);
        }

        if (type.isArrayType() || type.isCollectionLikeType()) {
            return parseArray(type);
        }

        if (type.isMapLikeType()) {
            return parseMap(type);
        }

        if (type.isTypeOrSubTypeOf(java.util.Optional.class)) {
            JavaType contentType = type.getContentType();
            if (contentType == null) {
                 if (type.containedTypeCount() > 0) {
                      contentType = type.containedType(0);
                 }
            }
            Type innerType = contentType != null ? parse(contentType) : system.top();
            return innerType;
        }

        if (type.isTypeOrSubTypeOf(Object.class) && !type.isJavaLangObject()) {
            return parseObject(type);
        }

        return system.type("java.lang.Object");
    }

    private boolean isPrimitiveNumber(JavaType type) {
        Class<?> raw = type.getRawClass();
        return raw == int.class || raw == long.class || raw == short.class || raw == byte.class || raw == double.class || raw == float.class;
    }

    private Type parseEnum(JavaType type) {
        Object[] constants = type.getRawClass().getEnumConstants();
        if (constants == null || constants.length == 0) {
            return system.type("String");
        }
        List<Type> members = new ArrayList<>();
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            members.add(system.intersect(
                    system.type("String"),
                    system.expression("eq '" + name.replace("'", "''") + "'")));
        }
        return system.union(members.toArray(Type[]::new));
    }

    private Type parseArray(JavaType type) {
        JavaType contentType = type.getContentType();
        if (contentType == null && type.isArrayType()) {
            contentType = type.getContentType(); // Might need to check Jackson 3 getContentType
        }
        Type itemsType = contentType != null ? parse(contentType) : system.top();
        List<TypeParameter> params = new ArrayList<>();
        params.add(new TypeParameter.Spread(itemsType)); // Array spread param uses Spread, not Positional directly in AST syntax [...T]
        return system.template("Array").construct(params);
    }

    private Type parseMap(JavaType type) {
        JavaType contentType = type.getContentType();
        if (contentType == null && type.containedTypeCount() > 1) {
            contentType = type.containedType(1);
        }
        Type valueType = contentType != null ? parse(contentType) : system.top();
        List<TypeParameter> params = new ArrayList<>();
        params.add(new TypeParameter.Spread(valueType));
        return system.template("Object").construct(params);
    }

    private Type parseObject(JavaType type) {
        AnnotatedClass ac = AnnotatedClassResolver.resolve(mapper.serializationConfig(), type, mapper.serializationConfig());
        BeanDescription bd = introspector.introspectForSerialization(type, ac);

        List<TypeParameter> params = new ArrayList<>();

        for (BeanPropertyDefinition prop : bd.findProperties()) {
            String name = prop.getName();
            JavaType propType = prop.getPrimaryType();

            // Check for NotNull/Nullable equivalent by checking requirement
            boolean isRequired = prop.isRequired();
            if (prop.getPrimaryMember() != null) {
                if (prop.getPrimaryMember().hasAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class)) {
                    com.fasterxml.jackson.annotation.JsonProperty jp = prop.getPrimaryMember().getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
                    if (jp != null && jp.required()) {
                        isRequired = true;
                    }
                }
            }

            Type parsedType = parse(propType);

            if (propType != null && propType.isTypeOrSubTypeOf(java.util.Optional.class)) {
                isRequired = false;
            }

            params.add(new TypeParameter.Named(parsedType, name, !isRequired));
        }

        params.add(new TypeParameter.Spread(system.top()));

        return system.template("Object").construct(params);
    }
}
