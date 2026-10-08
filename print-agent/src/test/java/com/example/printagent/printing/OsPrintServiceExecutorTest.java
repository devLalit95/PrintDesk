package com.example.printagent.printing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.print.Printable;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.print.Doc;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.event.PrintJobEvent;
import javax.print.event.PrintJobListener;
import com.example.printagent.printer.PrintServiceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OsPrintServiceExecutorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void reportsTheOperatingSystemCompletionEventWithoutClaimingPhysicalPaperOutput() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("document.pdf"), "valid test payload");
        AtomicInteger submitted = new AtomicInteger();
        FakeServices fakeServices = new FakeServices(true, submitted);
        OsPrintServiceExecutor executor = executor(fakeServices.provider(), Duration.ofSeconds(1));

        PrintOutcome outcome = executor.execute(validRequest(file));

        assertEquals(PrintOutcome.Status.OS_JOB_COMPLETED, outcome.status());
        org.junit.jupiter.api.Assertions.assertTrue(outcome.message().contains("does not confirm paper output"));
        assertEquals(1, submitted.get());
    }

    @Test
    void reportsAnUnknownOutcomeInsteadOfResubmittingWhenNoAcknowledgementArrives() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("document.pdf"), "valid test payload");
        AtomicInteger submitted = new AtomicInteger();
        FakeServices fakeServices = new FakeServices(false, submitted);
        OsPrintServiceExecutor executor = executor(fakeServices.provider(), Duration.ofMillis(1));

        PrintOutcome outcome = executor.execute(validRequest(file));

        assertEquals(PrintOutcome.Status.OUTCOME_UNKNOWN, outcome.status());
        assertEquals(1, submitted.get());
        org.junit.jupiter.api.Assertions.assertTrue(outcome.message().contains("not automatically resubmitted"));
    }

    @Test
    void refusesUnsupportedPrinterOptionsBeforeCreatingOrSubmittingAJob() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("document.pdf"), "valid test payload");
        AtomicInteger submitted = new AtomicInteger();
        FakeServices fakeServices = new FakeServices(true, submitted, false);
        OsPrintServiceExecutor executor = executor(fakeServices.provider(), Duration.ofSeconds(1));

        assertThrows(PrintJobValidationException.class, () -> executor.execute(validRequest(file)));
        assertEquals(0, submitted.get());
    }

    private OsPrintServiceExecutor executor(PrintServiceProvider provider, Duration timeout) {
        return new OsPrintServiceExecutor(
                provider,
                request -> new PrintDocument() {
                    @Override
                    public Printable printable() {
                        return (graphics, pageFormat, pageIndex) -> Printable.PAGE_EXISTS;
                    }

                    @Override
                    public int pageCount() {
                        return 1;
                    }

                    @Override
                    public void close() {
                    }
                },
                new PrintJobRequestValidator(),
                timeout);
    }

    private PrintJobRequest validRequest(Path document) {
        return new PrintJobRequest(
                java.util.UUID.randomUUID(),
                document,
                "application/pdf",
                "Test Printer",
                "BLACK_AND_WHITE",
                1,
                "A4",
                "portrait",
                false);
    }

    private static final class FakeServices {

        private final AtomicReference<PrintJobListener> listener = new AtomicReference<>();
        private final AtomicReference<DocPrintJob> printJobReference = new AtomicReference<>();
        private final PrintService printService;
        private final DocPrintJob printJob;

        private FakeServices(boolean sendCompletion, AtomicInteger submitted) {
            this(sendCompletion, submitted, true);
        }

        private FakeServices(boolean sendCompletion, AtomicInteger submitted, boolean supportsOptions) {
            printService = proxy(PrintService.class, (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> "Test Printer";
                case "isDocFlavorSupported" -> true;
                case "isAttributeValueSupported" -> supportsOptions;
                case "createPrintJob" -> printJobReference.get();
                case "toString" -> "Test Printer";
                default -> defaultValue(method.getReturnType());
            });
            printJob = proxy(DocPrintJob.class, (proxy, method, args) -> switch (method.getName()) {
                case "getPrintService" -> printService;
                case "addPrintJobListener" -> {
                    listener.set((PrintJobListener) args[0]);
                    yield null;
                }
                case "print" -> {
                    submitted.incrementAndGet();
                    if (sendCompletion) {
                        listener.get().printJobCompleted(new PrintJobEvent((DocPrintJob) proxy, PrintJobEvent.JOB_COMPLETE));
                    }
                    yield null;
                }
                case "toString" -> "Test Job";
                default -> defaultValue(method.getReturnType());
            });
            printJobReference.set(printJob);
        }

        private PrintServiceProvider provider() {
            return () -> List.of(printService);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
