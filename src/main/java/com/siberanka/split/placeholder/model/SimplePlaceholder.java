package com.siberanka.split.placeholder.model;

import com.siberanka.split.SplitPlugin;
import org.bukkit.OfflinePlayer;

public class SimplePlaceholder extends SplitPlaceholder {
    private final String javaVal;
    private final String bedrockVal;

    public SimplePlaceholder(String javaVal, String bedrockVal) {
        super("simple");
        this.javaVal = javaVal != null ? javaVal : "";
        this.bedrockVal = bedrockVal != null ? bedrockVal : "";
    }

    public String getJavaVal() {
        return javaVal;
    }

    public String getBedrockVal() {
        return bedrockVal;
    }

    @Override
    public String resolve(SplitPlugin plugin, OfflinePlayer player) {
        if (player == null || !player.isOnline()) {
            return plugin.getConfigManager().getConfigData().isDefaultToJavaOnNull() ? javaVal : "";
        }
        boolean isBedrock = plugin.isBedrock(player);
        return isBedrock ? bedrockVal : javaVal;
    }
}
