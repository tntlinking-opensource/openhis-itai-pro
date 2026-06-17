package com.openhis.whale.report.application.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfWriter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.report.application.pdf.element.PrintElementRenderContext;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutPage;
import com.openhis.whale.report.application.layout.model.LayoutPageSizeUtils;
import com.openhis.whale.report.application.pdf.overlay.OverlayLayoutRenderer;
import com.openhis.whale.report.domain.constants.ReportPipelineConstants;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * layoutJson → OpenPDF 字节流：支持 page（A4/A5）、roll（热敏裁切 + 绝对层）、overlay（套打）。
 */
@Component
public class LayoutJsonPageRenderer {

    private final ReportPdfFontFactory fontFactory;
    private final PrintElementLayoutRenderer flowRenderer;
    private final OverlayLayoutRenderer overlayRenderer;

    public LayoutJsonPageRenderer(
            ReportPdfFontFactory fontFactory,
            PrintElementLayoutRenderer flowRenderer,
            OverlayLayoutRenderer overlayRenderer) {
        this.fontFactory = fontFactory;
        this.flowRenderer = flowRenderer;
        this.overlayRenderer = overlayRenderer;
    }

    /**
     * 渲染整份 layout 为 PDF。
     *
     * @param document     已解析排版文档
     * @param dataset      打印数据集
     * @param elementIndex 元素定义索引
     * @return PDF 字节
     */
    public byte[] render(LayoutDocument document, Map<String, Object> dataset, Map<String, PrintElementDef> elementIndex) {
        String pipeline = resolvePipeline(document);
        if (!ReportPipelineConstants.PIPE_PAGE.equalsIgnoreCase(pipeline)
                && !ReportPipelineConstants.PIPE_ROLL.equalsIgnoreCase(pipeline)) {
            throw new BusinessException("RPT_PIPELINE_UNSUPPORTED", "不支持的 pipeline: " + pipeline);
        }
        LayoutPage page = document.getPage();
        boolean rollMode = isRollPipeline(document);
        boolean overlayMode = false;
        if (!rollMode && page != null) {
            overlayMode = ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equalsIgnoreCase(page.getLayoutMode());
        }
        try {
            Rectangle pageSize = resolvePageSize(page, rollMode);
            boolean overlayModeEarly = !rollMode && page != null
                    && ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equalsIgnoreCase(page.getLayoutMode());
            float[] margins = overlayModeEarly ? zeroMarginsPt() : resolveMargins(page, rollMode);
            Document pdfDoc = new Document(
                    pageSize,
                    margins[3],
                    margins[1],
                    margins[0],
                    margins[2]);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(pdfDoc, baos);
            pdfDoc.open();
            if (overlayMode) {
                // 套打：预印纸绝对坐标，不走 roll 裁切
                overlayRenderer.render(writer, pdfDoc, document, dataset, elementIndex);
            } else {
                PrintElementRenderContext ctx = new PrintElementRenderContext(
                        pdfDoc,
                        writer,
                        pageSize,
                        margins,
                        page,
                        fontFactory,
                        dataset,
                        elementIndex,
                        rollMode);
                if (rollMode) {
                    // roll：先 flow 测高，再 Stamper 裁切并在干净层叠加 qrcode 等
                    float maxAbsBottomMm = RollAbsoluteLayoutSupport.computeMaxAbsoluteBottomMm(
                            document, elementIndex, page);
                    flowRenderer.renderFlowContent(ctx, document);
                    float flowUsedMm = RollAbsoluteLayoutSupport.measureFlowUsedHeightMm(pdfDoc, writer, pageSize);
                    float finalHeightMm = RollAbsoluteLayoutSupport.resolveFinalPageHeightMm(flowUsedMm, maxAbsBottomMm);
                    pdfDoc.close();
                    return RollAbsoluteLayoutSupport.finalizeRollPdf(
                            baos.toByteArray(),
                            pageSize.getWidth(),
                            pageSize.getHeight(),
                            finalHeightMm,
                            flowRenderer,
                            ctx,
                            document);
                }
                flowRenderer.renderFlow(ctx, document);
            }
            pdfDoc.close();
            return baos.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("RPT_PDF_RENDER_FAILED", "PDF 渲染失败: " + e.getMessage(), e);
        }
    }

    /** 从 layout 或 page 宽推断 pipeline；缺省 page。 */
    private static String resolvePipeline(LayoutDocument document) {
        if (document.getPipeline() != null) {
            return document.getPipeline().trim();
        }
        return ReportPipelineConstants.PIPE_PAGE;
    }

    /** roll 用热敏纸宽 + 长纸高；page 用 A4/A5 与横竖向。 */
    private static Rectangle resolvePageSize(LayoutPage page, boolean rollMode) {
        if (rollMode) {
            float widthMm = ReportPipelineConstants.DEFAULT_ROLL_WIDTH_MM;
            if (page != null && page.getWidthMm() != null && page.getWidthMm() > 0) {
                widthMm = page.getWidthMm();
            }
            float widthPt = ReportPdfUnitUtils.mmToPt(widthMm);
            float heightPt = ReportPdfUnitUtils.mmToPt(ReportPipelineConstants.DEFAULT_ROLL_HEIGHT_MM);
            return new Rectangle(widthPt, heightPt);
        }
        LayoutPageSizeUtils.PageSizeMm pageSizeMm = LayoutPageSizeUtils.resolvePageSizeMm(page);
        float widthPt = ReportPdfUnitUtils.mmToPt(pageSizeMm.widthMm());
        float heightPt = ReportPdfUnitUtils.mmToPt(pageSizeMm.heightMm());
        return new Rectangle(widthPt, heightPt);
    }

    /** overlay 套打：落格坐标相对纸面左上角，Document 不设边距。 */
    private static float[] zeroMarginsPt() {
        return new float[] {0f, 0f, 0f, 0f};
    }

    /** 页边距 mm → pt；roll 默认 2mm 窄边距。 */
    private static float[] resolveMargins(LayoutPage page, boolean rollMode) {
        float topMm = 12f;
        float rightMm = 10f;
        float bottomMm = 12f;
        float leftMm = 10f;
        if (rollMode) {
            topMm = 2f;
            rightMm = 2f;
            bottomMm = 2f;
            leftMm = 2f;
        }
        if (page != null && page.getMarginMm() != null && page.getMarginMm().size() >= 4) {
            List<Float> mm = page.getMarginMm();
            topMm = mm.get(0);
            rightMm = mm.get(1);
            bottomMm = mm.get(2);
            leftMm = mm.get(3);
        }
        return new float[] {
                ReportPdfUnitUtils.mmToPt(topMm),
                ReportPdfUnitUtils.mmToPt(rightMm),
                ReportPdfUnitUtils.mmToPt(bottomMm),
                ReportPdfUnitUtils.mmToPt(leftMm)
        };
    }

    /** pipeline=roll 或指定热敏纸宽且非 overlay 时走 roll 裁切路径。 */
    private static boolean isRollPipeline(LayoutDocument document) {
        if (document == null) {
            return false;
        }
        String pipeline = document.getPipeline();
        if (pipeline != null && ReportPipelineConstants.PIPE_ROLL.equalsIgnoreCase(pipeline.trim())) {
            return true;
        }
        LayoutPage page = document.getPage();
        if (page != null && page.getWidthMm() != null && page.getWidthMm() > 0) {
            return !ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equalsIgnoreCase(page.getLayoutMode());
        }
        return false;
    }
}
