package com.openhis.whale.report.application.pdf.element;

import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.pdf.TableAlignSupport;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class StaticTitleElementRenderer implements PrintElementRenderer {

    @Override
    public String renderType() {
        return "staticTitle";
    }

    @Override
    public void render(PrintElementRenderContext ctx, LayoutItem item, PrintElementDef def) throws Exception {
        String text = resolveStaticTitleText(item, def);
        Font font = ctx.fontFor(item, def, 18f, Font.BOLD);
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(TableAlignSupport.toPdfAlign(TableAlignSupport.resolveTableDefaultAlign(item)));
        p.setSpacingAfter(8f);
        ctx.getDocument().add(p);
    }

    private static String resolveStaticTitleText(LayoutItem item, PrintElementDef def) {
        if (StringUtils.hasText(item.getLabelOverride())) {
            return item.getLabelOverride();
        }
        if (StringUtils.hasText(def.getDefaultLabel())) {
            return def.getDefaultLabel();
        }
        return def.getLabel();
    }
}
