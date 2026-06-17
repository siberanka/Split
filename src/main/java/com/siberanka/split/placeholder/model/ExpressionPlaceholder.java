package com.siberanka.split.placeholder.model;

import com.siberanka.split.SplitPlugin;
import com.siberanka.split.util.ExpressionEvaluator;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

public class ExpressionPlaceholder extends SplitPlaceholder {
    private final String formule;
    private final String trueVal;
    private final String falseVal;

    public ExpressionPlaceholder(String formule, String trueVal, String falseVal) {
        super("expression");
        this.formule = formule != null ? formule : "";
        this.trueVal = trueVal != null ? trueVal : "";
        this.falseVal = falseVal != null ? falseVal : "";
    }

    public String getFormule() {
        return formule;
    }

    public String getTrueVal() {
        return trueVal;
    }

    public String getFalseVal() {
        return falseVal;
    }

    @Override
    public String resolve(SplitPlugin plugin, OfflinePlayer player) {
        if (formule.isEmpty()) {
            return falseVal;
        }

        // Evaluate placeholders in the formula first (e.g. %player_ping% >> 60)
        String resolvedFormula = PlaceholderAPI.setPlaceholders(player, formule);

        boolean debug = plugin.getConfigManager().getConfigData().isDebug();
        boolean result = ExpressionEvaluator.evaluate(resolvedFormula, plugin.getLogger(), debug);

        return result ? trueVal : falseVal;
    }
}
