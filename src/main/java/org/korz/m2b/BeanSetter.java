package org.korz.m2b;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class BeanSetter {
    private static final Logger LOG = LoggerFactory.getLogger(BeanSetter.class);

    private final boolean errorOnUnused;
    private final Map<TypeConverterKey, Function<?, ?>> typeConverters;

    public BeanSetter() {
        this(new Builder());
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private boolean errorOnUnused = true;
        private final Map<TypeConverterKey, Function<?, ?>> typeConverters = new HashMap<>();

        private Builder() {
        }

        public Builder setErrorOnUnused(boolean errorOnUnused) {
            this.errorOnUnused = errorOnUnused;
            return this;
        }

        public <In, Out> Builder addTypeConverter(Class<In> in, Class<Out> out, Function<In, Out> converter) {
            if (in == null) {
                throw new NullPointerException("in is null");
            }
            if (out == null) {
                throw new NullPointerException("out is null");
            }
            if (converter == null) {
                throw new NullPointerException("converter is null");
            }
            typeConverters.put(new TypeConverterKey(in, out), converter);
            return this;
        }

        public Builder removeTypeConverter(Class<?> in, Class<?> out) {
            if (in == null) {
                throw new NullPointerException("in is null");
            }
            if (out == null) {
                throw new NullPointerException("out is null");
            }
            typeConverters.remove(new TypeConverterKey(in, out));
            return this;
        }

        public Builder removeAllTypeConverters() {
            typeConverters.clear();
            return this;
        }

        public BeanSetter build() {
            return new BeanSetter(this);
        }
    }

    private BeanSetter(Builder b) {
        errorOnUnused = b.errorOnUnused;
        typeConverters = b.typeConverters;
    }

    public void setProperties(Object bean, Map<String, ?> properties) {
        if (bean == null) {
            throw new NullPointerException("bean is null");
        }
        if (properties == null) {
            throw new NullPointerException("properties is null");
        }
        Map<String, Method> setters = getSetters(bean.getClass());
        for (Map.Entry<String, ?> property : properties.entrySet()) {
            // Find setter
            String propertyName = property.getKey();
            String setterName = getSetterName(propertyName);
            Method setter = setters.get(setterName);
            if (setter == null) {
                String msg = "Could not find setter for property \"" + propertyName + "\"";
                if (errorOnUnused) {
                    throw new IllegalArgumentException(msg);
                } else {
                    LOG.warn(msg);
                    continue;
                }
            }

            // Set property value
            Object propertyValue = property.getValue();
            try {
                setter.invoke(bean, propertyValue);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException(
                    "Failed to set property \"" + propertyName + "\", cannot invoke setter", e);
            } catch (IllegalArgumentException e) {
                // Argument type mismatch, try to convert property value
                Class<?> propertyType = propertyValue.getClass();
                Class<?> parameterType = setter.getParameterTypes()[0];
                // Search for static valueOf method
                try {
                    Function typeConverter = typeConverters.get(new TypeConverterKey(propertyType, parameterType));
                    if (typeConverter == null) {
                        throw new IllegalArgumentException(
                            "Failed to set property \"" + propertyName + "\"" +
                                ", cannot convert " + propertyType.getName() + " to " + parameterType.getName());
                    }
                    Object convertedValue = typeConverter.apply(propertyValue);
                    setter.invoke(bean, convertedValue);
                } catch (IllegalAccessException | InvocationTargetException e1) {
                    throw new IllegalArgumentException(
                        "Failed to set property \"" + propertyName + "\"" +
                            ", failed to convert " + propertyType.getName() + " to " + parameterType.getName(), e1);
                }
            }
        }
    }

    // TODO: cache this
    private static Map<String, Method> getSetters(Class<?> beanClass) {
        Map<String, Method> setters = new HashMap<>();
        Method[] methods = beanClass.getMethods();
        LOG.debug("Found {} methods", methods.length);
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers())) {
                LOG.debug("Skipping method, is static: {}", method);
            } else if (method.getReturnType() != void.class) {
                LOG.debug("Skipping method, not void return: {}", method);
            } else if (!method.getName().startsWith("set")) {
                LOG.debug("Skipping method, does not start with \"set\": {}", method);
            }  else if (method.getParameterTypes().length != 1) {
                LOG.debug("Skipping method, does not have exactly one parameter: {}", method);
            } else {
                LOG.debug("Found setter: {}", method);
                setters.put(method.getName(), method);
            }
        }
        LOG.debug("Found {} setters", setters.size());
        return setters;
    }

    private static String getSetterName(String propertyName) {
        return "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }
}
