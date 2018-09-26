package org.korz.beanmagic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;

public class BeanFactory {
    private static final Logger LOG = LoggerFactory.getLogger(BeanFactory.class);

    private final boolean validateInterface;

    public BeanFactory() {
        this(newBuilder());
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private boolean validateInterface = true;

        private Builder() {
        }

        public Builder setValidateInterface(boolean validateInterface) {
            this.validateInterface = validateInterface;
            return this;
        }

        public BeanFactory build() {
            return new BeanFactory(this);
        }
    }

    private BeanFactory(Builder b) {
        validateInterface = b.validateInterface;
    }

    @SuppressWarnings("unchecked")
    public <T> T createBean(Class<T> beanInterface) {
        if (beanInterface == null) {
            throw new NullPointerException("beanInterface is null");
        }
        if (!beanInterface.isInterface()) {
            throw new IllegalArgumentException("beanInterface must be an interface");
        }
        if (validateInterface) {
            // TODO: validate only matching getters and setters
        }
        return (T) Proxy.newProxyInstance(this.getClass().getClassLoader(),
                                          new Class[] { beanInterface },
                                          new BeanInvocationHandler(beanInterface));
    }
}
