package com.siberanka.split.platform;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliaSchedulerBridgeTest {

    @Test
    void routesAsyncGlobalAndEntityWorkThroughFoliaSchedulers() {
        FakeServer server = new FakeServer();
        FoliaSchedulerBridge bridge = new FoliaSchedulerBridge(server, pluginProxy());
        AtomicInteger executions = new AtomicInteger();

        bridge.runAsync(executions::incrementAndGet);
        bridge.runGlobal(executions::incrementAndGet);
        assertTrue(bridge.runEntity(new FakeEntity(true), executions::incrementAndGet));

        assertEquals(3, executions.get());
        assertEquals(1L, FakeEntityScheduler.lastDelay);
    }

    @Test
    void reportsRetiredEntityAndCancelsOwnedSchedulerTasks() {
        FakeServer server = new FakeServer();
        FoliaSchedulerBridge bridge = new FoliaSchedulerBridge(server, pluginProxy());

        assertFalse(bridge.runEntity(new FakeEntity(false), () -> { }));
        bridge.cancelAll();

        assertTrue(server.asyncScheduler.cancelled);
        assertTrue(server.globalScheduler.cancelled);
    }

    @Test
    void stillCancelsGlobalTasksWhenAsyncCancellationFails() {
        FakeServer server = new FakeServer();
        server.asyncScheduler.failCancellation = true;
        FoliaSchedulerBridge bridge = new FoliaSchedulerBridge(server, pluginProxy());

        assertThrows(IllegalStateException.class, bridge::cancelAll);
        assertTrue(server.globalScheduler.cancelled);
    }

    @Test
    void failsClosedWhenRequiredSchedulerMethodIsUnavailable() {
        FoliaSchedulerBridge bridge = new FoliaSchedulerBridge(new Object(), pluginProxy());
        assertThrows(IllegalStateException.class, () -> bridge.runAsync(() -> { }));
    }

    @Test
    void classDetectionDoesNotInitializeOrRequireFoliaAtCompileTime() {
        ClassLoader loader = FoliaSchedulerBridgeTest.class.getClassLoader();
        assertTrue(FoliaSchedulerBridge.isClassPresent("java.lang.String", loader));
        assertFalse(FoliaSchedulerBridge.isClassPresent("invalid.split.MissingFoliaMarker", loader));
    }

    @Test
    void pluginMetadataExplicitlyEnablesFoliaLoading() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/plugin.yml");
        assertNotNull(stream);
        try (stream) {
            String pluginYaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(pluginYaml.contains("folia-supported: true"));
            assertTrue(pluginYaml.contains("version: '1.3.0'"));
        }
    }

    private static Plugin pluginProxy() {
        return (Plugin) Proxy.newProxyInstance(
                FoliaSchedulerBridgeTest.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> {
                    Class<?> returnType = method.getReturnType();
                    if (!returnType.isPrimitive()) {
                        return null;
                    }
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == char.class) {
                        return '\0';
                    }
                    return 0;
                }
        );
    }

    public static final class FakeServer {
        private final FakeAsyncScheduler asyncScheduler = new FakeAsyncScheduler();
        private final FakeGlobalScheduler globalScheduler = new FakeGlobalScheduler();

        public FakeAsyncScheduler getAsyncScheduler() {
            return asyncScheduler;
        }

        public FakeGlobalScheduler getGlobalRegionScheduler() {
            return globalScheduler;
        }
    }

    public static final class FakeAsyncScheduler {
        private boolean cancelled;
        private boolean failCancellation;

        public Object runNow(Plugin plugin, Consumer<Object> task) {
            task.accept(new Object());
            return new Object();
        }

        public void cancelTasks(Plugin plugin) {
            if (failCancellation) {
                throw new IllegalStateException("simulated cancellation failure");
            }
            cancelled = true;
        }
    }

    public static final class FakeGlobalScheduler {
        private boolean cancelled;

        public void execute(Plugin plugin, Runnable task) {
            task.run();
        }

        public void cancelTasks(Plugin plugin) {
            cancelled = true;
        }
    }

    public static final class FakeEntity {
        private final FakeEntityScheduler scheduler;

        private FakeEntity(boolean active) {
            this.scheduler = new FakeEntityScheduler(active);
        }

        public FakeEntityScheduler getScheduler() {
            return scheduler;
        }
    }

    public static final class FakeEntityScheduler {
        private static long lastDelay;
        private final boolean active;

        private FakeEntityScheduler(boolean active) {
            this.active = active;
        }

        public boolean execute(Plugin plugin, Runnable task, Runnable retired, long delay) {
            lastDelay = delay;
            if (active) {
                task.run();
            }
            return active;
        }
    }
}
