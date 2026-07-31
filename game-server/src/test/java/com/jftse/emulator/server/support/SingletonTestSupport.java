package com.jftse.emulator.server.support;

import java.lang.reflect.Field;

public final class SingletonTestSupport {
    private SingletonTestSupport() {
    }

    public static Object replace(Class<?> owner, String fieldName, Object replacement) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object previous = field.get(null);
            field.set(null, replacement);
            return previous;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to replace " + owner.getName() + "." + fieldName, exception);
        }
    }
}
