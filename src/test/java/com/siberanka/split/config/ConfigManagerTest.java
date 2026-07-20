package com.siberanka.split.config;

import com.siberanka.split.placeholder.model.AdaptivePlaceholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigManagerTest {

    @Test
    void parsesBundledAdaptiveExample() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/placeholders.yml");
        assertNotNull(stream);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection section = yaml.getConfigurationSection("adaptive_spacing");
        assertNotNull(section);

        AdaptivePlaceholder placeholder = ConfigManager.parseAdaptivePlaceholder("adaptive_spacing", section);
        assertEquals("adaptive", placeholder.getType());
    }

    @Test
    void rejectsRangesAboveSafetyLimit() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                source: "%player_name%"
                calculation:
                  minimum: 0
                  maximum: 4097
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigManager.parseAdaptivePlaceholder("unsafe", yaml)
        );
    }

    @Test
    void acceptsFlatCompatibilityKeys() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                source: "%player_name%"
                mode: inverse
                ratio: 0.5
                base-spaces: 20
                min-spaces: 2
                max-spaces: 20
                output-mode: repeat
                value: "-"
                template: "{count} x {value}"
                """);

        AdaptivePlaceholder placeholder = ConfigManager.parseAdaptivePlaceholder("flat", yaml);
        assertEquals("adaptive", placeholder.getType());
        assertEquals(AdaptivePlaceholder.ResultType.REPEAT, placeholder.getResultType());
        assertEquals("-", placeholder.getResultValue());
        assertEquals("{count} x {value}", placeholder.getResultTemplate());
    }

    @Test
    void rejectsUnknownAdaptiveOutputType() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                source: "%player_name%"
                result:
                  type: executable
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigManager.parseAdaptivePlaceholder("invalid-output", yaml)
        );
    }

    @Test
    void preservesNestedPlainTextResultValue() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                source: "%player_name%"
                result:
                  type: repeat
                  value: "-"
                """);

        AdaptivePlaceholder placeholder = ConfigManager.parseAdaptivePlaceholder("dash", yaml);
        assertEquals(AdaptivePlaceholder.ResultType.REPEAT, placeholder.getResultType());
        assertEquals("-", placeholder.getResultValue());
    }

    @Test
    void parsesMultipleAdaptiveSourcesAndCooldownSettings() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                source:
                  - "%vault_eco_balance_formatted%"
                  - "%superior_island_level_format%"
                source-options:
                  separator: " | "
                  cooldown-milliseconds: 300
                  max-cache-entries: 64
                result:
                  type: number
                """);

        AdaptivePlaceholder placeholder = ConfigManager.parseAdaptivePlaceholder("multiple", yaml);
        assertEquals(List.of(
                "%vault_eco_balance_formatted%",
                "%superior_island_level_format%"
        ), placeholder.getSources());
        assertEquals(" | ", placeholder.getSourceSeparator());
        assertEquals(300L, placeholder.getCooldownMilliseconds());
    }

    @Test
    void rejectsCooldownBelowSafeMinimum() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                source: "%player_name%"
                source-options:
                  cooldown-milliseconds: 10
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigManager.parseAdaptivePlaceholder("unsafe-cooldown", yaml)
        );
    }
}
