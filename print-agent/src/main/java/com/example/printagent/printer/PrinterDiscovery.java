package com.example.printagent.printer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.CopiesSupported;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.Sides;

public class PrinterDiscovery {

    private static final DocFlavor FLAVOR = DocFlavor.SERVICE_FORMATTED.PAGEABLE;

    private final PrintServiceProvider provider;

    public PrinterDiscovery(PrintServiceProvider provider) {
        this.provider = provider;
    }

    public List<PrinterDescriptor> discover() {
        return provider.getPrintServices().stream()
                .filter(service -> service.isDocFlavorSupported(FLAVOR))
                .map(this::describe)
                .filter(printer -> !printer.paperSizes().isEmpty())
                .sorted(Comparator.comparing(PrinterDescriptor::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private PrinterDescriptor describe(PrintService service) {
        Set<String> paperSizes = new HashSet<>();
        Object supportedMedia = service.getSupportedAttributeValues(Media.class, FLAVOR, null);
        if (supportedMedia instanceof Media[] mediaValues) {
            Arrays.stream(mediaValues)
                    .filter(MediaSizeName.class::isInstance)
                    .map(MediaSizeName.class::cast)
                    .map(this::paperSize)
                    .filter(java.util.Objects::nonNull)
                    .forEach(paperSizes::add);
        }

        int maximumCopies = 1;
        Object copiesValues = service.getSupportedAttributeValues(Copies.class, FLAVOR, null);
        if (copiesValues instanceof CopiesSupported supportedCopies) {
            maximumCopies = Arrays.stream(supportedCopies.getMembers())
                    .filter(range -> range.length > 0)
                    .mapToInt(range -> range[range.length - 1])
                    .max()
                    .orElse(1);
        }

        return new PrinterDescriptor(
                service.getName(),
                service.getName(),
                service.isAttributeValueSupported(Chromaticity.COLOR, FLAVOR, null),
                service.isAttributeValueSupported(Sides.DUPLEX, FLAVOR, null),
                Math.min(maximumCopies, 100),
                paperSizes);
    }

    private String paperSize(MediaSizeName paperSize) {
        if (MediaSizeName.ISO_A4.equals(paperSize)) {
            return "A4";
        }
        if (MediaSizeName.ISO_A3.equals(paperSize)) {
            return "A3";
        }
        if (MediaSizeName.NA_LETTER.equals(paperSize)) {
            return "Letter";
        }
        if (MediaSizeName.NA_LEGAL.equals(paperSize)) {
            return "Legal";
        }
        return null;
    }
}
