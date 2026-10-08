package com.example.printagent.printer;

import java.util.Arrays;
import java.util.List;
import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

public class HostPrintServiceProvider implements PrintServiceProvider {

    @Override
    public List<PrintService> getPrintServices() {
        return Arrays.asList(PrintServiceLookup.lookupPrintServices(
                DocFlavor.SERVICE_FORMATTED.PAGEABLE,
                null));
    }
}
