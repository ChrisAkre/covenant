package dev.akre.covenant.types;

import dev.akre.covenant.api.Type;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.junit.jupiter.api.Assertions.*;

public class JavaTypeParserTest {

    private final TestTypeSystem typeSystem = TestTypeSystem.of(JavaTypeSystem.INSTANCE);
    private final JavaTypeParser parser = new JavaTypeParser(typeSystem); // Note: TestTypeSystem extends AbstractTypeSystem, and wraps internally. Wait, the constructor requires AbstractTypeSystem, which TestTypeSystem IS via "implements". So this is fine.

    @Test
    public void testPrimitives() {
        Type str = parser.parse(String.class);
        typeSystem.assertThat(str).isEquivalentTo("String");

        Type i = parser.parse(int.class);
        typeSystem.assertThat(i).isEquivalentTo("Int");

        Type iWrapper = parser.parse(Integer.class);
        typeSystem.assertThat(iWrapper).isEquivalentTo("Int");

        Type bool = parser.parse(boolean.class);
        typeSystem.assertThat(bool).isEquivalentTo("Bool");

        Type boolWrapper = parser.parse(Boolean.class);
        typeSystem.assertThat(boolWrapper).isEquivalentTo("Bool");

        Type floatNum = parser.parse(float.class);
        typeSystem.assertThat(floatNum).isEquivalentTo("Float");

        Type num = parser.parse(Number.class);
        typeSystem.assertThat(num).isEquivalentTo("Number");
    }

    enum Status {
        ACTIVE, INACTIVE
    }

    @Test
    public void testEnum() {
        Type status = parser.parse(Status.class);
        typeSystem.assertThat(status).isEquivalentTo("String & (eq 'ACTIVE' | eq 'INACTIVE')");
    }

    @Test
    public void testArrayAndList() throws NoSuchFieldException {
        Type arr = parser.parse(int[].class);
        assertTrue(arr.repr().contains("Array<"));

        java.lang.reflect.Type listType = TestPojo.class.getDeclaredField("list").getGenericType();
        Type list = parser.parse(listType);
        assertTrue(list.repr().contains("Array<"));
    }

    @Test
    public void testMap() throws NoSuchFieldException {
        java.lang.reflect.Type mapType = TestPojo.class.getDeclaredField("map").getGenericType();
        Type map = parser.parse(mapType);
        assertTrue(map.repr().contains("Object<"));
    }

    @Test
    public void testPojoAndOptional() {
        Type pojo = parser.parse(TestPojo.class);
        String pojoRepr = pojo.repr();
        assertTrue(pojoRepr.contains("name: String"));
        assertTrue(pojoRepr.contains("opt?: Int"));
        assertTrue(pojoRepr.contains("list?: Array<"));
        assertTrue(pojoRepr.contains("map?: Object<"));
    }

    static class TestPojo {
        @JsonProperty(required = true)
        public String name;
        public Optional<Integer> opt;
        public List<String> list;
        public Map<String, Integer> map;
    }
}
