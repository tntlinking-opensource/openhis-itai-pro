package com.openhis.whale.report.application.pdf.element;

import com.lowagie.text.pdf.PdfContentByte;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.domain.entity.PrintElementDef;

/**
 * roll 裁切后在 Stamper 干净层上叠加的绝对元素。
 */
public interface RollOverlayCapable {

    void renderRollOverlay(
            PdfContentByte canvas,
            PrintElementRenderContext ctx,
            LayoutItem item,
            PrintElementDef def,
            float finalPageHeightPt,
            float cropBottomPt) throws Exception;
}
