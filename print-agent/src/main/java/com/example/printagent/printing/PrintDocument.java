package com.example.printagent.printing;

import java.awt.print.Printable;
import java.io.IOException;

public interface PrintDocument extends AutoCloseable {

    Printable printable();

    int pageCount();

    @Override
    void close() throws IOException;
}
