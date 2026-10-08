package com.example.printagent.printer;

import java.util.Set;

public record PrinterDescriptor(
        String systemName,
        String displayName,
        boolean colorSupported,
        boolean duplexSupported,
        int maximumCopies,
        Set<String> paperSizes) {

    public PrinterDescriptor {
        paperSizes = Set.copyOf(paperSizes);
    }
}
