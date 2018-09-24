package org.korz.beanmagic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;

public class BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(BeanFactory.class);

    public BeanFactory() {
    }

    @SuppressWarnings("unchecked")
    public <T> T createBean(Class<T> beanInterface) {
        if (beanInterface == null) {
            throw new NullPointerException("beanInterface is null");
        }
        if (!beanInterface.isInterface()) {
            throw new IllegalArgumentException("beanInterface must be an interface");
        }
        return (T) Proxy.newProxyInstance(this.getClass().getClassLoader(),
                                          new Class[] { beanInterface },
                                          new BeanInvocationHandler(beanInterface));
    }
}
