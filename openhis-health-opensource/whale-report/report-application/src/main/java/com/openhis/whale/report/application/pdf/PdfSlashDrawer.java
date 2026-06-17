package com.openhis.whale.report.application.pdf;

import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;

/**
 * PDF 斜线绘制（处方空白行防篡改等场景）。
 */
public final class PdfSlashDrawer {

    private PdfSlashDrawer() {
    }

    /**
     * 在矩形内绘制左下 → 右上对角斜线。
     */
    public static void drawDiagonalSlash(PdfContentByte canvas, Rectangle rect, float lineWidthPt) {
        if (canvas == null || rect == null) {
            return;
        }
        float pad = Math.min(rect.getWidth(), rect.getHeight()) * 0.04f;
        float left = rect.getLeft() + pad;
        float bottom = rect.getBottom() + pad;
        float right = rect.getRight() - pad;
        float top = rect.getTop() - pad;
        if (right <= left || top <= bottom) {
            return;
        }
        canvas.saveState();
        canvas.setLineWidth(lineWidthPt);
        canvas.moveTo(left, bottom);
        canvas.lineTo(right, top);
        canvas.stroke();
        canvas.restoreState();
    }
}
