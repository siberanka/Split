package com.siberanka.split.placeholder.model;

import com.siberanka.split.SplitPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ParsePlaceholderTest {

    @Test
    void resolvesSelectorWithRequesterAndSelectedValueWithOnlineTarget() {
        Player requester = player("Requester", UUID.randomUUID(), true);
        Player target = player("Target", UUID.randomUUID(), true);
        AtomicReference<String> lookedUpName = new AtomicReference<>();
        ParsePlaceholder placeholder = placeholder(
                "%target_selector%",
                "%target_value%",
                "%bedrock_value%",
                true,
                128,
                (player, template) -> template.equals("%target_selector%")
                        ? "  Target  "
                        : player.getName() + ":java",
                lookup(target, null, lookedUpName, null),
                false,
                immediateRouter()
        );

        assertEquals("Target:java", placeholder.resolve(null, requester));
        assertEquals("Target", lookedUpName.get());
        assertFalse(placeholder.shouldResolveNestedPlaceholders());
    }

    @Test
    void selectsBedrockTemplateUsingTargetContextAndCapsUnicodeOutput() {
        Player target = player(".BedrockUser", UUID.randomUUID(), true);
        ParsePlaceholder placeholder = placeholder(
                "selector",
                "java-value",
                "%bedrock_value%",
                true,
                2,
                (player, template) -> template.equals("selector")
                        ? ".BedrockUser"
                        : "🌟AB",
                lookup(target, null, null, null),
                true,
                immediateRouter()
        );

        assertEquals("🌟A", placeholder.resolve(null, null));
    }

    @Test
    void resolvesKnownOfflinePlayerAndCachesItsResult() {
        OfflinePlayer offline = player("OfflineTarget", UUID.randomUUID(), false);
        AtomicInteger offlineLookups = new AtomicInteger();
        ParsePlaceholder placeholder = placeholder(
                "%selector%",
                "%offline_value%",
                "%bedrock_value%",
                true,
                128,
                (player, template) -> template.equals("%selector%")
                        ? "OfflineTarget"
                        : player.getName() + ":offline",
                lookup(null, offline, null, offlineLookups),
                false,
                immediateRouter()
        );

        assertEquals("OfflineTarget:offline", placeholder.resolve(null, null));
        assertEquals("OfflineTarget:offline", placeholder.resolve(null, null));
        assertEquals(1, offlineLookups.get());
    }

    @Test
    void honorsOfflineToggleAndBoundsUnknownProfileLookups() {
        AtomicInteger offlineLookups = new AtomicInteger();
        ParsePlaceholder.TargetPlayerLookup lookup = lookup(null, null, null, offlineLookups);
        AtomicReference<String> selectedName = new AtomicReference<>("UnknownOne");
        ParsePlaceholder bounded = new ParsePlaceholder(
                "%selector%",
                "value",
                "value",
                true,
                250,
                1,
                128,
                (player, template) -> selectedName.get(),
                lookup,
                (plugin, player) -> false,
                immediateRouter()
        );

        assertEquals("", bounded.resolve(null, null));
        selectedName.set("UnknownTwo");
        assertEquals("", bounded.resolve(null, null));
        assertEquals(1, offlineLookups.get());

        ParsePlaceholder onlineOnly = new ParsePlaceholder(
                "%selector%",
                "value",
                "value",
                false,
                250,
                1,
                128,
                (player, template) -> "OfflineTarget",
                lookup,
                (plugin, player) -> false,
                immediateRouter()
        );
        assertEquals("", onlineOnly.resolve(null, null));
        assertEquals(1, offlineLookups.get());
    }

    @Test
    void rejectsMalformedTargetBeforeAnyProfileLookup() {
        AtomicInteger lookups = new AtomicInteger();
        ParsePlaceholder malformed = placeholder(
                "%selector%",
                "value",
                "value",
                true,
                128,
                (player, template) -> "Bad/Target",
                lookup(null, null, lookups, lookups),
                false,
                immediateRouter()
        );

        assertEquals("", malformed.resolve(null, null));
        assertEquals(0, lookups.get());
    }

    @Test
    void schedulesOnlineTargetWithoutBlockingAndCoalescesRefreshes() {
        Player target = player("Target", UUID.randomUUID(), true);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        AtomicInteger schedules = new AtomicInteger();
        ParsePlaceholder.TargetExecutionRouter router = new ParsePlaceholder.TargetExecutionRouter() {
            @Override
            public boolean requiresScheduling(SplitPlugin plugin, OfflinePlayer player) {
                return true;
            }

            @Override
            public boolean schedule(SplitPlugin plugin, OfflinePlayer player, Runnable task) {
                schedules.incrementAndGet();
                scheduled.set(task);
                return true;
            }
        };
        ParsePlaceholder placeholder = placeholder(
                "%selector%",
                "%value%",
                "%value%",
                true,
                128,
                (player, template) -> template.equals("%selector%") ? "Target" : "resolved",
                lookup(target, null, null, null),
                false,
                router
        );

        assertEquals("", placeholder.resolve(null, null));
        assertEquals("", placeholder.resolve(null, null));
        assertEquals(1, schedules.get());
        scheduled.get().run();
        assertEquals("resolved", placeholder.resolve(null, null));
        assertEquals(1, schedules.get());
    }

    private static ParsePlaceholder placeholder(
            String selector,
            String javaValue,
            String bedrockValue,
            boolean allowOffline,
            int maximumOutputLength,
            ParsePlaceholder.TemplateResolver templateResolver,
            ParsePlaceholder.TargetPlayerLookup targetLookup,
            boolean bedrock,
            ParsePlaceholder.TargetExecutionRouter executionRouter
    ) {
        return new ParsePlaceholder(
                selector,
                javaValue,
                bedrockValue,
                allowOffline,
                250,
                16,
                maximumOutputLength,
                templateResolver,
                targetLookup,
                (plugin, player) -> bedrock,
                executionRouter
        );
    }

    private static ParsePlaceholder.TargetPlayerLookup lookup(
            OfflinePlayer online,
            OfflinePlayer offline,
            Object onlineObserver,
            AtomicInteger offlineLookups
    ) {
        return new ParsePlaceholder.TargetPlayerLookup() {
            @Override
            public OfflinePlayer findOnline(SplitPlugin plugin, String exactName) {
                if (onlineObserver instanceof AtomicReference<?> reference) {
                    @SuppressWarnings("unchecked")
                    AtomicReference<String> names = (AtomicReference<String>) reference;
                    names.set(exactName);
                } else if (onlineObserver instanceof AtomicInteger counter) {
                    counter.incrementAndGet();
                }
                return online;
            }

            @Override
            public OfflinePlayer findKnownOffline(SplitPlugin plugin, String exactName) {
                if (offlineLookups != null) {
                    offlineLookups.incrementAndGet();
                }
                return offline;
            }

            @Override
            public OfflinePlayer findById(SplitPlugin plugin, UUID playerId) {
                if (offline != null && offline.getUniqueId().equals(playerId)) {
                    return offline;
                }
                return online != null && online.getUniqueId().equals(playerId) ? online : null;
            }
        };
    }

    private static ParsePlaceholder.TargetExecutionRouter immediateRouter() {
        return new ParsePlaceholder.TargetExecutionRouter() {
            @Override
            public boolean requiresScheduling(SplitPlugin plugin, OfflinePlayer player) {
                return false;
            }

            @Override
            public boolean schedule(SplitPlugin plugin, OfflinePlayer player, Runnable task) {
                task.run();
                return true;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends OfflinePlayer> T player(String name, UUID uniqueId, boolean online) {
        Class<?> type = online ? Player.class : OfflinePlayer.class;
        return (T) Proxy.newProxyInstance(
                ParsePlaceholderTest.class.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> uniqueId;
                    case "isOnline" -> online;
                    case "hasPlayedBefore" -> true;
                    case "toString" -> "PlayerProxy[" + name + "]";
                    default -> primitiveDefault(method.getReturnType());
                }
        );
    }

    private static Object primitiveDefault(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }
}
