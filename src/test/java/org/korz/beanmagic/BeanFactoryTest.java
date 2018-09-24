package org.korz.beanmagic;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class BeanFactoryTest {
    interface SimpleBean {
        String getName();
        void setName(String name);
    }

    @Test
    public void simple() {
        SimpleBean bean = new BeanFactory().createBean(SimpleBean.class);
        bean.setName("fred");
        assertThat(bean.getName(), is("fred"));
    }

    @Test
    public void string() {
        SimpleBean bean = new BeanFactory().createBean(SimpleBean.class);
        bean.setName("fred");
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "fred");
        assertThat(bean.toString(), is(properties.toString()));
    }

    @Test
    public void equal() {
        SimpleBean bean = new BeanFactory().createBean(SimpleBean.class);
        bean.setName("fred");
        SimpleBean bean2 = new BeanFactory().createBean(SimpleBean.class);
        bean2.setName("fred");
        assertThat(bean, is(bean2));
        assertThat(bean.hashCode(), is(bean2.hashCode()));
    }

    @Test
    public void notEqualProperty() {
        SimpleBean bean = new BeanFactory().createBean(SimpleBean.class);
        bean.setName("fred");
        SimpleBean bean2 = new BeanFactory().createBean(SimpleBean.class);
        bean2.setName("zed");
        assertThat(bean, not(bean2));
        assertThat(bean.hashCode(), not(bean2.hashCode()));
    }

    // idential to SimpleBean, but a different class
    interface SimpleBean2 {
        String getName();
        void setName(String name);
    }

    @Test
    public void notEqualClass() {
        SimpleBean bean = new BeanFactory().createBean(SimpleBean.class);
        bean.setName("fred");
        SimpleBean2 bean2 = new BeanFactory().createBean(SimpleBean2.class);
        bean2.setName("fred");
        assertThat(bean, not(bean2));
    }

    interface BooleanGetBean {
        boolean getEnabled();
        void setEnabled(boolean enabled);
    }

    @Test
    public void booleanGet() {
        BooleanGetBean bean = new BeanFactory().createBean(BooleanGetBean.class);
        bean.setEnabled(true);
        assertThat(bean.getEnabled(), is(true));
    }

    interface BooleanIsBean {
        boolean isEnabled();
        void setEnabled(boolean enabled);
    }

    @Test
    public void booleanIs() {
        BooleanIsBean bean = new BeanFactory().createBean(BooleanIsBean.class);
        bean.setEnabled(true);
        assertThat(bean.isEnabled(), is(true));
    }
}
