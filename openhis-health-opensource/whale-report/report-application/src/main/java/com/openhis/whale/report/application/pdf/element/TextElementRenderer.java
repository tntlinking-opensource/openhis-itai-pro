package com.openhis.whale.report.application.pdf.element;

import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.openhis.whale.report.application.pdf.BindPathResolver;
import com.openhis.whale.report.application.pdf.PrintTextFormat;
import com.openhis.whale.report.application.pdf.TableAlignSupport;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TextElementRenderer implements PrintElementRenderer {

    @Override
    public String renderType() {
        return "text";
    }

    @Override
    public void render(PrintElementRenderContext ctx, LayoutItem item, PrintElementDef def) throws Exception {
        Object value = BindPathResolver.resolve(ctx.getDataset(), def.getBindPath());
        String label = def.getLabel();
        if (StringUtils.hasText(item.getLabelOverride())) {
            label = item.getLabelOverride();
        }
        String valueText = BindPathResolver.asText(value);
        String text = PrintTextFormat.formatLabelValue(label, valueText, false);
        if (!StringUtils.hasText(text)) {
            return;
        }
        Font font = ctx.fontFor(item, def, 10f, Font.NORMAL);
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(TableAlignSupport.toPdfAlign(TableAlignSupport.resolveTableDefaultAlign(item)));
        p.setSpacingAfter(4f);
        ctx.getDocument().add(p);
    }
}
