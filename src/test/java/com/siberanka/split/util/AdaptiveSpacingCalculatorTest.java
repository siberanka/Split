package com.siberanka.split.util;

import com.siberanka.split.util.AdaptiveSpacingCalculator.Mode;
import com.siberanka.split.util.AdaptiveSpacingCalculator.Rounding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdaptiveSpacingCalculatorTest {

    @Test
    void calculatesDirectAndInverseValues() {
        assertEquals(10, AdaptiveSpacingCalculator.calculate(5, Mode.DIRECT, 1.5, 2, 0, 32, Rounding.NEAREST));
        assertEquals(13, AdaptiveSpacingCalculator.calculate(5, Mode.INVERSE, 1.5, 20, 0, 32, Rounding.NEAREST));
    }

    @Test
    void roundsAndClampsWithinConfiguredRange() {
        assertEquals(3, AdaptiveSpacingCalculator.calculate(1, Mode.DIRECT, 0.2, 2.1, 0, 10, Rounding.CEILING));
        assertEquals(2, AdaptiveSpacingCalculator.calculate(1, Mode.DIRECT, 0.2, 2.1, 0, 10, Rounding.FLOOR));
        assertEquals(4, AdaptiveSpacingCalculator.calculate(100, Mode.INVERSE, 10, 10, 4, 20, Rounding.NEAREST));
        assertEquals(20, AdaptiveSpacingCalculator.calculate(100, Mode.DIRECT, 10, 10, 4, 20, Rounding.NEAREST));
    }

    @Test
    void countsUnicodeCharactersAndOptionalWhitespace() {
        String value = "&aHi §b🌟";
        assertEquals(4, AdaptiveSpacingCalculator.countCharacters(value, false, true, true, 100));
        assertEquals(3, AdaptiveSpacingCalculator.countCharacters(value, false, true, false, 100));
        assertEquals(8, AdaptiveSpacingCalculator.countCharacters(value, false, false, true, 100));
    }

    @Test
    void stripsHexColorsAndTrimsBeforeCounting() {
        assertEquals(4, AdaptiveSpacingCalculator.countCharacters("  &#12ab34Test  ", true, true, true, 100));
    }

    @Test
    void capsSourceInspectionByUnicodeCodePoint() {
        assertEquals(2, AdaptiveSpacingCalculator.countCharacters("🌟AB", false, false, true, 2));
        assertEquals("🌟A", AdaptiveSpacingCalculator.truncateToCodePoints("🌟AB", 2));
    }

    @Test
    void rejectsUnsafeCalculationArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> AdaptiveSpacingCalculator.calculate(1, Mode.DIRECT, -1, 0, 0, 10, Rounding.NEAREST));
        assertThrows(IllegalArgumentException.class,
                () -> AdaptiveSpacingCalculator.calculate(1, Mode.DIRECT, 1, 0, 10, 5, Rounding.NEAREST));
    }
}
