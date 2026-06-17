package com.openhis.whale.report.application.pdf.element;

import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.domain.entity.PrintElementDef;

public interface PrintElementRenderer {

    String renderType();

    void render(PrintElementRenderContext ctx, LayoutItem item, PrintElementDef def) throws Exception;
}
