package com.yoedi.epos.sale;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceNumberGeneratorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 16);

    @Test
    void format_matchesInvYyyyMmDdNnnn() {
        assertThat(InvoiceNumberGenerator.format(DAY, 1)).isEqualTo("INV-20260916-0001");
        assertThat(InvoiceNumberGenerator.format(DAY, 42)).isEqualTo("INV-20260916-0042");
        assertThat(InvoiceNumberGenerator.format(DAY, 9999)).isEqualTo("INV-20260916-9999");
        assertThat(InvoiceNumberGenerator.format(DAY, 1)).matches("INV-\\d{8}-\\d{4}");
    }

    @Test
    void format_rejectsNonPositiveSequence() {
        assertThatThrownBy(() -> InvoiceNumberGenerator.format(DAY, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void prefix_containsDate() {
        assertThat(InvoiceNumberGenerator.prefix(DAY)).isEqualTo("INV-20260916-");
    }

    @Test
    void next_startsAtOneWhenNoInvoiceYet() {
        assertThat(InvoiceNumberGenerator.next(DAY, null)).isEqualTo("INV-20260916-0001");
    }

    @Test
    void next_incrementsLastSequenceOfSameDay() {
        assertThat(InvoiceNumberGenerator.next(DAY, "INV-20260916-0001")).isEqualTo("INV-20260916-0002");
        assertThat(InvoiceNumberGenerator.next(DAY, "INV-20260916-0099")).isEqualTo("INV-20260916-0100");
    }

    @Test
    void next_resetsSequenceOnNewDay() {
        LocalDate nextDay = DAY.plusDays(1);

        assertThat(InvoiceNumberGenerator.next(nextDay, "INV-20260916-0057")).isEqualTo("INV-20260917-0001");
    }

    @Test
    void next_resetsSequenceAcrossMonthAndYearBoundary() {
        assertThat(InvoiceNumberGenerator.next(LocalDate.of(2026, 10, 1), "INV-20260930-0012"))
                .isEqualTo("INV-20261001-0001");
        assertThat(InvoiceNumberGenerator.next(LocalDate.of(2027, 1, 1), "INV-20261231-0300"))
                .isEqualTo("INV-20270101-0001");
    }

    @Test
    void next_rejectsMalformedInvoiceNumberOfSameDay() {
        assertThatThrownBy(() -> InvoiceNumberGenerator.next(DAY, "INV-20260916-abcd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void businessDate_usesAsiaJakartaZone() {
        // 16:59:59 UTC = 23:59:59 WIB, still 16 Sept
        assertThat(InvoiceNumberGenerator.businessDate(Instant.parse("2026-09-16T16:59:59Z")))
                .isEqualTo(LocalDate.of(2026, 9, 16));
        // 17:00:00 UTC = 00:00:00 WIB, already 17 Sept
        assertThat(InvoiceNumberGenerator.businessDate(Instant.parse("2026-09-16T17:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 9, 17));
    }

    @Test
    void dayRollover_atJakartaMidnight_restartsSequence() {
        LocalDate beforeMidnight = InvoiceNumberGenerator.businessDate(Instant.parse("2026-09-16T16:59:59Z"));
        String lastOfDay = InvoiceNumberGenerator.next(beforeMidnight, "INV-20260916-0041");

        LocalDate afterMidnight = InvoiceNumberGenerator.businessDate(Instant.parse("2026-09-16T17:00:00Z"));
        String firstOfNextDay = InvoiceNumberGenerator.next(afterMidnight, lastOfDay);

        assertThat(lastOfDay).isEqualTo("INV-20260916-0042");
        assertThat(firstOfNextDay).isEqualTo("INV-20260917-0001");
    }
}
