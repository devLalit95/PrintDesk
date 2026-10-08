package com.example.printagent.printing;

import java.io.IOException;

public interface PrintDocumentPreparer {

    PrintDocument prepare(PrintJobRequest request) throws IOException;
}
