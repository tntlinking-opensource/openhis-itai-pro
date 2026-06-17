package com.openhis.whale.report.application.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;

/**
 * P1 占位：生成最小 PDF，待 Layout 渲染器接入后替换。
 */
public final class MinimalPdfFactory {

    private MinimalPdfFactory() {
    }

    public static byte[] createPlaceholderPdf(String title) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            String paragraphText = "OpenHIS Report Preview (stub)";
            if (title != null) {
                paragraphText = title;
            }
            document.add(new Paragraph(paragraphText));
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("生成占位 PDF 失败", e);
        }
    }
}
