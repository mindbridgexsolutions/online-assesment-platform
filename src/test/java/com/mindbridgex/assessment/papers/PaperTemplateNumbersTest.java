package com.mindbridgex.assessment.papers;

import com.mindbridgex.assessment.domain.Bloom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The paper template writes Bloom's level as a number from 1 to 6. */
class PaperTemplateNumbersTest {
    @Test
    void bloomNumbersMapToLevels() {
        assertEquals(Bloom.REMEMBER, Bloom.parse("1"));
        assertEquals(Bloom.APPLY, Bloom.parse(" 3 "));
        assertEquals(Bloom.CREATE, Bloom.parse("6"));
        assertEquals(Bloom.ANALYZE, Bloom.parse("Analyze"));
    }

    @Test
    void bloomOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Bloom.parse("7"));
        assertThrows(IllegalArgumentException.class, () -> Bloom.parse("0"));
    }
}
