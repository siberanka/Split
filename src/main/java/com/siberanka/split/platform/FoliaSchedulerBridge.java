package com.siberanka.split.platform;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Reflection boundary for Folia's scheduler API. Keeping Folia-only types out of
 * method descriptors preserves runtime compatibility with Spigot while still
 * using the official schedulers whenever Folia is detected.
 */
final class FoliaSchedulerBridge {

    static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

    private final Object server;
    private final Plugin plugin;

    FoliaSchedulerBridge(Object server, Plugin plugin) {
        this.server = Objects.requireNonNull(server, "server");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    static boolean isFolia() {
        return isClassPresent(FOLIA_MARKER, FoliaSchedulerBridge.class.getClassLoader());
    }

    static boolean isClassPresent(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    void runAsync(Runnable task) {
        Object scheduler = invokeRequired(server, "getAsyncScheduler");
        Consumer<Object> consumer = ignored -> task.run();
        invokeRequired(scheduler, "runNow", plugin, consumer);
    }

    void runGlobal(Runnable task) {
        Object scheduler = invokeRequired(server, "getGlobalRegionScheduler");
        invokeRequired(scheduler, "execute", plugin, task);
    }

    boolean runEntity(Object entity, Runnable task) {
        Objects.requireNonNull(entity, "entity");
        Object scheduler = invokeRequired(entity, "getScheduler");
        Runnable retired = () -> { };
        Object scheduled = invokeRequired(scheduler, "execute", plugin, task, retired, 1L);
        return Boolean.TRUE.equals(scheduled);
    }

    void cancelAll() {
        RuntimeException failure = null;
        try {
            Object asyncScheduler = invokeRequired(server, "getAsyncScheduler");
            invokeRequired(asyncScheduler, "cancelTasks", plugin);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            Object globalScheduler = invokeRequired(server, "getGlobalRegionScheduler");
            invokeRequired(globalScheduler, "cancelTasks", plugin);
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    static Object invokeRequired(Object target, String methodName, Object... arguments) {
        Objects.requireNonNull(target, "target");
        Method method = findCompatibleMethod(target.getClass(), methodName, arguments);
        if (method == null) {
            throw new IllegalStateException("Required Folia method is unavailable: "
                    + target.getClass().getName() + '#' + methodName);
        }
        try {
            if (!method.canAccess(target) && !method.trySetAccessible()) {
                throw new IllegalStateException("Cannot access Folia method: " + methodName);
            }
            return method.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access Folia method: " + methodName, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Folia method failed: " + methodName, cause);
        }
    }

    private static Method findCompatibleMethod(Class<?> type, String methodName, Object[] arguments) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != arguments.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (!isCompatible(parameterTypes[index], arguments[index])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        return null;
    }

    private static boolean isCompatible(Class<?> parameterType, Object argument) {
        if (argument == null) {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isInstance(argument);
        }
        return (parameterType == boolean.class && argument instanceof Boolean)
                || (parameterType == byte.class && argument instanceof Byte)
                || (parameterType == short.class && argument instanceof Short)
                || (parameterType == int.class && argument instanceof Integer)
                || (parameterType == long.class && argument instanceof Long)
                || (parameterType == float.class && argument instanceof Float)
                || (parameterType == double.class && argument instanceof Double)
                || (parameterType == char.class && argument instanceof Character);
    }
}
