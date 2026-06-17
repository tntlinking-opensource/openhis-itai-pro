package com.openhis.whale.report.application.pdf.element;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.openhis.whale.report.application.pdf.BindPathResolver;
import com.openhis.whale.report.application.pdf.PrescriptionDrugTableSupport;
import com.openhis.whale.report.application.pdf.TableAlignSupport;
import com.openhis.whale.report.application.layout.model.ColumnDef;
import com.openhis.whale.report.application.layout.model.LayoutBox;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TableElementRenderer implements PrintElementRenderer {

    private final ObjectMapper objectMapper;

    public TableElementRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String renderType() {
        return "table";
    }

    @Override
    public void render(PrintElementRenderContext ctx, LayoutItem item, PrintElementDef def) throws Exception {
        List<ColumnDef> columns = parseColumns(def.getColumnDefsJson());
        List<ColumnDef> visible = new ArrayList<>();
        for (ColumnDef col : columns) {
            if (item.getHiddenColumns() != null && item.getHiddenColumns().contains(col.getField())) {
                continue;
            }
            visible.add(col);
        }
        if (visible.isEmpty()) {
            return;
        }
        Object rowsObj = BindPathResolver.resolve(ctx.getDataset(), def.getBindPath());
        List<Map<String, Object>> rows = BindPathResolver.asRowList(rowsObj);
        LayoutBox box = item.getBox();
        boolean fixedSlots = PrescriptionDrugTableSupport.useFixedSlotsWithSlash(def.getElementCode(), item, box);
        int maxRows = fixedSlots ? PrescriptionDrugTableSupport.resolveMaxRows(item, box) : rows.size();
        if (fixedSlots && rows.size() > maxRows) {
            rows = rows.subList(0, maxRows);
        }
        PdfPTable table = new PdfPTable(visible.size());
        table.setWidthPercentage(100f);
        table.setSpacingBefore(4f);
        table.setSpacingAfter(8f);
        float[] relativeWidths = TableAlignSupport.resolveRelativeColumnWidths(visible);
        if (relativeWidths.length == visible.size()) {
            table.setWidths(relativeWidths);
        }
        String tableDefaultAlign = TableAlignSupport.resolveTableDefaultAlign(item);
        Font headerFont = ctx.fontFor(item, def, 9f, Font.BOLD);
        Font cellFont = ctx.fontFor(item, def, 9f, Font.NORMAL);
        for (ColumnDef col : visible) {
            int headerAlign = TableAlignSupport.resolveColumnPdfAlign(item, col, tableDefaultAlign);
            table.addCell(cell(new Phrase(col.getHeader(), headerFont), headerAlign));
        }
        for (Map<String, Object> row : rows) {
            for (ColumnDef col : visible) {
                String val = BindPathResolver.asText(row.get(col.getField()));
                int cellAlign = TableAlignSupport.resolveColumnPdfAlign(item, col, tableDefaultAlign);
                table.addCell(cell(new Phrase(val, cellFont), cellAlign));
            }
        }
        if (fixedSlots && rows.size() < maxRows) {
            int emptyRows = maxRows - rows.size();
            float slashRowHeightPt = 14f;
            PdfPCell slashCell = new PdfPCell();
            slashCell.setColspan(visible.size());
            slashCell.setFixedHeight(slashRowHeightPt * emptyRows);
            slashCell.setBorder(PdfPCell.NO_BORDER);
            slashCell.setCellEvent(new SlashPdfPCellEvent());
            table.addCell(slashCell);
        }
        ctx.getDocument().add(table);
    }

    private List<ColumnDef> parseColumns(String json) throws Exception {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<List<ColumnDef>>() {});
    }

    private static PdfPCell cell(Phrase phrase, int horizontalAlign) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setPadding(3f);
        cell.setHorizontalAlignment(horizontalAlign);
        return cell;
    }
}
