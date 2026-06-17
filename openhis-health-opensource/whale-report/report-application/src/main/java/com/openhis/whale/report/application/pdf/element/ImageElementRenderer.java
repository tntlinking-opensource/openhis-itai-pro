package com.openhis.whale.report.application.pdf.element;

import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.openhis.whale.report.application.layout.model.LayoutBox;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.support.PositionedElementSupport;
import com.openhis.whale.report.application.pdf.BindPathResolver;
import com.openhis.whale.report.application.pdf.PdfLayoutBoxUtils;
import com.openhis.whale.report.application.pdf.PrintImageLoader;
import com.openhis.whale.report.application.pdf.overlay.OverlayBoxResolver;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 图片绝对层渲染：bindPath 为 HTTP(S) URL 或 data URI / Base64。
 */
@Component
public class ImageElementRenderer implements PrintElementRenderer, RollOverlayCapable {

    private final OverlayBoxResolver boxResolver;

    public ImageElementRenderer(OverlayBoxResolver boxResolver) {
        this.boxResolver = boxResolver;
    }

    @Override
    public String renderType() {
        return "image";
    }

    @Override
    public void render(PrintElementRenderContext ctx, LayoutItem item, PrintElementDef def) throws Exception {
        if (ctx.isRollMode() || ctx.getWriter() == null) {
            return;
        }
        LayoutBox box = boxResolver.resolveEffectiveBox(item, def, ctx.getPage());
        Rectangle rect = PdfLayoutBoxUtils.toPdfRectangle(box, ctx.getPageSize(), ctx.getMarginsPt());
        drawImage(ctx.getWriter().getDirectContent(), ctx, box, rect, def);
    }

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
        drawImage(canvas, ctx, box, streamRect, def);
    }

    private void drawImage(
            PdfContentByte canvas,
            PrintElementRenderContext ctx,
            LayoutBox box,
            Rectangle rect,
            PrintElementDef def) throws Exception {
        if (canvas == null || !PositionedElementSupport.isAbsoluteLayerElement(def)) {
            return;
        }
        String source = BindPathResolver.asText(BindPathResolver.resolve(ctx.getDataset(), def.getBindPath()));
        if (!StringUtils.hasText(source)) {
            return;
        }
        if (rect.getWidth() <= 0 || rect.getHeight() <= 0) {
            return;
        }
        byte[] bytes = PrintImageLoader.loadBytes(source);
        if (bytes == null || bytes.length == 0) {
            return;
        }
        Image image = Image.getInstance(bytes);
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
