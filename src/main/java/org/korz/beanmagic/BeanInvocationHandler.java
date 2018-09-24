package org.korz.beanmagic;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BeanInvocationHandler implements InvocationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(BeanInvocationHandler.class);

    private final Map<String, Object> properties = new HashMap<>();

    public BeanInvocationHandler(Class<?> beanInterface) {
        LOG.info("creating handler for interface: " + beanInterface.getName());
    }

    @Override // InvocationHandler
    public Object invoke(Object proxy, Method method, Object[] args) {
        String methodName = method.getName();
        Class<?> returnType = method.getReturnType();
        if (methodName.startsWith("get")) {
            String propertyName = removePrefix("get", methodName);
            return properties.get(propertyName);
        }
        else if (methodName.startsWith("set")) {
            String propertyName = removePrefix("set", methodName);
            properties.put(propertyName, args[0]);
            return null; // void
        }
        else if (isBoolean(returnType) && methodName.startsWith("is")) {
            String propertyName = removePrefix("is", methodName);
            return properties.get(propertyName);
        }
        else if (methodName.equals("toString")) {
            return properties.toString();
        }
        else if (methodName.equals("equals")) {
            // If both are same proxy class, compare properties maps
            if (proxy.getClass().equals(args[0].getClass())) {
                BeanInvocationHandler other = (BeanInvocationHandler) Proxy.getInvocationHandler(args[0]);
                return properties.equals(other.properties);
            }
            return false;
        }
        else if (methodName.equals("hashCode")) {
            return properties.hashCode();
        }
        throw new IllegalArgumentException("cannot handle method: " + methodName);
    }

    private static String removePrefix(String prefix, String word) {
        return Character.toLowerCase(word.charAt(prefix.length())) + word.substring(prefix.length() + 1);
    }

    private static boolean isBoolean(Class<?> clazz) {
        return clazz == boolean.class || clazz == Boolean.class;
    }
}
