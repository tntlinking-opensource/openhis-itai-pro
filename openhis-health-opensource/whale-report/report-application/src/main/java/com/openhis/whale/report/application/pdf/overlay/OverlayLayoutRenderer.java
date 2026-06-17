package com.openhis.whale.report.application.pdf.overlay;
import com.openhis.whale.report.application.layout.model.KvItemDef;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.lowagie.text.Image;
import com.openhis.whale.report.application.pdf.BindPathResolver;
import com.openhis.whale.report.application.pdf.PdfLayoutBoxUtils;
import com.openhis.whale.report.application.pdf.PrintImageLoader;
import com.openhis.whale.report.application.pdf.QrCodeImageFactory;
import com.openhis.whale.report.application.pdf.PdfSlashDrawer;
import com.openhis.whale.report.application.pdf.PrescriptionDrugTableSupport;
import com.openhis.whale.report.application.pdf.ReportPdfFontFactory;
import com.openhis.whale.report.application.pdf.PrintTextFormat;
import com.openhis.whale.report.application.pdf.ReportPdfUnitUtils;
import com.openhis.whale.report.application.pdf.TableAlignSupport;
import com.openhis.whale.report.application.layout.model.ColumnDef;
import com.openhis.whale.report.application.layout.support.KvRowLayoutSupport;
import com.openhis.whale.report.application.layout.support.KvRowLayoutSupport.ResolvedKvItem;
import com.openhis.whale.report.application.layout.model.LayoutBox;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.model.LayoutPage;
import com.openhis.whale.report.application.layout.model.LayoutStyle;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 套打 overlay：按 mm 框绝对定位绘制（页面左上角原点，Y 向下）。
 * <p>表格超出落格框行数时自动分页：每页重复绘制非 table 元素（对应每张预印纸），
 * table 按 {@code rowHeightMm} 切片续打。</p>
 * <p>guideImage 仅前端预览叠图，本类不绘制底图。</p>
 */
@Component
public class OverlayLayoutRenderer {

    private static final float MIN_FONT_PT = 6f;
    private static final float DEFAULT_TABLE_ROW_HEIGHT_MM = 4f;

    private final ReportPdfFontFactory fontFactory;
    private final OverlayBoxResolver boxResolver;
    private final ObjectMapper objectMapper;

    public OverlayLayoutRenderer(
            ReportPdfFontFactory fontFactory,
            OverlayBoxResolver boxResolver,
            ObjectMapper objectMapper) {
        this.fontFactory = fontFactory;
        this.boxResolver = boxResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * 套打多页渲染：每页重复非 table 元素，table 按行高分页续打。
     *
     * @param writer         PdfWriter
     * @param document       OpenPDF Document
     * @param layoutDocument 排版文档
     * @param dataset        打印数据集
     * @param elementIndex   元素定义索引
     */
    public void render(
            PdfWriter writer,
            Document document,
            LayoutDocument layoutDocument,
            Map<String, Object> dataset,
            Map<String, PrintElementDef> elementIndex) throws Exception {
        LayoutPage page = layoutDocument.getPage();
        int totalPages = computeTotalPages(layoutDocument, dataset, elementIndex, page);
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            if (pageIndex > 0) {
                document.newPage();
            }
            renderOverlayPage(writer, document, layoutDocument, dataset, elementIndex, page, pageIndex);
        }
    }

    private int computeTotalPages(
            LayoutDocument layoutDocument,
            Map<String, Object> dataset,
            Map<String, PrintElementDef> elementIndex,
            LayoutPage page) throws Exception {
        int maxPages = 1;
        for (LayoutItem item : layoutDocument.getItems()) {
            if (item == null || !Boolean.TRUE.equals(item.getVisible())) {
                continue;
            }
            PrintElementDef def = elementIndex.get(item.getElementCode());
            if (def == null || !"table".equalsIgnoreCase(def.getRenderType())) {
                continue;
            }
            LayoutBox box = boxResolver.resolveEffectiveBox(item, def, page);
            if (PrescriptionDrugTableSupport.useFixedSlotsWithSlash(def.getElementCode(), item, box)) {
                continue;
            }
            List<Map<String, Object>> rows = loadTableRows(item, def, dataset);
            if (rows.isEmpty()) {
                continue;
            }
            int rowsPerPage = resolveRowsPerPage(box, rows.size());
            int tablePages = (rows.size() + rowsPerPage - 1) / rowsPerPage;
            if (tablePages > maxPages) {
                maxPages = tablePages;
            }
        }
        return maxPages;
    }

    private void renderOverlayPage(
            PdfWriter writer,
            Document document,
            LayoutDocument layoutDocument,
            Map<String, Object> dataset,
            Map<String, PrintElementDef> elementIndex,
            LayoutPage page,
            int pageIndex) throws Exception {
        PdfContentByte canvas = writer.getDirectContent();
        Rectangle pageSize = document.getPageSize();

        for (LayoutItem item : layoutDocument.getItems()) {
            if (item == null || !Boolean.TRUE.equals(item.getVisible())) {
                continue;
            }
            PrintElementDef def = elementIndex.get(item.getElementCode());
            if (def == null) {
                throw new BusinessException("RPT_ELEMENT_UNKNOWN", "元素未定义: " + item.getElementCode());
            }
            LayoutBox box = boxResolver.resolveEffectiveBox(item, def, page);
            Rectangle rect = PdfLayoutBoxUtils.toPdfRectangleFromPaperEdge(box, pageSize);
            String renderType = def.getRenderType();
            if ("table".equalsIgnoreCase(renderType)) {
                drawTableInBox(canvas, rect, item, def, dataset, box, pageIndex);
            } else if ("text".equalsIgnoreCase(renderType) || "staticTitle".equalsIgnoreCase(renderType)) {
                drawTextInBox(canvas, rect, item, def, dataset);
            } else if ("kvRow".equalsIgnoreCase(renderType)) {
                drawKvInBox(canvas, rect, item, def, dataset);
            } else if ("qrcode".equalsIgnoreCase(renderType)) {
                drawQrcodeInBox(canvas, rect, def, dataset);
            } else if ("image".equalsIgnoreCase(renderType)) {
                drawImageInBox(canvas, rect, def, dataset);
            }
        }
    }

    private void drawTextInBox(
            PdfContentByte canvas,
            Rectangle rect,
            LayoutItem item,
            PrintElementDef def,
            Map<String, Object> dataset) throws Exception {
        String text = resolveOverlayText(item, def, dataset);
        if (!StringUtils.hasText(text)) {
            return;
        }
        float fontSize = resolveFontSizePt(item, 9f);
        Font font = fontFor(item, fontSize, Font.NORMAL);
        drawParagraphInRect(
                canvas,
                rect,
                text,
                font,
                "shrinkFont".equalsIgnoreCase(resolveOverflow(item, def)),
                TableAlignSupport.toPdfAlign(TableAlignSupport.resolveTableDefaultAlign(item)));
    }

    private void drawKvInBox(
            PdfContentByte canvas,
            Rectangle rect,
            LayoutItem item,
            PrintElementDef def,
            Map<String, Object> dataset) throws Exception {
        Object root = BindPathResolver.resolve(dataset, def.getBindPath());
        Map<String, Object> row;
        if (root instanceof Map<?, ?> mapRoot) {
            row = castMap(mapRoot);
        } else {
            row = Map.of();
        }
        List<com.openhis.whale.report.application.layout.model.KvItemDef> kvItems = parseKvItems(def.getKvItemsJson());
        List<ResolvedKvItem> visible = KvRowLayoutSupport.resolveVisibleItems(item, row, kvItems);
        if (visible.isEmpty()) {
            return;
        }
        int columnsPerRow = KvRowLayoutSupport.resolveColumnsPerRow(item);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < visible.size(); i++) {
            if (i > 0) {
                if (columnsPerRow > 1 && i % columnsPerRow == 0) {
                    sb.append('\n');
                } else {
                    sb.append(' ');
                }
            }
            sb.append(visible.get(i).value());
        }
        float fontSize = resolveFontSizePt(item, 9f);
        Font font = fontFor(item, fontSize, Font.NORMAL);
        drawParagraphInRect(
                canvas,
                rect,
                sb.toString(),
                font,
                true,
                TableAlignSupport.toPdfAlign(TableAlignSupport.resolveTableDefaultAlign(item)));
    }

    private void drawQrcodeInBox(
            PdfContentByte canvas,
            Rectangle rect,
            PrintElementDef def,
            Map<String, Object> dataset) throws Exception {
        String payload = BindPathResolver.asText(BindPathResolver.resolve(dataset, def.getBindPath()));
        if (!StringUtils.hasText(payload)) {
            return;
        }
        int px = Math.max(96, (int) Math.max(rect.getWidth(), rect.getHeight()));
        byte[] png = QrCodeImageFactory.encodePng(payload, px);
        Image image = Image.getInstance(png);
        image.scaleAbsolute(rect.getWidth(), rect.getHeight());
        image.setAbsolutePosition(rect.getLeft(), rect.getBottom());
        canvas.addImage(image);
    }

    private void drawImageInBox(
            PdfContentByte canvas,
            Rectangle rect,
            PrintElementDef def,
            Map<String, Object> dataset) throws Exception {
        String source = BindPathResolver.asText(BindPathResolver.resolve(dataset, def.getBindPath()));
        if (!StringUtils.hasText(source)) {
            return;
        }
        byte[] bytes = PrintImageLoader.loadBytes(source);
        if (bytes == null || bytes.length == 0) {
            return;
        }
        Image image = Image.getInstance(bytes);
        image.scaleAbsolute(rect.getWidth(), rect.getHeight());
        image.setAbsolutePosition(rect.getLeft(), rect.getBottom());
        canvas.addImage(image);
    }

    private void drawTableInBox(
            PdfContentByte canvas,
            Rectangle rect,
            LayoutItem item,
            PrintElementDef def,
            Map<String, Object> dataset,
            LayoutBox box,
            int pageIndex) throws Exception {
        List<ColumnDef> visible = visibleColumns(item, def);
        if (visible.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = loadTableRows(item, def, dataset);
        if (PrescriptionDrugTableSupport.useFixedSlotsWithSlash(def.getElementCode(), item, box)) {
            if (pageIndex > 0) {
                return;
            }
            drawFixedSlotPrescriptionTable(canvas, rect, item, visible, rows, box);
            return;
        }
        if (rows.isEmpty()) {
            return;
        }
        int rowsPerPage = resolveRowsPerPage(box, rows.size());
        int fromIndex = pageIndex * rowsPerPage;
        if (fromIndex >= rows.size()) {
            return;
        }
        int toIndex = Math.min(fromIndex + rowsPerPage, rows.size());
        List<Map<String, Object>> pageRows = rows.subList(fromIndex, toIndex);

        float rowHeightMm = resolveRowHeightMm(box, rows.size());
        float rowHeightPt = ReportPdfUnitUtils.mmToPt(rowHeightMm);
        float fontSize = resolveFontSizePt(item, 8f);
        Font font = fontFor(item, fontSize, Font.NORMAL);

        float yTop = rect.getTop();
        int rowIndex = 0;
        for (Map<String, Object> row : pageRows) {
            float yBottom = yTop - (rowIndex + 1) * rowHeightPt;
            if (yBottom < rect.getBottom()) {
                break;
            }
            Rectangle rowRect = new Rectangle(rect.getLeft(), yBottom, rect.getRight(), yTop - rowIndex * rowHeightPt);
            drawTableRowInRect(canvas, rowRect, visible, row, item, font, true);
            rowIndex++;
        }
    }

    private void drawFixedSlotPrescriptionTable(
            PdfContentByte canvas,
            Rectangle rect,
            LayoutItem item,
            List<ColumnDef> visible,
            List<Map<String, Object>> rows,
            LayoutBox box) throws Exception {
        int maxRows = PrescriptionDrugTableSupport.resolveMaxRows(item, box);
        int dataRows = Math.min(rows.size(), maxRows);
        float rowHeightMm = PrescriptionDrugTableSupport.resolveFixedRowHeightMm(box, maxRows);
        float rowHeightPt = ReportPdfUnitUtils.mmToPt(rowHeightMm);
        float fontSize = resolveFontSizePt(item, 8f);
        Font font = fontFor(item, fontSize, Font.NORMAL);

        float yTop = rect.getTop();
        for (int slot = 0; slot < dataRows; slot++) {
            float yBottom = yTop - (slot + 1) * rowHeightPt;
            if (yBottom < rect.getBottom()) {
                break;
            }
            Rectangle rowRect = new Rectangle(rect.getLeft(), yBottom, rect.getRight(), yTop - slot * rowHeightPt);
            drawTableRowInRect(canvas, rowRect, visible, rows.get(slot), item, font, true);
        }
        if (dataRows < maxRows) {
            float emptyTop = yTop - dataRows * rowHeightPt;
            float emptyBottom = yTop - maxRows * rowHeightPt;
            if (emptyBottom < rect.getBottom()) {
                emptyBottom = rect.getBottom();
            }
            if (emptyTop > emptyBottom) {
                Rectangle emptyRect = new Rectangle(rect.getLeft(), emptyBottom, rect.getRight(), emptyTop);
                PdfSlashDrawer.drawDiagonalSlash(canvas, emptyRect, 0.5f);
            }
        }
    }

    private List<Map<String, Object>> loadTableRows(
            LayoutItem item,
            PrintElementDef def,
            Map<String, Object> dataset) throws Exception {
        Object rowsObj = BindPathResolver.resolve(dataset, def.getBindPath());
        return BindPathResolver.asRowList(rowsObj);
    }

    private List<ColumnDef> visibleColumns(LayoutItem item, PrintElementDef def) throws Exception {
        List<ColumnDef> columns = parseColumns(def.getColumnDefsJson());
        List<ColumnDef> visible = new ArrayList<>();
        for (ColumnDef col : columns) {
            if (item.getHiddenColumns() != null && item.getHiddenColumns().contains(col.getField())) {
                continue;
            }
            visible.add(col);
        }
        return visible;
    }

    private static int resolveRowsPerPage(LayoutBox box, int totalRows) {
        float rowHeightMm = resolveRowHeightMm(box, totalRows);
        float heightMm = nvl(box.getHeightMm());
        if (heightMm <= 0f) {
            return Math.max(1, totalRows);
        }
        return Math.max(1, (int) Math.floor(heightMm / rowHeightMm));
    }

    private static float resolveRowHeightMm(LayoutBox box, int totalRows) {
        if (box.getRowHeightMm() != null && box.getRowHeightMm() > 0) {
            return box.getRowHeightMm();
        }
        float heightMm = nvl(box.getHeightMm());
        if (heightMm > 0 && totalRows > 0) {
            return Math.max(DEFAULT_TABLE_ROW_HEIGHT_MM, heightMm / totalRows);
        }
        return DEFAULT_TABLE_ROW_HEIGHT_MM;
    }

    private void drawTableRowInRect(
            PdfContentByte canvas,
            Rectangle rowRect,
            List<ColumnDef> columns,
            Map<String, Object> row,
            LayoutItem item,
            Font font,
            boolean shrinkIfOverflow) throws Exception {
        if (columns == null || columns.isEmpty()) {
            return;
        }
        String tableDefaultAlign = TableAlignSupport.resolveTableDefaultAlign(item);
        float[] colWidths = TableAlignSupport.resolveColumnWidthPoints(columns, rowRect.getWidth());
        float x = rowRect.getLeft();
        for (int i = 0; i < columns.size(); i++) {
            ColumnDef col = columns.get(i);
            float width = i < colWidths.length ? colWidths[i] : 0f;
            if (width <= 0f) {
                continue;
            }
            Rectangle cellRect = new Rectangle(x, rowRect.getBottom(), x + width, rowRect.getTop());
            String val = BindPathResolver.asText(row.get(col.getField()));
            if (StringUtils.hasText(val)) {
                int align = TableAlignSupport.resolveColumnPdfAlign(item, col, tableDefaultAlign);
                drawParagraphInRect(canvas, cellRect, val, font, shrinkIfOverflow, align);
            }
            x += width;
        }
    }

    private void drawParagraphInRect(
            PdfContentByte canvas,
            Rectangle rect,
            String text,
            Font font,
            boolean shrinkIfOverflow,
            int horizontalAlign) throws Exception {
        if (!StringUtils.hasText(text)) {
            return;
        }
        Font useFont = font;
        if (shrinkIfOverflow) {
            useFont = shrinkToFit(text, font, rect);
        }
        if (!text.contains("\n")) {
            drawTopAlignedSingleLine(canvas, rect, text, useFont, horizontalAlign);
            return;
        }
        Paragraph paragraph = new Paragraph(text, useFont);
        paragraph.setLeading(useFont.getSize() * 1.05f);
        paragraph.setSpacingBefore(0f);
        paragraph.setSpacingAfter(0f);
        paragraph.setAlignment(horizontalAlign);
        ColumnText ct = new ColumnText(canvas);
        ct.setUseAscender(true);
        ct.setSimpleColumn(rect.getLeft(), rect.getBottom(), rect.getRight(), rect.getTop());
        ct.addText(paragraph);
        ct.go();
    }

    /** 单行文本顶对齐（ColumnText + useAscender，避免页顶元素被裁切）。 */
    private static void drawTopAlignedSingleLine(
            PdfContentByte canvas,
            Rectangle rect,
            String text,
            Font font,
            int horizontalAlign) throws com.lowagie.text.DocumentException {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setLeading(font.getSize() * 1.05f);
        paragraph.setSpacingBefore(0f);
        paragraph.setSpacingAfter(0f);
        paragraph.setAlignment(horizontalAlign);
        ColumnText ct = new ColumnText(canvas);
        ct.setUseAscender(true);
        ct.setAlignment(horizontalAlign);
        ct.setSimpleColumn(rect.getLeft(), rect.getBottom(), rect.getRight(), rect.getTop());
        ct.addText(paragraph);
        ct.go();
    }

    private String resolveOverlayText(LayoutItem item, PrintElementDef def, Map<String, Object> dataset) {
        if ("staticTitle".equalsIgnoreCase(def.getRenderType())) {
            if (StringUtils.hasText(item.getLabelOverride())) {
                return item.getLabelOverride();
            }
            if (StringUtils.hasText(def.getDefaultLabel())) {
                return def.getDefaultLabel();
            }
            return def.getLabel();
        }
        Object value = BindPathResolver.resolve(dataset, def.getBindPath());
        String valueText = BindPathResolver.asText(value);
        if (StringUtils.hasText(item.getLabelOverride())) {
            return PrintTextFormat.formatLabelValue(item.getLabelOverride(), valueText, false);
        }
        return PrintTextFormat.formatLabelValue(def.getLabel(), valueText, false);
    }

    private Font shrinkToFit(String text, Font font, Rectangle rect) {
        float size = font.getSize();
        while (size > 6f) {
            Font trial = fontFactory.bodyFont(size, font.getStyle());
            float width = trial.getBaseFont().getWidthPoint(text, size);
            if (width <= rect.getWidth()) {
                return trial;
            }
            size -= 0.5f;
        }
        return fontFactory.bodyFont(MIN_FONT_PT, font.getStyle());
    }

    private Font fontFor(LayoutItem item, float defaultPt, int style) {
        LayoutStyle layoutStyle = item.getStyle();
        float size = defaultPt;
        int flags = style;
        if (layoutStyle != null) {
            if (layoutStyle.getFontSizePt() != null) {
                size = layoutStyle.getFontSizePt();
            }
            if (Boolean.TRUE.equals(layoutStyle.getBold())) {
                flags |= Font.BOLD;
            }
        }
        return fontFactory.bodyFont(size, flags);
    }

    private float resolveFontSizePt(LayoutItem item, float defaultPt) {
        LayoutStyle style = item.getStyle();
        if (style != null && style.getFontSizePt() != null) {
            return style.getFontSizePt();
        }
        return defaultPt;
    }

    private String resolveOverflow(LayoutItem item, PrintElementDef def) {
        if (item.getBox() != null && StringUtils.hasText(item.getBox().getOverflow())) {
            return item.getBox().getOverflow();
        }
        if (StringUtils.hasText(def.getBoxJson())) {
            try {
                LayoutBox box = objectMapper.readValue(def.getBoxJson(), LayoutBox.class);
                if (box != null && StringUtils.hasText(box.getOverflow())) {
                    return box.getOverflow();
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return "clip";
    }

    private List<ColumnDef> parseColumns(String json) throws Exception {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<List<ColumnDef>>() {});
    }

    private List<com.openhis.whale.report.application.layout.model.KvItemDef> parseKvItems(String json) throws Exception {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static float nvl(Float v) {
        if (v != null) {
            return v;
        }
        return 0f;
    }
}
