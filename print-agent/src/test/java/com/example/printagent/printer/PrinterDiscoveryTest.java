package com.example.printagent.printer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.CopiesSupported;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSizeName;
import org.junit.jupiter.api.Test;

class PrinterDiscoveryTest {

    @Test
    void describesAvailablePrintersUsingTheInjectedHostProvider() {
        PrintService zebra = new TestPrintService("Zebra", true);
        PrintService canon = new TestPrintService("Canon", true);
        PrinterDiscovery discovery = new PrinterDiscovery(() -> List.of(zebra, canon));

        List<PrinterDescriptor> printers = discovery.discover();

        assertEquals(List.of("Canon", "Zebra"), printers.stream().map(PrinterDescriptor::systemName).toList());
    }

    @Test
    void omitsServicesThatCannotAcceptPageableDocuments() {
        PrinterDiscovery discovery = new PrinterDiscovery(() -> List.of(
                new TestPrintService("PDF Printer", true),
                new TestPrintService("Unsupported", false)));

        assertEquals(1, discovery.discover().size());
        assertTrue(discovery.discover().getFirst().systemName().equals("PDF Printer"));
    }

    @Test
    void reportsOnlyBackendSupportedPaperNamesAndCapsCopiesAtOneHundred() {
        TestPrintService printer = new TestPrintService(
                "Office",
                true,
                new Media[]{
                        MediaSizeName.ISO_A4,
                        MediaSizeName.NA_LETTER,
                        MediaSizeName.ISO_B5},
                new CopiesSupported(1, 500));

        PrinterDescriptor descriptor = new PrinterDiscovery(() -> List.of(printer)).discover().getFirst();

        assertEquals(java.util.Set.of("A4", "Letter"), descriptor.paperSizes());
        assertEquals(100, descriptor.maximumCopies());
    }

    @Test
    void omitsPrintersWithoutARecognizedPaperSizeInsteadOfSendingAnInvalidHeartbeat() {
        PrinterDiscovery discovery = new PrinterDiscovery(() -> List.of(new TestPrintService(
                "Unknown paper",
                true,
                new Media[]{MediaSizeName.ISO_B5},
                null)));

        assertTrue(discovery.discover().isEmpty());
    }

    private static final class TestPrintService implements PrintService {

        private final String name;
        private final boolean supportsPageable;
        private final Object supportedMedia;
        private final Object supportedCopies;

        private TestPrintService(String name, boolean supportsPageable) {
            this(
                    name,
                    supportsPageable,
                    new Media[]{MediaSizeName.ISO_A4},
                    new CopiesSupported(1, 1));
        }

        private TestPrintService(
                String name, boolean supportsPageable, Object supportedMedia, Object supportedCopies) {
            this.name = name;
            this.supportsPageable = supportsPageable;
            this.supportedMedia = supportedMedia;
            this.supportedCopies = supportedCopies;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isDocFlavorSupported(DocFlavor flavor) {
            return supportsPageable && DocFlavor.SERVICE_FORMATTED.PAGEABLE.equals(flavor);
        }

        @Override
        public javax.print.attribute.Attribute getDefaultAttributeValue(Class<? extends javax.print.attribute.Attribute> category) {
            return null;
        }

        @Override
        public Object getSupportedAttributeValues(
                Class<? extends javax.print.attribute.Attribute> category,
                DocFlavor flavor,
                javax.print.attribute.AttributeSet attributes) {
            if (Media.class.equals(category)) {
                return supportedMedia;
            }
            if (Copies.class.equals(category)) {
                return supportedCopies;
            }
            return null;
        }

        @Override
        public boolean isAttributeValueSupported(
                javax.print.attribute.Attribute attr,
                DocFlavor flavor,
                javax.print.attribute.AttributeSet attributes) {
            return false;
        }

        @Override
        public boolean isAttributeCategorySupported(Class<? extends javax.print.attribute.Attribute> category) {
            return false;
        }

        @Override
        public javax.print.attribute.AttributeSet getUnsupportedAttributes(
                DocFlavor flavor,
                javax.print.attribute.AttributeSet attributes) {
            return null;
        }

        @Override
        public DocFlavor[] getSupportedDocFlavors() {
            return supportsPageable ? new DocFlavor[]{DocFlavor.SERVICE_FORMATTED.PAGEABLE} : new DocFlavor[0];
        }

        @Override
        public Class<?>[] getSupportedAttributeCategories() {
            return new Class<?>[0];
        }

        @Override
        public javax.print.DocPrintJob createPrintJob() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addPrintServiceAttributeListener(javax.print.event.PrintServiceAttributeListener listener) {
        }

        @Override
        public void removePrintServiceAttributeListener(javax.print.event.PrintServiceAttributeListener listener) {
        }

        @Override
        public javax.print.attribute.PrintServiceAttributeSet getAttributes() {
            return new javax.print.attribute.HashPrintServiceAttributeSet();
        }

        @Override
        public <T extends javax.print.attribute.PrintServiceAttribute> T getAttribute(Class<T> category) {
            return null;
        }

        @Override
        public javax.print.ServiceUIFactory getServiceUIFactory() {
            return null;
        }

    }
}
