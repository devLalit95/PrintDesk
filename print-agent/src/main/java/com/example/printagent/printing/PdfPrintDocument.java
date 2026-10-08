package com.example.printagent.printing;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

final class PdfPrintDocument implements PrintDocument {

    private static final float RENDER_DPI = 150;

    private final PDDocument document;
    private final PDFRenderer renderer;
    private final String printType;

    PdfPrintDocument(Path path, String printType) throws IOException {
        this.document = Loader.loadPDF(path.toFile());
        this.renderer = new PDFRenderer(document);
        this.printType = printType;
    }

    @Override
    public java.awt.print.Printable printable() {
        return (graphics, pageFormat, pageIndex) -> {
            if (pageIndex < 0 || pageIndex >= pageCount()) {
                return java.awt.print.Printable.NO_SUCH_PAGE;
            }
            ImageType imageType = "COLOR".equals(printType) ? ImageType.RGB : ImageType.GRAY;
            BufferedImage page;
            try {
                page = renderer.renderImage(pageIndex, RENDER_DPI / 72.0f, imageType);
            } catch (IOException exception) {
                java.awt.print.PrinterException failure =
                        new java.awt.print.PrinterException("A PDF page could not be rendered.");
                failure.initCause(exception);
                throw failure;
            }
            try {
                paintImage(graphics, pageFormat, page);
            } finally {
                page.flush();
            }
            return java.awt.print.Printable.PAGE_EXISTS;
        };
    }

    @Override
    public int pageCount() {
        return document.getNumberOfPages();
    }

    @Override
    public void close() throws IOException {
        document.close();
    }

    static void paintImage(Graphics graphics, java.awt.print.PageFormat pageFormat, BufferedImage image) {
        Graphics2D target = (Graphics2D) graphics.create();
        try {
            target.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            target.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double scale = Math.min(
                    pageFormat.getImageableWidth() / image.getWidth(),
                    pageFormat.getImageableHeight() / image.getHeight());
            double width = image.getWidth() * scale;
            double height = image.getHeight() * scale;
            double x = pageFormat.getImageableX() + (pageFormat.getImageableWidth() - width) / 2;
            double y = pageFormat.getImageableY() + (pageFormat.getImageableHeight() - height) / 2;
            target.drawImage(image, transformFor(x, y, scale), null);
        } finally {
            target.dispose();
        }
    }

    private static AffineTransform transformFor(double x, double y, double scale) {
        AffineTransform transform = new AffineTransform();
        transform.translate(x, y);
        transform.scale(scale, scale);
        return transform;
    }
}
