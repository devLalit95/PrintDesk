package com.example.printagent.printer;

import java.util.List;
import javax.print.PrintService;

public interface PrintServiceProvider {

    List<PrintService> getPrintServices();
}
