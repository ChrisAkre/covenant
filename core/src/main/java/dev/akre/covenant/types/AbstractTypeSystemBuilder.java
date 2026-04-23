package dev.akre.covenant.types;

import static dev.akre.covenant.types.TypeSystemUtils.updateNominalDef;

import dev.akre.covenant.api.Parameter;
import dev.akre.covenant.types.AbstractTypeSystemBuilder.PatternConstructor.Pattern;
import java.util.*;
import java.util.function.Function;

/**
 * Base class for AbstractTypeSystem builders, supporting a flat, fluent DSL.
 * @param <B> The type of the builder subclass.
 * @param <S> The type of the AbstractTypeSystem being built.
 */
public abstract class AbstractTypeSystemBuilder<B extends AbstractTypeSystemBuilder<B, S>, S extends AbstractTypeSystem>
        implements AbstractTypeSystem {

    protected TypeDef lastType;
    protected final Function<Map<String, TypeDef>, S> builder;
    protected final Map<String, TypeDef> types;

    protected AbstractTypeSystemBuilder(Map<String, TypeDef> types, Function<Map<String, TypeDef>, S> builder) {
        this.types = new LinkedHashMap<>(types);
        this.builder = builder;
    }

    protected AbstractTypeSystemBuilder(Function<Map<String, TypeDef>, S> builder) {
        this(Map.of(), builder);
    }

    protected AbstractTypeSystemBuilder(AbstractTypeSystem base, Function<Map<String, TypeDef>, S> builder) {
        this(base.typesDef(), builder);
    }

    protected abstract B self();

    @Override
    public Map<String, TypeDef> typesDef() {
        return types;
    }

    @Override
    public TypeDef topDef() {
        return types.values().stream()
                .filter(TopType.class::isInstance)
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Top must be defined"));
    }

    @Override
    public TypeDef bottomDef() {
        return types.values().stream()
                .filter(BottomType.class::isInstance)
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Bottom must be defined"));
    }

    @Override
    public TypeDef nilDef() {
        return types.values().stream()
                .filter(t -> t.attributes().contains(dev.akre.covenant.api.TypeAttribute.NULL_SEMANTICS))
                .findFirst()
                .orElse(null);
    }

    // --- CORE ATOM DEFINITION ---

    public B atom(String name) {
        validateName(name);
        // Defaulting to empty attributes for opt-in modifiers.
        lastType = new AtomType(name, Set.of(), EnumSet.noneOf(dev.akre.covenant.api.TypeAttribute.class));
        types.put(name, lastType);
        return self();
    }

    private void validateName(String name) {
        if (!com.google.re2j.Pattern.matches("[a-zA-Z][a-zA-Z0-9_-]*", name)) {
            throw new IllegalArgumentException("Invalid atom name: " + name + ". Names must be identifiers.");
        }
    }

    // --- SYSTEM ABSOLUTES & FLAGS ---

    public B asTop() {
        if (this.types.values().stream().anyMatch(TopType.class::isInstance)) {
            throw new IllegalStateException("Top type already defined in this system.");
        }
        AtomType last = requireLastType("asTop()", AtomType.class);
        types.put(last.name(), new TopType(last.name()));
        this.lastType = null; // Black hole lock
        return self();
    }

    public B asBottom() {
        if (types.values().stream().anyMatch(BottomType.class::isInstance)) {
            throw new IllegalStateException("Bottom type already defined in this system.");
        }
        AtomType last = requireLastType("asBottom()", AtomType.class);
        types.put(last.name(), new BottomType(last.name()));
        this.lastType = null; // Black hole lock
        return self();
    }

    public B asNull() {
        NominalDef updated = updateNominalDef(
                this,
                requireLastType("asNull()", AtomType.class),
                null,
                dev.akre.covenant.api.TypeAttribute.NULL_SEMANTICS);
        types.put(updated.name(), updated);
        lastType = updated;
        return self();
    }

    // --- CONSTRAINT CAPABILITIES ---

    public B supportsNumericBounds() {
        NominalDef updated = updateNominalDef(
                this,
                requireLastType("supportsNumericBounds()", NominalDef.class),
                null,
                dev.akre.covenant.api.TypeAttribute.NUMERIC_SEMANTICS);
        types.put(updated.name(), updated);
        lastType = updated;
        return self();
    }

    public B supportsTextualBounds() {
        NominalDef updated = updateNominalDef(
                this,
                requireLastType("supportsTextualBounds()", NominalDef.class),
                null,
                dev.akre.covenant.api.TypeAttribute.STRING_SEMANTICS);
        types.put(updated.name(), updated);
        lastType = updated;
        return self();
    }

    public B asAbstract() {
        NominalDef updated = updateNominalDef(
                this,
                requireLastType("asAbstract()", NominalDef.class),
                null,
                dev.akre.covenant.api.TypeAttribute.ABSTRACT);
        types.put(updated.name(), updated);
        lastType = updated;
        return self();
    }

    public B asBoolean() {
        NominalDef updated = updateNominalDef(
                this,
                requireLastType("asBoolean()", NominalDef.class),
                null,
                dev.akre.covenant.api.TypeAttribute.BOOLEAN_SEMANTICS);
        types.put(updated.name(), updated);
        lastType = updated;
        return self();
    }

    public B asNumeric() {
        return supportsNumericBounds();
    }

    public B asString() {
        return supportsTextualBounds();
    }

    // --- HIERARCHY ---

    public B satisfies(String... parent) {
        requireLastType("satisfies()", NominalDef.class);
        for (String p : parent) {
            lastType = updateNominalDef(this, (NominalDef) lastType, Set.of(p), null);
        }
        types.put(((NominalDef) lastType).name(), lastType);
        return self();
    }

    public B satisfiedBy(String... childName) {
        NominalDef last = requireLastType("satisfies()", NominalDef.class);
        for (String c : childName) {
            TypeDef childDef = types.get(c);
            if (childDef instanceof NominalDef child) {
                types.put(c, updateNominalDef(this, child, Set.of(last.name()), null));
            } else {
                throw new IllegalStateException("Type " + c + " not defined or is not a valid Atom or Template");
            }
        }
        return self();
    }

    // --- FLAT CONSTRUCTOR GRAMMAR ---

    public B positionalPattern() {
        AtomType last = requireLastType("positionalPattern()", AtomType.class);
        lastType = TypeSystemUtils.updateTemplate(this, last, Pattern.POSITIONAL, null, null);
        types.put(last.name(), lastType);
        return self();
    }

    public B arrayPattern() {
        AtomType last = requireLastType("arrayPattern()", AtomType.class);
        lastType = TypeSystemUtils.updateTemplate(this, last, Pattern.ARRAY, null, null)
                .withAttribute(dev.akre.covenant.api.TypeAttribute.ARRAY);
        types.put(last.name(), lastType);
        return self();
    }

    public B objectPattern() {
        AtomType last = requireLastType("objectPattern()", AtomType.class);
        lastType = TypeSystemUtils.updateTemplate(this, last, Pattern.OBJECT, null, null)
                .withAttribute(dev.akre.covenant.api.TypeAttribute.OBJECT);
        types.put(last.name(), lastType);
        return self();
    }

    public B minParams(int min) {
        TemplateType last = requireLastType("minParams()", TemplateType.class);
        lastType = TypeSystemUtils.updateTemplate(this, last, null, min, null);
        types.put(last.name(), lastType);
        return self();
    }

    public B maxParams(int max) {
        TemplateType last = requireLastType("maxParams()", TemplateType.class);
        lastType = TypeSystemUtils.updateTemplate(this, last, null, null, max);
        types.put(last.name(), lastType);
        return self();
    }

    // --- ALIASES & FUNCTIONS ---

    public B typeAlias(String name, String expression) {
        TypeDef type = new TypeExprVisitor().parseDef(this, expression);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Failed to parse type expression for alias '" + name + "': " + expression);
        }
        types.put(name, type);
        this.lastType = null; // Prevent accidental modifier attachment to previous atom
        return self();
    }

    public B functionAlias(String name, String... signatures) {
        if (types.containsKey(name)) {
            throw new IllegalStateException("Alias '" + name + "' is already defined.");
        }

        TypeDef overloadIntersection = Arrays.stream(signatures)
                .map(sig -> {
                    TypeDef type = new TypeExprVisitor().parseDef(this, sig);
                    if (type == null) {
                        throw new IllegalArgumentException(
                                "Failed to parse function signature for '" + name + "': " + sig);
                    }
                    return type;
                })
                .reduce(this::intersectDef)
                .orElseThrow(() ->
                        new IllegalArgumentException("Must provide at least one signature for function: " + name));

        types.put(name, overloadIntersection);
        this.lastType = null;
        return self();
    }

    // --- TERMINATION ---

    public S build() {
        // add an unnamed top and bottom if not present
        if (this.types.values().stream().noneMatch(TopType.class::isInstance)) {
            types.put("__unnamed_top__", new TopType("__unnamed_top__"));
        }
        if (this.types.values().stream().noneMatch(BottomType.class::isInstance)) {
            types.put("__unnamed_bottom__", new BottomType("__unnamed_bottom__"));
        }
        return builder.apply(types);
    }

    private <T extends TypeDef> T requireLastType(String operation, Class<T> cls) {
        if (lastType == null) {
            throw new IllegalStateException("No active atom context for " + operation + ". Call .atom() first.");
        } else if (!cls.isInstance(lastType)) {
            throw new IllegalStateException("Atom context for " + operation + " expected to be " + cls + " but was "
                    + lastType.getClass() + ".");
        } else {
            return cls.cast(lastType);
        }
    }

    // --- INTERNAL CONSTRUCTOR RECORDS ---

    public record PatternConstructor(Pattern pattern, int min, int max) implements TypeConstructor {
        public enum Pattern {
            POSITIONAL,
            ARRAY,
            OBJECT
        }

        public PatternConstructor(Pattern pattern) {
            this(pattern, 0, Integer.MAX_VALUE);
        }

        @Override
        public TypeDef construct(
                AbstractTypeSystem system, TemplateType origin, List<TypeDef> members, List<Parameter> parameters) {
            if (parameters.size() < min || parameters.size() > max) {
                throw new IllegalArgumentException(String.format(
                        "Invalid number of parameters for %s. Expected [%d, %d], found %d",
                        origin.name(), min, max, parameters.size()));
            }
            if (!(origin instanceof TemplateType template)) {
                throw new IllegalArgumentException(
                        "Origin must be a TemplateType: " + origin.getClass().getName());
            }

            List<TypeDefParam> params = new java.util.ArrayList<>();
            for (dev.akre.covenant.api.Parameter p : parameters) {
                params.add(new TypeDefParam(p.index() != null ? members.get(p.index()) : system.topDef(), p));
            }

            return new GenericTypeDef(template, pattern, params);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractTypeSystem that)) return false;
        return Objects.equals(this.typesDef(), that.typesDef());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.typesDef());
    }
}
