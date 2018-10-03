package org.korz.beanmagic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
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
        this(newBuilder());
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
            typeConverters.put(new TypeConverterKey(toBoxedType(in), toBoxedType(out)), converter);
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
                continue;
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException(
                    "Failed to set property \"" + propertyName + "\", cannot invoke setter", e);
            } catch (IllegalArgumentException e) {
                // Argument type mismatch, try to convert property value
            }

            Class<?> parameterType = setter.getParameterTypes()[0];
            Object convertedValue;
            try {
                convertedValue = convertValue(propertyValue, toBoxedType(parameterType));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Failed to set property \"" + propertyName + "\"" +
                        ", failed to convert " + propertyValue.getClass().getName() + " to " + parameterType.getName(), e);
            }
            try {
                setter.invoke(bean, convertedValue);
            } catch (IllegalAccessException | InvocationTargetException e) {
                // This should've happened already...
                throw new IllegalArgumentException(
                    "Failed to set property \"" + propertyName + "\", cannot invoke setter", e);
            }
        }
    }

    private Object convertValue(Object propertyValue, Class<?> parameterType) {
        Class<?> propertyType = propertyValue.getClass();

        // Type converter is highest priority since they are explicitly registered
        Function<Object, Object> typeConverter = getTypeConverter(propertyType, parameterType);
        if (typeConverter == null) {
            LOG.debug("No type converter for {} to {}", propertyType, parameterType);
        } else {
            try {
                return typeConverter.apply(propertyValue);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                    "Failed invoking type converter from " + propertyType.getName() + " to " + parameterType.getName(), e);
            }
        }

        // TODO: Search for static valueOf method
        Method[] methods = parameterType.getMethods();

        // valueOf is used by boxed types and enums
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) &&
                method.getReturnType() == parameterType &&
                method.getName().equals("valueOf") &&
                method.getParameterTypes().length == 1) {
                try {
                    return method.invoke(null, propertyValue);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalArgumentException("Cannot invoke " + parameterType.getName() + ".valueOf", e);
                } catch (IllegalArgumentException e) {
                    // Argument type mismatch
                    LOG.debug("{}.valueOf failed", method.toString(), e);
                }
            }
        }

        // parse is used by java.time types
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) &&
                method.getReturnType() == parameterType &&
                method.getName().equals("parse") &&
                method.getParameterTypes().length == 1) {
                try {
                    return method.invoke(null, propertyValue);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalArgumentException("Cannot invoke " + parameterType.getName() + ".parse", e);
                } catch (IllegalArgumentException e) {
                    // Argument type mismatch
                    LOG.debug("{}.parse failed", parameterType.getName(), e);
                }
            }
        }

        // TODO: constructor is lowest priority since it always allocates a new instance
        for (Constructor<?> ctor : parameterType.getConstructors()) {
            if (ctor.getParameterTypes().length == 1) {
                try {
                    return ctor.newInstance(propertyValue);
                } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                    throw new IllegalArgumentException("Cannot construct " + parameterType.getName(), e);
                } catch (IllegalArgumentException e) {
                    // Argument type mismatch
                    LOG.debug("{} constructor failed", parameterType.getName(), e);
                }
            }
        }

        throw new IllegalArgumentException("Cannot convert " + propertyType.getName()  + " to " + parameterType.getName());
    }

    @SuppressWarnings("unchecked")
    private Function<Object, Object> getTypeConverter(Class<?> in, Class<?> out) {
        return (Function) typeConverters.get(new TypeConverterKey(toBoxedType(in), toBoxedType(out)));
    }

    private static Class<?> toBoxedType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        } else if (type == boolean.class) {
            return Boolean.class;
        } else if (type == byte.class) {
            return Byte.class;
        } else if (type == short.class) {
            return Short.class;
        } else if (type == int.class) {
            return Integer.class;
        } else if (type == long.class) {
            return Long.class;
        } else if (type == char.class) {
            return Character.class;
        } else if (type == float.class) {
            return Float.class;
        } else if (type == double.class) {
            return Double.class;
        } else {
            throw new IllegalArgumentException("Unknown primitive type?! " + type.getName());
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
