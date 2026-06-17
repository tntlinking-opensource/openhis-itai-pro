package com.openhis.whale.report.application.pdf;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.report.application.pdf.element.PrintElementRenderContext;
import com.openhis.whale.report.application.pdf.element.PrintElementRenderer;
import com.openhis.whale.report.application.pdf.element.PrintElementRendererRegistry;
import com.openhis.whale.report.application.pdf.element.RollOverlayCapable;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.support.PositionedElementSupport;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;

/**
 * layout 元素分发：flow 排版、page 绝对层与 roll 裁切后叠加层。
 */
@Component
public class PrintElementLayoutRenderer {

    private final PrintElementRendererRegistry registry;

    public PrintElementLayoutRenderer(PrintElementRendererRegistry registry) {
        this.registry = registry;
    }

    /**
     * page/roll 常规路径：先 flow 元素，再在同一页绘制绝对层（非 roll 裁切场景）。
     *
     * @param ctx      渲染上下文
     * @param document 排版文档
     */
    public void renderFlow(
            PrintElementRenderContext ctx,
            LayoutDocument document) throws Exception {
        renderFlowContent(ctx, document);
        renderAbsoluteItems(ctx, document);
    }

    /**
     * 仅渲染参与 flow 的元素（table/kv/text 等），跳过 qrcode 等绝对层。
     *
     * @param ctx      渲染上下文
     * @param document 排版文档
     */
    public void renderFlowContent(PrintElementRenderContext ctx, LayoutDocument document) throws Exception {
        for (LayoutItem item : document.getItems()) {
            if (!PositionedElementSupport.isVisible(item)) {
                continue;
            }
            PrintElementDef def = ctx.getElementIndex().get(item.getElementCode());
            if (def == null) {
                throw new BusinessException("RPT_ELEMENT_UNKNOWN", "元素未在 pack 中定义: " + item.getElementCode());
            }
            if (PositionedElementSupport.isAbsoluteLayerElement(def)) {
                continue;
            }
            PrintElementRenderer renderer = registry.get(def.getRenderType());
            renderer.render(ctx, item, def);
        }
    }

    /**
     * page 模式下在同一 Document 用户空间绘制绝对层元素。
     *
     * @param ctx      渲染上下文
     * @param document 排版文档
     */
    public void renderAbsoluteItems(PrintElementRenderContext ctx, LayoutDocument document) throws Exception {
        for (LayoutItem item : document.getItems()) {
            if (!PositionedElementSupport.isVisible(item)) {
                continue;
            }
            PrintElementDef def = ctx.getElementIndex().get(item.getElementCode());
            if (def == null || !PositionedElementSupport.isAbsoluteLayerElement(def)) {
                continue;
            }
            PrintElementRenderer renderer = registry.get(def.getRenderType());
            renderer.render(ctx, item, def);
        }
    }

    /**
     * roll 裁切完成后，在 Stamper 干净层上叠加 qrcode 等绝对元素。
     *
     * @param canvas            PdfStamper 叠加层
     * @param ctx               渲染上下文
     * @param document          排版文档
     * @param finalPageHeightPt 裁切后页高（pt）
     * @param cropBottomPt      自纸底裁去的距离（pt），用于坐标换算
     */
    public void renderRollAbsoluteOverlay(
            com.lowagie.text.pdf.PdfContentByte canvas,
            PrintElementRenderContext ctx,
            LayoutDocument document,
            float finalPageHeightPt,
            float cropBottomPt) throws Exception {
        for (LayoutItem item : document.getItems()) {
            if (!PositionedElementSupport.isVisible(item)) {
                continue;
            }
            PrintElementDef def = ctx.getElementIndex().get(item.getElementCode());
            if (def == null || !PositionedElementSupport.isAbsoluteLayerElement(def)) {
                continue;
            }
            PrintElementRenderer renderer = registry.get(def.getRenderType());
            if (renderer instanceof RollOverlayCapable overlayRenderer) {
                overlayRenderer.renderRollOverlay(canvas, ctx, item, def, finalPageHeightPt, cropBottomPt);
            }
        }
    }
}
