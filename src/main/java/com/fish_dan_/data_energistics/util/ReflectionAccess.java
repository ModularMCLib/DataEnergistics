package com.fish_dan_.data_energistics.util;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionAccess {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Map<MethodLookupKey, Optional<MethodHandle>> VIRTUAL_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Method, Optional<MethodHandle>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<FieldLookupKey, Optional<VarHandle>> FIELD_CACHE = new ConcurrentHashMap<>();

    private ReflectionAccess() {}

    public static boolean hasNoArgMethod(Class<?> type, String methodName) {
        return getNoArgVirtualMethod(type, methodName).isPresent();
    }

    @Nullable
    public static Object invokeNoArg(@Nullable Object target, String methodName) {
        if (target == null) {
            return null;
        }

        Optional<MethodHandle> method = getNoArgVirtualMethod(target.getClass(), methodName);
        if (method.isEmpty()) {
            return null;
        }

        try {
            return method.get().invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Optional<VarHandle> findField(Class<?> owner, String fieldName) {
        return FIELD_CACHE.computeIfAbsent(new FieldLookupKey(owner, fieldName), ReflectionAccess::findInstanceField);
    }

    @Nullable
    public static Object getField(Optional<VarHandle> handle, @Nullable Object target) {
        if (handle.isEmpty()) {
            return null;
        }

        try {
            return target == null ? handle.get().get() : handle.get().get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    public static Object invoke(Method method, @Nullable Object target, Object... args) {
        Optional<MethodHandle> handle = METHOD_CACHE.computeIfAbsent(method, ReflectionAccess::unreflectMethod);
        if (handle.isEmpty()) {
            return null;
        }

        Object[] arguments = target == null ? args : prependTarget(target, args);
        try {
            return handle.get().invokeWithArguments(arguments);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Optional<MethodHandle> getNoArgVirtualMethod(Class<?> type, String methodName) {
        return VIRTUAL_METHOD_CACHE.computeIfAbsent(
                new MethodLookupKey(type, methodName),
                ReflectionAccess::findNoArgVirtualMethod);
    }

    private static Optional<MethodHandle> findNoArgVirtualMethod(MethodLookupKey key) {
        Class<?> type = key.type();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(key.methodName());
                method.setAccessible(true);
                return Optional.of(MethodHandles.privateLookupIn(type, LOOKUP).unreflect(method));
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | SecurityException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<MethodHandle> unreflectMethod(Method method) {
        try {
            method.setAccessible(true);
            return Optional.of(MethodHandles.privateLookupIn(method.getDeclaringClass(), LOOKUP).unreflect(method));
        } catch (IllegalAccessException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<VarHandle> findInstanceField(FieldLookupKey key) {
        Class<?> type = key.owner();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(key.fieldName());
                field.setAccessible(true);
                return Optional.of(MethodHandles.privateLookupIn(type, LOOKUP).unreflectVarHandle(field));
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | SecurityException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Object[] prependTarget(Object target, Object[] args) {
        Object[] arguments = new Object[args.length + 1];
        arguments[0] = target;
        System.arraycopy(args, 0, arguments, 1, args.length);
        return arguments;
    }

    private record MethodLookupKey(Class<?> type, String methodName) {}

    private record FieldLookupKey(Class<?> owner, String fieldName) {}
}
