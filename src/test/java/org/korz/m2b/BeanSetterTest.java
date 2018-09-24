package org.korz.m2b;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class BeanSetterTest {
    class SimpleBean {
        private String name;

        public String getName() {
            return name;
        }

        @SuppressWarnings("unused") // reflection
        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    public void simple() {
        SimpleBean bean = new SimpleBean();
        BeanSetter setter = new BeanSetter();
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "fred");
        setter.setProperties(bean, properties);
        assertThat(bean.getName(), is("fred"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unusedError() {
        SimpleBean bean = new SimpleBean();
        BeanSetter setter = BeanSetter.newBuilder()
            .setErrorOnUnused(true)
            .build();
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "fred");
        properties.put("llamo", "federico");
        setter.setProperties(bean, properties);
    }

    @Test
    public void ignoreUnused() {
        SimpleBean bean = new SimpleBean();
        BeanSetter setter = BeanSetter.newBuilder()
            .setErrorOnUnused(false)
            .build();
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "fred");
        properties.put("llamo", "federico");
        setter.setProperties(bean, properties);
        assertThat(bean.getName(), is("fred"));
    }

    class NumberBean {
        private int count;

        public int getCount() {
            return count;
        }

        @SuppressWarnings("unused") // reflection
        public void setCount(int count) {
            this.count = count;
        }
    }

    @Test
    public void typeConvert() {
        NumberBean bean = new NumberBean();
        BeanSetter setter = BeanSetter.newBuilder()
            .addTypeConverter(String.class, int.class, Integer::valueOf)
            .build();
        Map<String, Object> properties = new HashMap<>();
        properties.put("count", "42");
        setter.setProperties(bean, properties);
        assertThat(bean.getCount(), is(42));
    }
}
