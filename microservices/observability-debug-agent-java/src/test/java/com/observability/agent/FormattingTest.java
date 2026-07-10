package com.observability.agent;

import com.observability.agent.util.Formatting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormattingTest {

    @Test
    void formatBytesMbAndGb() {
        assertThat(Formatting.formatBytes(536_870_912)).contains("MB");
        assertThat(Formatting.formatBytes(1_073_741_824)).contains("GB");
        assertThat(Formatting.formatBytes(536_870_912)).contains("512.00");
        assertThat(Formatting.formatBytes(1_073_741_824)).contains("1.00");
    }

    @Test
    void formatCountRoundsToWholeNumber() {
        assertThat(Formatting.formatCount(69.43)).isEqualTo("69");
        assertThat(Formatting.formatCount(69.6)).isEqualTo("70");
    }

    @Test
    void formatRpsAdaptivePrecision() {
        assertThat(Formatting.formatRps(2.75)).isEqualTo("2.75 rps");
        assertThat(Formatting.formatRps(12.3)).isEqualTo("12.3 rps");
        assertThat(Formatting.formatRps(150.0)).isEqualTo("150 rps");
    }

    @Test
    void formatPercentOneDecimal() {
        assertThat(Formatting.formatPercent(72.387)).isEqualTo("72.4%");
        assertThat(Formatting.formatPercent(-5)).isEqualTo("0.0%");
    }
}
