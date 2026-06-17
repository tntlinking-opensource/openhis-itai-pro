package com.openhis.whale.report.application.pdf;

import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.openhis.whale.report.application.pdf.element.PrintElementRenderContext;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutBox;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.model.LayoutPage;
import com.openhis.whale.report.application.layout.support.PositionedElementSupport;
import com.openhis.whale.report.domain.entity.PrintElementDef;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * 热敏 roll：flow 渲染完成后裁切页高，再在 PdfStamper 干净坐标系上叠加绝对层（qrcode 等）。
 */
public final class RollAbsoluteLayoutSupport {

    private static final float MIN_PAGE_HEIGHT_MM = 40f;
    private static final float PAGE_HEIGHT_BUFFER_MM = 2f;

    private RollAbsoluteLayoutSupport() {
    }

    /**
     * 扫描 layout 中绝对层元素，取最大 bottom（mm），供 roll 裁切高度计算。
     *
     * @param document     排版文档
     * @param elementIndex 元素定义索引
     * @param page         页级偏移（overlay.printOffsetMm）
     * @return 绝对层最下缘 mm；无绝对元素时为 0
     */
    public static float computeMaxAbsoluteBottomMm(
            LayoutDocument document,
            Map<String, PrintElementDef> elementIndex,
            LayoutPage page) {
        float maxBottom = 0f;
        if (document.getItems() == null) {
            return maxBottom;
        }
        for (LayoutItem item : document.getItems()) {
            if (!PositionedElementSupport.isVisible(item)) {
                continue;
            }
            PrintElementDef def = elementIndex.get(item.getElementCode());
            if (def == null || !PositionedElementSupport.isAbsoluteLayerElement(def)) {
                continue;
            }
            LayoutBox box = PositionedElementSupport.resolveEffectiveBox(item, def, page);
            if (box != null) {
                maxBottom = Math.max(maxBottom, PdfLayoutBoxUtils.bottomMm(box));
            }
        }
        return maxBottom;
    }

    /**
     * 测量 flow 渲染已占用高度（mm），不含绝对层占位。
     *
     * @param pdfDoc   OpenPDF Document
     * @param writer   当前页 Writer
     * @param pageSize 初始长纸页尺寸
     * @return flow 占用高度 mm
     */
    public static float measureFlowUsedHeightMm(
            com.lowagie.text.Document pdfDoc,
            com.lowagie.text.pdf.PdfWriter writer,
            Rectangle pageSize) {
        float y = writer.getVerticalPosition(true);
        if (y <= 0) {
            return 0f;
        }
        float usedPt = pageSize.getHeight() - y + pdfDoc.bottomMargin();
        return ReportPdfUnitUtils.ptToMm(Math.max(0f, usedPt));
    }

    /**
     * 综合 flow 高度与绝对层 bottom，得到裁切后页高（含最小高度与缓冲）。
     *
     * @param flowUsedMm          flow 占用高度 mm
     * @param maxAbsoluteBottomMm 绝对层最下缘 mm
     * @return 最终页高 mm
     */
    public static float resolveFinalPageHeightMm(float flowUsedMm, float maxAbsoluteBottomMm) {
        float required = Math.max(flowUsedMm, maxAbsoluteBottomMm);
        return Math.max(MIN_PAGE_HEIGHT_MM, required + PAGE_HEIGHT_BUFFER_MM);
    }

    /**
     * 用 PdfStamper 裁切 roll 长纸，并在叠加层绘制绝对元素后输出最终 PDF。
     *
     * @param pdfBytes          flow 阶段生成的 PDF
     * @param pageWidthPt       纸宽 pt
     * @param initialHeightPt   裁切前页高 pt
     * @param finalHeightMm     目标页高 mm
     * @param flowRenderer      元素渲染器（用于 roll 叠加）
     * @param ctx               渲染上下文
     * @param document          排版文档
     * @return 裁切并叠加后的 PDF 字节
     */
    public static byte[] finalizeRollPdf(
            byte[] pdfBytes,
            float pageWidthPt,
            float initialHeightPt,
            float finalHeightMm,
            PrintElementLayoutRenderer flowRenderer,
            PrintElementRenderContext ctx,
            LayoutDocument document) throws Exception {
        float finalHeightPt = ReportPdfUnitUtils.mmToPt(finalHeightMm);
        float cropBottom = initialHeightPt - finalHeightPt;
        if (cropBottom < 0f) {
            cropBottom = 0f;
        }

        PdfReader reader = new PdfReader(pdfBytes);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfStamper stamper = new PdfStamper(reader, out);
            try {
                PdfDictionary page = reader.getPageN(1);
                PdfArray crop = new PdfArray(new float[] {0f, cropBottom, pageWidthPt, initialHeightPt});
                page.put(PdfName.CROPBOX, crop);
                page.put(PdfName.MEDIABOX, crop);

                PdfContentByte overlay = stamper.getOverContent(1);
                flowRenderer.renderRollAbsoluteOverlay(overlay, ctx, document, finalHeightPt, cropBottom);
            } finally {
                stamper.close();
            }
            return out.toByteArray();
        } finally {
            reader.close();
        }
    }
}
