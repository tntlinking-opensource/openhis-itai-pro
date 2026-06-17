package com.openhis.whale.report.application.pdf.element;

import com.lowagie.text.pdf.draw.LineSeparator;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;

@Component
public class LineElementRenderer implements PrintElementRenderer {

    @Override
    public String renderType() {
        return "line";
    }

    @Override
    public void render(PrintElementRenderContext ctx, LayoutItem item, PrintElementDef def) throws Exception {
        LineSeparator line = new LineSeparator();
        line.setOffset(-2f);
        ctx.getDocument().add(line);
        ctx.getDocument().add(new com.lowagie.text.Paragraph(" "));
    }
}
