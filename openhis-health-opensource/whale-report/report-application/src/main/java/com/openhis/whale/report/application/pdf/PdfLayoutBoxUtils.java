package com.openhis.whale.report.application.pdf;

import com.lowagie.text.Rectangle;
import com.openhis.whale.report.application.layout.model.LayoutBox;

/**
 * layout box（mm，页面左上角原点）→ OpenPDF 矩形（左下角原点）。
 */
public final class PdfLayoutBoxUtils {

    private PdfLayoutBoxUtils() {
    }

    /** flow 绝对层等：坐标相对 content area（含 page margin）。overlay 请用 {@link #toPdfRectangleFromPaperEdge}。 */
    public static Rectangle toPdfRectangle(LayoutBox box, Rectangle pageSize, float[] marginsPt) {
        return toPdfRectangleFromPaperEdge(box, pageSize.getWidth(), pageSize.getHeight(), marginsPt[3], marginsPt[0]);
    }

    /**
     * 热敏 roll 绝对层：坐标相对纸面左上角（与设计器 OverlayBoxGuide 一致）。
     * DirectContent 不受 Document margin 影响，默认 margin 为 0。
     */
    public static Rectangle toPdfRectangleFromPaperEdge(LayoutBox box, Rectangle pageSize) {
        return toPdfRectangleFromPaperEdge(
                box,
                pageSize.getWidth(),
                pageSize.getHeight(),
                0f,
                0f);
    }

    public static Rectangle toPdfRectangleFromPaperEdge(
            LayoutBox box,
            float pageWidthPt,
            float pageHeightPt,
            float marginLeftPt,
            float marginTopPt) {
        float left = marginLeftPt + ReportPdfUnitUtils.mmToPt(nvl(box.getXMm()));
        float heightPt = ReportPdfUnitUtils.mmToPt(nvl(box.getHeightMm()));
        float yFromTopPt = ReportPdfUnitUtils.mmToPt(nvl(box.getYMm()));
        float top = pageHeightPt - marginTopPt - yFromTopPt;
        float bottom = top - heightPt;
        float right = left + ReportPdfUnitUtils.mmToPt(nvl(box.getWidthMm()));
        return new Rectangle(left, bottom, right, top);
    }

    public static float bottomMm(LayoutBox box) {
        return nvl(box.getYMm()) + nvl(box.getHeightMm());
    }

    private static float nvl(Float value) {
        if (value != null) {
            return value;
        }
        return 0f;
    }
}
