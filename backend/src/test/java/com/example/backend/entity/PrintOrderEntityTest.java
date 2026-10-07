package com.example.backend.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class PrintOrderEntityTest {

    @Test
    void allowsTheSupportedQueueAndRetryTransitions() {
        PrintOrderEntity order = order();

        order.transitionTo(PrintOrderStatus.PRINT_REQUESTED, Instant.parse("2026-10-08T00:00:00Z"));
        order.transitionTo(PrintOrderStatus.QUEUED, Instant.parse("2026-10-08T00:00:01Z"));
        order.transitionTo(PrintOrderStatus.PRINTING, Instant.parse("2026-10-08T00:00:02Z"));
        order.transitionTo(PrintOrderStatus.FAILED, Instant.parse("2026-10-08T00:00:03Z"));
        order.transitionTo(PrintOrderStatus.PRINT_REQUESTED, Instant.parse("2026-10-08T00:00:04Z"));

        assertEquals(PrintOrderStatus.PRINT_REQUESTED, order.getStatus());
    }

    @Test
    void recordsPrintedAtOnlyWhenPrintingCompletesSuccessfully() {
        PrintOrderEntity order = order();
        Instant printedAt = Instant.parse("2026-10-08T00:00:05Z");

        order.transitionTo(PrintOrderStatus.PRINT_REQUESTED, Instant.parse("2026-10-08T00:00:01Z"));
        order.transitionTo(PrintOrderStatus.QUEUED, Instant.parse("2026-10-08T00:00:02Z"));
        order.transitionTo(PrintOrderStatus.PRINTING, Instant.parse("2026-10-08T00:00:03Z"));
        order.transitionTo(PrintOrderStatus.PRINTED, printedAt);

        assertEquals(PrintOrderStatus.PRINTED, order.getStatus());
        assertEquals(printedAt, order.getPrintedAt());
    }

    @Test
    void rejectsInvalidAndTerminalStateTransitions() {
        PrintOrderEntity order = order();

        assertThrows(
                InvalidOrderTransitionException.class,
                () -> order.transitionTo(PrintOrderStatus.PRINTED, Instant.now()));
        order.transitionTo(PrintOrderStatus.CANCELLED, Instant.now());
        assertThrows(
                InvalidOrderTransitionException.class,
                () -> order.transitionTo(PrintOrderStatus.PRINT_REQUESTED, Instant.now()));
    }

    @Test
    void requiresTransitionTimeWhenMarkingOrderPrinted() {
        PrintOrderEntity order = order();
        order.transitionTo(PrintOrderStatus.PRINT_REQUESTED, Instant.now());
        order.transitionTo(PrintOrderStatus.QUEUED, Instant.now());
        order.transitionTo(PrintOrderStatus.PRINTING, Instant.now());

        assertThrows(
                InvalidOrderTransitionException.class,
                () -> order.transitionTo(PrintOrderStatus.PRINTED, null));
        assertNotNull(order.getStatus());
        assertEquals(PrintOrderStatus.PRINTING, order.getStatus());
    }

    private static PrintOrderEntity order() {
        return new PrintOrderEntity(
                "ABCDEFGHJKLM",
                new DocumentEntity("doc.pdf", "key", "application/pdf", 10, 1, "a".repeat(64)),
                1,
                PrintType.BLACK_AND_WHITE,
                1,
                new BigDecimal("1.00"),
                1,
                new BigDecimal("1.00"),
                "A4",
                "portrait",
                null,
                false);
    }
}
