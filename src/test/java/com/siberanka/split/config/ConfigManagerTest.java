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

        ConfigurationSection mappedSection = yaml.getConfigurationSection("adaptive_mapped_number");
        assertNotNull(mappedSection);
        AdaptivePlaceholder mapped = ConfigManager.parseAdaptivePlaceholder("adaptive_mapped_number", mappedSection);
        assertEquals(com.siberanka.split.util.AdaptiveSpacingCalculator.Mode.MAP, mapped.getMode());
        assertEquals(100, mapped.getMinimum());
        assertEquals(-100, mapped.getMaximum());
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
    void parsesMapModeAndNegativeOutputRange() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                source: "%player_name%"
                calculation:
                  mode: map
                  source-minimum: 2
                  source-maximum: 12
                  minimum: -20
                  maximum: 30
                result:
                  type: number
                """);

        AdaptivePlaceholder placeholder = ConfigManager.parseAdaptivePlaceholder("mapped", yaml);
        assertEquals(com.siberanka.split.util.AdaptiveSpacingCalculator.Mode.MAP, placeholder.getMode());
        assertEquals(-20, placeholder.getMinimum());
        assertEquals(30, placeholder.getMaximum());
        assertEquals(2, placeholder.getMapSourceMinimum());
        assertEquals(12, placeholder.getMapSourceMaximum());
    }

    @Test
    void acceptsDescendingMapEndpointsButRejectsDescendingDirectRange() throws Exception {
        YamlConfiguration descendingMap = new YamlConfiguration();
        descendingMap.loadFromString("""
                source: "%player_name%"
                calculation:
                  mode: map
                  source-minimum: 0
                  source-maximum: 20
                  minimum: 100
                  maximum: -100
                """);

        AdaptivePlaceholder mapped = ConfigManager.parseAdaptivePlaceholder("descending-map", descendingMap);
        assertEquals(100, mapped.getMinimum());
        assertEquals(-100, mapped.getMaximum());

        YamlConfiguration descendingDirect = new YamlConfiguration();
        descendingDirect.loadFromString("""
                source: "%player_name%"
                calculation:
                  mode: direct
                  minimum: 100
                  maximum: -100
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigManager.parseAdaptivePlaceholder("descending-direct", descendingDirect)
        );
    }

    @Test
    void rejectsRangesBelowNegativeSafetyLimitAndInvalidMapSourceRange() throws Exception {
        YamlConfiguration unsafeOutput = new YamlConfiguration();
        unsafeOutput.loadFromString("""
                source: "%player_name%"
                calculation:
                  minimum: -4097
                  maximum: 0
                """);
        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigManager.parseAdaptivePlaceholder("unsafe-negative", unsafeOutput)
        );

        YamlConfiguration invalidMap = new YamlConfiguration();
        invalidMap.loadFromString("""
                source: "%player_name%"
                calculation:
                  mode: map
                  source-minimum: 20
                  source-maximum: 10
                """);
        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigManager.parseAdaptivePlaceholder("invalid-map", invalidMap)
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
