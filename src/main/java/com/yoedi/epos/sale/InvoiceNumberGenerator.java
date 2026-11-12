package com.yoedi.epos.sale;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Builds invoice numbers in the format {@code INV-YYYYMMDD-NNNN}. No Spring, no database.
 * <p>
 * The date part is the business date in {@code Asia/Jakarta}, and the sequence resets every day.
 * {@code SaleService} looks up the last invoice number for the day (by {@link #prefix(LocalDate)})
 * and passes it to {@link #next(LocalDate, String)}.
 */
public final class InvoiceNumberGenerator {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Jakarta");

    private static final String PREFIX = "INV-";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private InvoiceNumberGenerator() {
    }

    /** Business date of a moment in time, in the Asia/Jakarta zone. */
    public static LocalDate businessDate(Instant instant) {
        return LocalDate.ofInstant(instant, BUSINESS_ZONE);
    }

    /** Prefix shared by all invoices of a day, e.g. {@code INV-20260916-}. */
    public static String prefix(LocalDate date) {
        return PREFIX + date.format(DATE_FORMAT) + "-";
    }

    /** Invoice number for a given date and sequence, e.g. {@code INV-20260916-0001}. */
    public static String format(LocalDate date, int sequence) {
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive: " + sequence);
        }
        return prefix(date) + String.format("%04d", sequence);
    }

    /**
     * Next invoice number for {@code date}.
     *
     * @param lastInvoiceNo the latest invoice number issued so far, or {@code null} if none.
     *                      If it belongs to a different day, the sequence starts again at 1.
     */
    public static String next(LocalDate date, String lastInvoiceNo) {
        String prefix = prefix(date);
        if (lastInvoiceNo == null || !lastInvoiceNo.startsWith(prefix)) {
            return format(date, 1);
        }
        String sequencePart = lastInvoiceNo.substring(prefix.length());
        int lastSequence;
        try {
            lastSequence = Integer.parseInt(sequencePart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid invoice number: " + lastInvoiceNo, e);
        }
        return format(date, Math.addExact(lastSequence, 1));
    }
}
