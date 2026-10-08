package com.example.printagent.printing;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

final class ImagePrintDocument implements PrintDocument {

    private final BufferedImage image;

    ImagePrintDocument(Path path) throws IOException {
        this.image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new PrintJobValidationException("The image document could not be decoded.");
        }
    }

    @Override
    public java.awt.print.Printable printable() {
        return (graphics, pageFormat, pageIndex) -> {
            if (pageIndex != 0) {
                return java.awt.print.Printable.NO_SUCH_PAGE;
            }
            PdfPrintDocument.paintImage(graphics, pageFormat, image);
            return java.awt.print.Printable.PAGE_EXISTS;
        };
    }

    @Override
    public int pageCount() {
        return 1;
    }

    @Override
    public void close() {
        image.flush();
    }
}
