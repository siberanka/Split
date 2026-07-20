package com.siberanka.split.placeholder.model;

import com.siberanka.split.util.AdaptiveSpacingCalculator.Mode;
import com.siberanka.split.util.AdaptiveSpacingCalculator.Rounding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdaptivePlaceholderTest {

    @Test
    void returnsCalculatedNumber() {
        AdaptivePlaceholder placeholder = placeholder(
                AdaptivePlaceholder.ResultType.NUMBER,
                " ",
                "{count}",
                32
        );

        assertEquals("5", placeholder.resolve(null, null));
        assertFalse(placeholder.shouldResolveNestedPlaceholders());
    }

    @Test
    void repeatsSpacesOrCustomSymbols() {
        assertEquals("     ", placeholder(AdaptivePlaceholder.ResultType.REPEAT, " ", "", 32).resolve(null, null));
        assertEquals("-----", placeholder(AdaptivePlaceholder.ResultType.REPEAT, "-", "", 32).resolve(null, null));
        assertEquals("abcabcabcabcabc", placeholder(AdaptivePlaceholder.ResultType.REPEAT, "abc", "", 32).resolve(null, null));
        assertEquals("•••••", placeholder(AdaptivePlaceholder.ResultType.REPEAT, "•", "", 32).resolve(null, null));
        assertEquals("%x%%x%", placeholder(AdaptivePlaceholder.ResultType.REPEAT, "%x%", "", 6).resolve(null, null));
    }

    @Test
    void rendersCustomTemplate() {
        AdaptivePlaceholder placeholder = placeholder(
                AdaptivePlaceholder.ResultType.TEMPLATE,
                "•",
                "length={length}, count={count}, value={value}, source='{source}'",
                128
        );

        assertEquals("length=0, count=5, value=•, source=''", placeholder.resolve(null, null));
    }

    @Test
    void resolvesAndCombinesEveryConfiguredSourceThenCachesSilently() {
        AtomicInteger resolutions = new AtomicInteger();
        AdaptivePlaceholder placeholder = new AdaptivePlaceholder(
                List.of("%first%", "%second%"),
                "|",
                250L,
                16,
                Mode.DIRECT,
                1,
                0,
                0,
                100,
                Rounding.NEAREST,
                false,
                false,
                true,
                128,
                AdaptivePlaceholder.ResultType.TEMPLATE,
                " ",
                "{source}:{length}:{count}",
                128,
                (player, source) -> {
                    resolutions.incrementAndGet();
                    return source.equals("%first%") ? "AA" : "BBB";
                }
        );

        assertEquals("AA|BBB:6:6", placeholder.resolve(null, null));
        assertEquals("AA|BBB:6:6", placeholder.resolve(null, null));
        assertEquals(2, resolutions.get());
    }

    private static AdaptivePlaceholder placeholder(
            AdaptivePlaceholder.ResultType resultType,
            String resultValue,
            String resultTemplate,
            int maxOutputLength
    ) {
        return new AdaptivePlaceholder(
                "",
                Mode.DIRECT,
                1,
                5,
                0,
                20,
                Rounding.NEAREST,
                false,
                true,
                true,
                128,
                resultType,
                resultValue,
                resultTemplate,
                maxOutputLength
        );
    }
}
