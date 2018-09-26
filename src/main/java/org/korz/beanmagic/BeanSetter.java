package org.korz.beanmagic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class BeanSetter {
    private static final Logger LOG = LoggerFactory.getLogger(BeanSetter.class);

    private final boolean errorOnUnused;
    private final Map<TypeConverterKey, Function<?, ?>> typeConverters;

    public BeanSetter() {
        this(newBuilder().addDefaultTypeConverters());
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

        public Builder addDefaultTypeConverters() {
            // booleans
            addTypeConverter(String.class, boolean.class, Boolean::valueOf);
            addTypeConverter(String.class, Boolean.class, Boolean::valueOf);
            // characters (not official)
            addTypeConverter(String.class, char.class, s -> s.charAt(0));
            addTypeConverter(String.class, Character.class, s -> s.charAt(0));
            // integers
            addTypeConverter(String.class, byte.class, Byte::valueOf);
            addTypeConverter(String.class, Byte.class, Byte::valueOf);
            addTypeConverter(String.class, short.class, Short::valueOf);
            addTypeConverter(String.class, Short.class, Short::valueOf);
            addTypeConverter(String.class, int.class, Integer::valueOf);
            addTypeConverter(String.class, Integer.class, Integer::valueOf);
            addTypeConverter(String.class, long.class, Long::valueOf);
            addTypeConverter(String.class, Long.class, Long::valueOf);
            addTypeConverter(String.class, BigInteger.class, BigInteger::new);
            // decimals/floats
            addTypeConverter(String.class, float.class, Float::valueOf);
            addTypeConverter(String.class, Float.class, Float::valueOf);
            addTypeConverter(String.class, double.class, Double::valueOf);
            addTypeConverter(String.class, Double.class, Double::valueOf);
            addTypeConverter(String.class, BigDecimal.class, BigDecimal::new);
            // class
            addTypeConverter(String.class, Class.class, s -> {
                try {
                    return Class.forName(s);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            });
            // time
            addTypeConverter(String.class, Instant.class, Instant::parse);
            addTypeConverter(String.class, LocalDate.class, LocalDate::parse);
            addTypeConverter(String.class, LocalDateTime.class, LocalDateTime::parse);
            addTypeConverter(String.class, LocalTime.class, LocalTime::parse);
            addTypeConverter(String.class, OffsetDateTime.class, OffsetDateTime::parse);
            addTypeConverter(String.class, ZonedDateTime.class, ZonedDateTime::parse);
            addTypeConverter(String.class, OffsetTime.class, OffsetTime::parse);
            addTypeConverter(String.class, Duration.class, Duration::parse);
            addTypeConverter(String.class, Period.class, Period::parse);
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
                    @SuppressWarnings("unchecked") // type parameters enforced in addTypeConverter
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
