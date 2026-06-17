package com.openhis.whale.report.application.pdf.element;

import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.openhis.whale.report.application.pdf.BindPathResolver;
import com.openhis.whale.report.application.pdf.PdfLayoutBoxUtils;
import com.openhis.whale.report.application.pdf.QrCodeImageFactory;
import com.openhis.whale.report.application.pdf.ReportPdfUnitUtils;
import com.openhis.whale.report.application.layout.model.LayoutBox;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.support.PositionedElementSupport;
import com.openhis.whale.report.application.pdf.overlay.OverlayBoxResolver;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 二维码绝对层渲染：page 模式走 DirectContent，roll 模式由 Stamper 叠加。
 */
@Component
public class QrcodeElementRenderer implements PrintElementRenderer, RollOverlayCapable {

    private final OverlayBoxResolver boxResolver;

    public QrcodeElementRenderer(OverlayBoxResolver boxResolver) {
        this.boxResolver = boxResolver;
    }

    @Override
    public String renderType() {
        return "qrcode";
    }

    @Override
    public void render(PrintElementRenderContext ctx, LayoutItem item, PrintElementDef def) throws Exception {
        if (ctx.isRollMode() || ctx.getWriter() == null) {
            return;
        }
        LayoutBox box = boxResolver.resolveEffectiveBox(item, def, ctx.getPage());
        Rectangle rect = PdfLayoutBoxUtils.toPdfRectangle(box, ctx.getPageSize(), ctx.getMarginsPt());
        drawImage(ctx.getWriter().getDirectContent(), ctx, box, rect, def, item.getElementCode());
    }

    /**
     * roll 裁切后由 PdfStamper 叠加；坐标相对裁切后小票（与设计器 viewBox 一致）。
     *
     * @param canvas            Stamper 叠加层
     * @param ctx               渲染上下文
     * @param item              layout 项
     * @param def               元素定义
     * @param finalPageHeightPt 裁切后页高 pt
     * @param cropBottomPt      自纸底裁去距离 pt
     */
    @Override
    public void renderRollOverlay(
            PdfContentByte canvas,
            PrintElementRenderContext ctx,
            LayoutItem item,
            PrintElementDef def,
            float finalPageHeightPt,
            float cropBottomPt) throws Exception {
        LayoutBox box = boxResolver.resolveEffectiveBox(item, def, ctx.getPage());
        Rectangle rect = PdfLayoutBoxUtils.toPdfRectangleFromPaperEdge(
                box,
                ctx.getInitialPageWidthPt(),
                finalPageHeightPt,
                0f,
                0f);
        Rectangle streamRect = new Rectangle(
                rect.getLeft(),
                rect.getBottom() + cropBottomPt,
                rect.getRight(),
                rect.getTop() + cropBottomPt);
        drawImage(canvas, ctx, box, streamRect, def, item.getElementCode());
    }

    private void drawImage(
            PdfContentByte canvas,
            PrintElementRenderContext ctx,
            LayoutBox box,
            Rectangle rect,
            PrintElementDef def,
            String elementCode) throws Exception {
        if (canvas == null || !PositionedElementSupport.isAbsoluteLayerElement(def)) {
            return;
        }
        String payload = BindPathResolver.asText(BindPathResolver.resolve(ctx.getDataset(), def.getBindPath()));
        if (!StringUtils.hasText(payload)) {
            return;
        }
        if (rect.getWidth() <= 0 || rect.getHeight() <= 0) {
            return;
        }
        int px = Math.max(96, (int) ReportPdfUnitUtils.mmToPt(Math.max(box.getWidthMm(), box.getHeightMm())));
        byte[] png = QrCodeImageFactory.encodePng(payload, px);
        Image image = Image.getInstance(png);
        image.scaleAbsolute(rect.getWidth(), rect.getHeight());
        canvas.saveState();
        canvas.addImage(
                image,
                rect.getWidth(),
                0,
                0,
                rect.getHeight(),
                rect.getLeft(),
                rect.getBottom());
        canvas.restoreState();
        ctx.noteAbsoluteBottomMm(PdfLayoutBoxUtils.bottomMm(box));
    }
}
