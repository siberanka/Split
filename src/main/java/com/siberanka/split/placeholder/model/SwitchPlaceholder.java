package com.siberanka.split.placeholder.model;

import com.siberanka.split.SplitPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public class SwitchPlaceholder extends SplitPlaceholder {
    private final String switchVal;
    private final Map<String, String> cases;

    public SwitchPlaceholder(String switchVal, Map<String, String> cases) {
        super("switch");
        this.switchVal = switchVal != null ? switchVal : "";
        this.cases = cases != null ? cases : Collections.emptyMap();
    }

    public String getSwitchVal() {
        return switchVal;
    }

    public Map<String, String> getCases() {
        return cases;
    }

    @Override
    public String resolve(SplitPlugin plugin, OfflinePlayer player) {
        if (switchVal.isEmpty()) {
            return cases.getOrDefault("default", "");
        }

        // Evaluate the target string (e.g. %luckperms_highest_group_by_weight%)
        String resolvedTarget = PlaceholderAPI.setPlaceholders(player, switchVal);
        if (resolvedTarget == null) {
            resolvedTarget = "";
        }
        String targetLower = resolvedTarget.toLowerCase(Locale.ROOT);

        // 1. Try case-insensitive lookup
        String result = cases.get(targetLower);
        if (result == null) {
            // 2. Try original case lookup
            result = cases.get(resolvedTarget);
        }
        if (result == null) {
            // 3. Fallback to default
            result = cases.get("default");
        }

        return result != null ? result : "";
    }
}
