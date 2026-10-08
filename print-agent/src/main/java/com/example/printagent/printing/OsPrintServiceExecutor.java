package com.example.printagent.printing;

import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.print.Doc;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.Sides;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;
import com.example.printagent.printer.PrintServiceProvider;

public class OsPrintServiceExecutor {

    private static final javax.print.DocFlavor PRINTABLE_FLAVOR = javax.print.DocFlavor.SERVICE_FORMATTED.PAGEABLE;
    private final PrintServiceProvider serviceProvider;
    private final PrintDocumentPreparer documentPreparer;
    private final PrintJobRequestValidator validator;
    private final Duration acknowledgementTimeout;

    public OsPrintServiceExecutor(
            PrintServiceProvider serviceProvider,
            PrintDocumentPreparer documentPreparer,
            PrintJobRequestValidator validator,
            Duration acknowledgementTimeout) {
        this.serviceProvider = serviceProvider;
        this.documentPreparer = documentPreparer;
        this.validator = validator;
        this.acknowledgementTimeout = acknowledgementTimeout;
    }

    public PrintOutcome execute(PrintJobRequest request) {
        validator.validate(request);
        PrintService printService = serviceProvider.getPrintServices().stream()
                .filter(service -> service.getName().equals(request.printerSystemName()))
                .findFirst()
                .orElseThrow(() -> new PrintExecutionException(
                        "The configured printer is unavailable; no print job was submitted."));
        PrintRequestAttributeSet attributes = createAndValidateAttributes(printService, request);

        try (PrintDocument document = documentPreparer.prepare(request)) {
            if (document.pageCount() < 1) {
                throw new PrintJobValidationException("The print document contains no printable pages.");
            }
            Book book = new Book();
            PageFormat pageFormat = createPageFormat(request);
            book.append(document.printable(), pageFormat, document.pageCount());

            DocPrintJob printJob = printService.createPrintJob();
            CountDownLatch completion = new CountDownLatch(1);
            AtomicReference<PrintOutcome> outcome = new AtomicReference<>();
            printJob.addPrintJobListener(new PrintJobAdapter() {
                @Override
                public void printJobCompleted(PrintJobEvent event) {
                    outcome.set(new PrintOutcome(
                            PrintOutcome.Status.OS_JOB_COMPLETED,
                            "The operating-system print service reported the job complete; this does not confirm paper output."));
                    completion.countDown();
                }

                @Override
                public void printJobFailed(PrintJobEvent event) {
                    outcome.set(new PrintOutcome(
                            PrintOutcome.Status.FAILED,
                            "The operating-system print service reported a failure."));
                    completion.countDown();
                }

                @Override
                public void printJobCanceled(PrintJobEvent event) {
                    outcome.set(new PrintOutcome(
                            PrintOutcome.Status.FAILED,
                            "The operating-system print service cancelled the job."));
                    completion.countDown();
                }

                @Override
                public void printJobNoMoreEvents(PrintJobEvent event) {
                    outcome.compareAndSet(null, new PrintOutcome(
                            PrintOutcome.Status.OUTCOME_UNKNOWN,
                            "The operating-system print service stopped reporting job events."));
                    completion.countDown();
                }
            });

            Doc doc = new SimpleDoc(book, PRINTABLE_FLAVOR, null);
            try {
                printJob.print(doc, attributes);
            } catch (PrintException exception) {
                throw new PrintExecutionException(
                        "The operating-system print service did not confirm whether the job was submitted.",
                        exception,
                        true);
            }
            try {
                if (!completion.await(acknowledgementTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    return new PrintOutcome(
                            PrintOutcome.Status.OUTCOME_UNKNOWN,
                            "No final print-service acknowledgement was received; the job was not automatically resubmitted.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new PrintOutcome(
                        PrintOutcome.Status.OUTCOME_UNKNOWN,
                        "Waiting for print-service acknowledgement was interrupted; the job was not automatically resubmitted.");
            }
            return outcome.get() == null
                    ? new PrintOutcome(
                            PrintOutcome.Status.OUTCOME_UNKNOWN,
                            "The print-service outcome could not be determined; the job was not automatically resubmitted.")
                    : outcome.get();
        } catch (IOException exception) {
            throw new PrintExecutionException("The print document could not be prepared.", exception);
        }
    }

    private PrintRequestAttributeSet createAndValidateAttributes(PrintService service, PrintJobRequest request) {
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add("COLOR".equals(request.printType()) ? Chromaticity.COLOR : Chromaticity.MONOCHROME);
        attributes.add(new Copies(request.copies()));
        attributes.add(media(request.paperSize()));
        attributes.add("landscape".equals(request.orientation())
                ? OrientationRequested.LANDSCAPE
                : OrientationRequested.PORTRAIT);
        attributes.add(request.doubleSided() ? Sides.DUPLEX : Sides.ONE_SIDED);

        List<javax.print.attribute.Attribute> requested = Arrays.stream(attributes.toArray())
                .map(javax.print.attribute.Attribute.class::cast)
                .toList();
        for (javax.print.attribute.Attribute attribute : requested) {
            if (!service.isAttributeValueSupported(attribute, PRINTABLE_FLAVOR, attributes)) {
                throw new PrintJobValidationException(
                        "The selected printer does not support requested print option: "
                                + attribute.getName() + ".");
            }
        }
        return attributes;
    }

    private MediaSizeName media(String paperSize) {
        return switch (paperSize) {
            case "A4" -> MediaSizeName.ISO_A4;
            case "A3" -> MediaSizeName.ISO_A3;
            case "Letter" -> MediaSizeName.NA_LETTER;
            case "Legal" -> MediaSizeName.NA_LEGAL;
            default -> throw new PrintJobValidationException("The requested paper size is not supported.");
        };
    }

    private PageFormat createPageFormat(PrintJobRequest request) {
        Paper paper = new Paper();
        float[] size = switch (request.paperSize()) {
            case "A4" -> new float[]{595, 842};
            case "A3" -> new float[]{842, 1191};
            case "Letter" -> new float[]{612, 792};
            case "Legal" -> new float[]{612, 1008};
            default -> throw new PrintJobValidationException("The requested paper size is not supported.");
        };
        paper.setSize(size[0], size[1]);
        double margin = 18;
        paper.setImageableArea(margin, margin, size[0] - margin * 2, size[1] - margin * 2);
        PageFormat format = new PageFormat();
        format.setPaper(paper);
        format.setOrientation("landscape".equals(request.orientation())
                ? PageFormat.LANDSCAPE
                : PageFormat.PORTRAIT);
        return format;
    }
}
