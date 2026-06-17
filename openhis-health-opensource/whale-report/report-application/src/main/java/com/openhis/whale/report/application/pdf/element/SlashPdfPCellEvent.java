package com.openhis.whale.report.application.pdf.element;

import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.openhis.whale.report.application.pdf.PdfSlashDrawer;

/**
 * flow 表格空行斜线单元格。
 */
public class SlashPdfPCellEvent implements PdfPCellEvent {

    private static final float LINE_WIDTH_PT = 0.5f;

    @Override
    public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
        PdfContentByte canvas = canvases[PdfPTable.LINECANVAS];
        PdfSlashDrawer.drawDiagonalSlash(canvas, position, LINE_WIDTH_PT);
    }
}
