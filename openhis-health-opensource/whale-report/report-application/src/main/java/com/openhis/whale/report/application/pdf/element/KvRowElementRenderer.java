package com.openhis.whale.report.application.pdf.element;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.openhis.whale.report.application.pdf.BindPathResolver;
import com.openhis.whale.report.application.pdf.PrintTextFormat;
import com.openhis.whale.report.application.layout.model.KvItemDef;
import com.openhis.whale.report.application.layout.support.KvRowLayoutSupport;
import com.openhis.whale.report.application.layout.support.KvRowLayoutSupport.ResolvedKvItem;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
public class KvRowElementRenderer implements PrintElementRenderer {

    private static final float GRID_COLUMN_GAP_PT = 4f;

    private final ObjectMapper objectMapper;

    public KvRowElementRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String renderType() {
        return "kvRow";
    }

    @Override
    public void render(PrintElementRenderContext ctx, LayoutItem item, PrintElementDef def) throws Exception {
        Object root = BindPathResolver.resolve(ctx.getDataset(), def.getBindPath());
        Map<String, Object> row;
        if (root instanceof Map<?, ?> mapRoot) {
            row = castMap(mapRoot);
        } else {
            row = Map.of();
        }
        List<KvItemDef> kvItems = parseKvItems(def.getKvItemsJson());
        List<ResolvedKvItem> visible = KvRowLayoutSupport.resolveVisibleItems(item, row, kvItems);
        if (visible.isEmpty()) {
            return;
        }
        Font font = ctx.fontFor(item, def, 10f, Font.NORMAL);
        int columnsPerRow = KvRowLayoutSupport.resolveColumnsPerRow(item);
        if (columnsPerRow <= 1) {
            renderVertical(ctx, visible, font);
            return;
        }
        renderGrid(ctx, visible, font, columnsPerRow);
    }

    private void renderVertical(PrintElementRenderContext ctx, List<ResolvedKvItem> visible, Font font)
            throws Exception {
        int maxLabelChars = KvRowLayoutSupport.resolveMaxLabelChars(visible);
        for (ResolvedKvItem kv : visible) {
            Paragraph line = new Paragraph(formatKvLine(kv, maxLabelChars), font);
            line.setSpacingAfter(2f);
            ctx.getDocument().add(line);
        }
        Paragraph gap = new Paragraph(" ", font);
        gap.setSpacingAfter(4f);
        ctx.getDocument().add(gap);
    }

    private void renderGrid(
            PrintElementRenderContext ctx, List<ResolvedKvItem> visible, Font font, int columnsPerRow)
            throws Exception {
        int[] maxLabelPerCol = KvRowLayoutSupport.resolveMaxLabelCharsPerColumn(visible, columnsPerRow);
        PdfPTable table = new PdfPTable(columnsPerRow);
        table.setWidthPercentage(100f);
        table.setSpacingAfter(4f);
        float[] widths = new float[columnsPerRow];
        for (int i = 0; i < columnsPerRow; i++) {
            widths[i] = 1f;
        }
        table.setWidths(widths);
        for (int i = 0; i < visible.size(); i += columnsPerRow) {
            for (int c = 0; c < columnsPerRow; c++) {
                int idx = i + c;
                if (idx < visible.size()) {
                    table.addCell(kvAlignedCell(visible.get(idx), font, maxLabelPerCol[c], c > 0));
                } else {
                    table.addCell(emptyCell(font));
                }
            }
        }
        ctx.getDocument().add(table);
    }

    private static String formatKvLine(ResolvedKvItem kv, int maxLabelChars) {
        String paddedLabel = PrintTextFormat.padKvLabel(kv.def().getLabel(), maxLabelChars);
        return paddedLabel + "：" + kv.value();
    }

    private static PdfPCell kvAlignedCell(ResolvedKvItem kv, Font font, int maxLabelChars, boolean secondColumn)
            throws Exception {
        PdfPTable inner = new PdfPTable(2);
        inner.setWidthPercentage(100f);
        inner.setWidths(new float[] {1.35f, 0.65f});

        String paddedLabel = PrintTextFormat.padKvLabel(kv.def().getLabel(), maxLabelChars);
        inner.addCell(textCell(new Phrase(paddedLabel + "：", font), Element.ALIGN_LEFT));
        inner.addCell(textCell(new Phrase(kv.value(), font), Element.ALIGN_RIGHT));

        PdfPCell outer = new PdfPCell(inner);
        outer.setBorder(PdfPCell.NO_BORDER);
        outer.setPadding(1f);
        outer.setPaddingBottom(2f);
        if (secondColumn) {
            outer.setPaddingLeft(GRID_COLUMN_GAP_PT);
        }
        return outer;
    }

    private static PdfPCell textCell(Phrase phrase, int horizontalAlign) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(0f);
        cell.setHorizontalAlignment(horizontalAlign);
        return cell;
    }

    private static PdfPCell emptyCell(Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(" ", font));
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(1f);
        return cell;
    }

    private List<KvItemDef> parseKvItems(String json) throws Exception {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<List<KvItemDef>>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
