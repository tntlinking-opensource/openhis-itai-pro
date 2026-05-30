/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.application.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.openhis.whale.core.application.util.FileUploadUtils;
import com.openhis.whale.emr.application.pdf.config.PrintConfig;
import com.openhis.whale.emr.application.pdf.model.PdfRenderOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 将 Tiptap/ProseMirror JSON 渲染到 OpenPDF Document。
 */
@Component
@RequiredArgsConstructor
public class EmrContentJsonRenderer {

    private static final String TYPE_DOC = "doc";
    private static final String TYPE_TEXT = "text";
    private static final String ATTRS = "attrs";
    private static final String CONTENT = "content";
    private static final String TYPE = "type";
    private static final String MARKS = "marks";

    private final ObjectMapper objectMapper;
    private final EmrPdfFontFactory fontFactory;

    private Document document;
    private PdfWriter writer;
    private PrintConfig printConfig;
    private PdfRenderOptions renderOptions;
    private Font bodyFont;
    private Font titleFont;
    private int pagesRendered;
    private boolean previewTruncated;

    public boolean render(
            Document document,
            PdfWriter writer,
            String contentJson,
            PrintConfig printConfig,
            PdfRenderOptions renderOptions) throws Exception {
        this.document = document;
        this.writer = writer;
        this.printConfig = printConfig;
        this.renderOptions = renderOptions;
        float bodySize = printConfig.getFont().getDefaultSizePt();
        this.bodyFont = fontFactory.bodyFont(bodySize, Font.NORMAL);
        this.titleFont = fontFactory.bodyFont(printConfig.getFont().getTitleSizePt(), Font.BOLD);
        this.pagesRendered = 0;
        this.previewTruncated = false;

        JsonNode root = objectMapper.readTree(contentJson);
        if (root == null || !root.isObject() || !TYPE_DOC.equals(root.path(TYPE).asText(null))) {
            document.add(new Paragraph("（空文档）", bodyFont));
            return false;
        }
        JsonNode content = root.get(CONTENT);
        if (content == null || !content.isArray() || content.isEmpty()) {
            document.add(new Paragraph("（空文档）", bodyFont));
            return false;
        }
        JsonNode filtered = filterByRegionNames(content, renderOptions);
        renderBlockArray(filtered);
        return previewTruncated;
    }

    private JsonNode filterByRegionNames(JsonNode content, PdfRenderOptions options) {
        if (options.getRegionNames() == null || options.getRegionNames().isEmpty()) {
            return content;
        }
        Set<String> allowed = new HashSet<>(options.getRegionNames());
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode node : content) {
            collectRegionSubtree(node, allowed, out);
        }
        return out;
    }

    private void collectRegionSubtree(JsonNode node, Set<String> allowed, ArrayNode out) {
        if (!node.isObject()) {
            return;
        }
        String type = normalizeType(node.path(TYPE).asText(null));
        if ("region".equals(type)) {
            String name = attrText(node, "name");
            if (name != null && allowed.contains(name)) {
                out.add(node);
            }
            return;
        }
        if (node.has(CONTENT) && node.get(CONTENT).isArray()) {
            for (JsonNode child : node.get(CONTENT)) {
                collectRegionSubtree(child, allowed, out);
            }
        }
    }

    private void renderBlockArray(JsonNode blocks) throws Exception {
        if (blocks == null || !blocks.isArray()) {
            return;
        }
        for (JsonNode block : blocks) {
            if (checkPreviewPageLimit()) {
                return;
            }
            renderNode(block);
        }
    }

    private void renderNode(JsonNode node) throws Exception {
        if (!node.isObject() || shouldSkipNode(node)) {
            return;
        }
        String type = normalizeType(node.path(TYPE).asText(null));
        if (type == null) {
            return;
        }
        switch (type) {
            case "paragraph" -> renderParagraph(node, bodyFont, Element.ALIGN_LEFT);
            case "heading" -> renderHeading(node);
            case "pagebreak", "emrpagebreak" -> newPage();
            case "region" -> renderRegion(node);
            case "section" -> renderSection(node);
            case "dataelement", "emrdataelement", "emrtextfield", "emrselectfield", "emrcheckboxfield" ->
                    renderDataElementBlock(node);
            case "emrmetadatachip" -> renderMetadataChipParagraph(node);
            case "emrfixedtext" -> renderFixedText(node);
            case "emrfreetext" -> renderFreeText(node);
            case "bulletlist", "orderedlist" -> renderList(node, type);
            case "blockquote" -> renderBlockquote(node);
            case "image" -> renderImage(node);
            case "table" -> renderTable(node);
            case "horizontalrule", "emrhorizontaldashed", "emrstyledhorizontalrule" -> renderHorizontalRule();
            case "emrtoc" -> { /* 目录节点不输出到 PDF */ }
            default -> {
                if (node.has(CONTENT) && node.get(CONTENT).isArray()) {
                    renderBlockArray(node.get(CONTENT));
                }
            }
        }
    }

    private void renderRegion(JsonNode node) throws Exception {
        String title = attrText(node, "title");
        if (StringUtils.hasText(title)) {
            Paragraph p = new Paragraph(title, titleFont);
            p.setSpacingBefore(6f);
            p.setSpacingAfter(4f);
            document.add(p);
        }
        renderBlockArray(node.get(CONTENT));
    }

    private void renderSection(JsonNode node) throws Exception {
        String title = attrText(node, "title");
        if (StringUtils.hasText(title)) {
            Font sectionFont = fontFactory.bodyFont(printConfig.getFont().getDefaultSizePt(), Font.BOLD);
            Paragraph p = new Paragraph(title, sectionFont);
            p.setSpacingBefore(4f);
            p.setSpacingAfter(2f);
            document.add(p);
        }
        renderBlockArray(node.get(CONTENT));
    }

    private void renderDataElementBlock(JsonNode node) throws Exception {
        String label = firstNonBlank(attrText(node, "labelSnapshot"), attrText(node, "title"), attrText(node, "name"));
        String value = extractDataElementValue(node);
        String line = StringUtils.hasText(label) ? label + "：" + nullToEmpty(value) : nullToEmpty(value);
        if (!StringUtils.hasText(line)) {
            return;
        }
        Paragraph p = new Paragraph(line, bodyFont);
        p.setLeading(bodyFont.getSize() * printConfig.getFont().getLineHeight());
        document.add(p);
    }

    private void renderMetadataChipParagraph(JsonNode node) throws Exception {
        String value = firstNonBlank(attrText(node, "value"), attrText(node, "label"));
        if (!StringUtils.hasText(value)) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("data:image/")) {
            com.lowagie.text.Image img = loadPdfImage(trimmed);
            if (img != null) {
                img.scaleToFit(120f, 48f);
                document.add(img);
                document.add(new Paragraph(" ", bodyFont));
            }
            return;
        }
        Paragraph p = new Paragraph(trimmed, bodyFont);
        document.add(p);
    }

    private void renderFixedText(JsonNode node) throws Exception {
        String text = attrText(node, "text");
        if (!StringUtils.hasText(text) && node.has("segments") && node.get("segments").isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode seg : node.get("segments")) {
                sb.append(seg.path("text").asText(""));
            }
            text = sb.toString();
        }
        if (!StringUtils.hasText(text) && node.has(CONTENT)) {
            Paragraph p = buildParagraphFromInline(node.get(CONTENT), bodyFont);
            if (p != null) {
                document.add(p);
            }
            return;
        }
        if (StringUtils.hasText(text)) {
            Paragraph p = new Paragraph(text, bodyFont);
            document.add(p);
        }
    }

    private void renderFreeText(JsonNode node) throws Exception {
        if (node.has(CONTENT)) {
            Paragraph p = buildParagraphFromInline(node.get(CONTENT), bodyFont);
            if (p != null) {
                document.add(p);
            }
        }
    }

    private void renderHeading(JsonNode node) throws Exception {
        int level = node.path(ATTRS).path("level").asInt(1);
        float size = printConfig.getFont().getTitleSizePt() - Math.max(0, level - 1) * 2f;
        Font font = fontFactory.bodyFont(Math.max(size, bodyFont.getSize()), Font.BOLD);
        renderParagraph(node, font, Element.ALIGN_LEFT);
    }

    private void renderParagraph(JsonNode node, Font baseFont, int alignment) throws Exception {
        Paragraph p = node.has(CONTENT)
                ? buildParagraphFromInline(node.get(CONTENT), baseFont)
                : new Paragraph("", baseFont);
        if (p == null) {
            p = new Paragraph("", baseFont);
        }
        p.setAlignment(resolveTextAlign(node, alignment));
        p.setLeading(baseFont.getSize() * printConfig.getFont().getLineHeight());
        applyBlockSpacing(node, p);
        document.add(p);
    }

    private Paragraph buildParagraphFromInline(JsonNode inlineNodes, Font baseFont) {
        if (inlineNodes == null || !inlineNodes.isArray()) {
            return new Paragraph("", baseFont);
        }
        Paragraph p = new Paragraph();
        p.setLeading(baseFont.getSize() * printConfig.getFont().getLineHeight());
        for (JsonNode inline : inlineNodes) {
            if (!inline.isObject()) {
                continue;
            }
            String type = normalizeType(inline.path(TYPE).asText(null));
            if (TYPE_TEXT.equals(type)) {
                appendTextNode(p, inline, baseFont);
            } else if (isInlineFieldType(type)) {
                String v = extractDataElementValue(inline);
                if (StringUtils.hasText(v)) {
                    p.add(new Chunk(v, baseFont));
                }
            } else if (inline.has(CONTENT)) {
                for (JsonNode nested : inline.get(CONTENT)) {
                    if (TYPE_TEXT.equals(nested.path(TYPE).asText(null))) {
                        appendTextNode(p, nested, baseFont);
                    }
                }
            }
        }
        return p;
    }

    private void appendTextNode(Paragraph p, JsonNode textNode, Font baseFont) {
        String text = textNode.path("text").asText("");
        if (text.isEmpty()) {
            return;
        }
        if (!renderOptions.isIncludeTrackChanges() && hasMark(textNode, "deletion")) {
            return;
        }
        int style = Font.NORMAL;
        if (hasMark(textNode, "bold")) {
            style |= Font.BOLD;
        }
        if (hasMark(textNode, "italic")) {
            style |= Font.ITALIC;
        }
        if (hasMark(textNode, "underline")) {
            style |= Font.UNDERLINE;
        }
        if (hasMark(textNode, "strike") || hasMark(textNode, "deletion")) {
            style |= Font.STRIKETHRU;
        }
        Font f = fontFactory.bodyFont(baseFont.getSize(), style);
        p.add(new Chunk(text, f));
    }

    private void renderList(JsonNode node, String type) throws Exception {
        if (!node.has(CONTENT) || !node.get(CONTENT).isArray()) {
            return;
        }
        int index = 1;
        for (JsonNode item : node.get(CONTENT)) {
            if (!"listItem".equals(normalizeType(item.path(TYPE).asText(null)))) {
                continue;
            }
            JsonNode itemContent = item.get(CONTENT);
            if (itemContent == null || !itemContent.isArray()) {
                continue;
            }
            for (JsonNode block : itemContent) {
                String prefix = "orderedlist".equals(type) ? (index + ". ") : "• ";
                Paragraph p = buildParagraphFromBlockContent(block, prefix);
                if (p != null) {
                    document.add(p);
                }
            }
            index++;
        }
    }

    private Paragraph buildParagraphFromBlockContent(JsonNode block, String prefix) throws Exception {
        if (block == null) {
            return null;
        }
        String type = normalizeType(block.path(TYPE).asText(null));
        if ("paragraph".equals(type) && block.has(CONTENT)) {
            Paragraph p = buildParagraphFromInline(block.get(CONTENT), bodyFont);
            if (p != null && prefix != null) {
                p.add(0, new Chunk(prefix, bodyFont));
            }
            return p;
        }
        renderNode(block);
        return null;
    }

    private void renderBlockquote(JsonNode node) throws Exception {
        if (node.has(CONTENT)) {
            renderBlockArray(node.get(CONTENT));
        }
    }

    private void renderImage(JsonNode node) throws Exception {
        String src = attrText(node, "src");
        if (!StringUtils.hasText(src)) {
            return;
        }
        com.lowagie.text.Image img = loadPdfImage(src.trim());
        if (img == null) {
            return;
        }
        img.scaleToFit(480f, 360f);
        document.add(img);
        document.add(new Paragraph(" ", bodyFont));
    }

    private com.lowagie.text.Image loadPdfImage(String src) throws Exception {
        if (src.startsWith("data:")) {
            int comma = src.indexOf(',');
            if (comma > 0) {
                byte[] bytes = Base64.getDecoder().decode(src.substring(comma + 1));
                return com.lowagie.text.Image.getInstance(bytes);
            }
            return null;
        }
        if (src.startsWith("/uploads/emr/")) {
            String relative = src.substring("/uploads/emr/".length());
            String baseDir = FileUploadUtils.getDefaultBaseDir() + "/emr";
            File file = FileUploadUtils.getAbsoluteFile(baseDir, relative);
            if (file.isFile()) {
                return com.lowagie.text.Image.getInstance(file.getAbsolutePath());
            }
            return null;
        }
        if (src.startsWith("http://") || src.startsWith("https://")) {
            return com.lowagie.text.Image.getInstance(new URL(src));
        }
        return null;
    }

    private void renderTable(JsonNode node) throws Exception {
        if (!node.has(CONTENT) || !node.get(CONTENT).isArray()) {
            return;
        }
        List<JsonNode> rows = new ArrayList<>();
        int colCount = 0;
        for (JsonNode row : node.get(CONTENT)) {
            if (!"tableRow".equals(normalizeType(row.path(TYPE).asText(null)))) {
                continue;
            }
            rows.add(row);
            if (row.has(CONTENT) && row.get(CONTENT).isArray()) {
                colCount = Math.max(colCount, row.get(CONTENT).size());
            }
        }
        if (rows.isEmpty() || colCount <= 0) {
            return;
        }
        PdfPTable table = new PdfPTable(colCount);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(4f);
        table.setSpacingAfter(4f);
        for (JsonNode row : rows) {
            int written = 0;
            if (row.has(CONTENT) && row.get(CONTENT).isArray()) {
                for (JsonNode cell : row.get(CONTENT)) {
                    String cellText = extractCellText(cell);
                    PdfPCell pdfCell = new PdfPCell(new Phrase(nullToEmpty(cellText), bodyFont));
                    pdfCell.setPadding(4f);
                    table.addCell(pdfCell);
                    written++;
                }
            }
            while (written < colCount) {
                table.addCell(new PdfPCell(new Phrase("", bodyFont)));
                written++;
            }
        }
        document.add(table);
    }

    private String extractCellText(JsonNode cell) {
        if (cell == null || !cell.has(CONTENT)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : cell.get(CONTENT)) {
            if (block.has(CONTENT)) {
                for (JsonNode inline : block.get(CONTENT)) {
                    if (TYPE_TEXT.equals(inline.path(TYPE).asText(null))) {
                        sb.append(inline.path("text").asText(""));
                    }
                }
            }
        }
        return sb.toString();
    }

    private void renderHorizontalRule() throws Exception {
        Paragraph p = new Paragraph("————————————————————", bodyFont);
        p.setSpacingBefore(6f);
        p.setSpacingAfter(6f);
        document.add(p);
    }

    private void newPage() throws Exception {
        if (checkPreviewPageLimit()) {
            return;
        }
        document.newPage();
        pagesRendered = writer.getPageNumber();
    }

    private boolean checkPreviewPageLimit() {
        if (!renderOptions.isPreviewMode()) {
            return false;
        }
        int max = renderOptions.getPreviewMaxPages() > 0 ? renderOptions.getPreviewMaxPages() : 20;
        int current = writer.getPageNumber();
        if (current > max) {
            previewTruncated = true;
            return true;
        }
        return false;
    }

    private boolean shouldSkipNode(JsonNode node) {
        JsonNode attrs = node.get(ATTRS);
        if (attrs == null) {
            return false;
        }
        if (attrs.path("deleted").asBoolean(false) && !printConfig.isShowDeletedNodes()) {
            return true;
        }
        if (attrs.has("participatePrint") && !attrs.path("participatePrint").asBoolean(true)) {
            return true;
        }
        return false;
    }

    private static String extractDataElementValue(JsonNode node) {
        JsonNode attrs = node.get(ATTRS);
        if (attrs == null) {
            return "";
        }
        if (attrs.has("value") && !attrs.get("value").isNull()) {
            JsonNode v = attrs.get("value");
            if (v.isTextual()) {
                return v.asText();
            }
            return v.toString();
        }
        return "";
    }

    private static int resolveTextAlign(JsonNode node, int defaultAlign) {
        JsonNode attrs = node.get(ATTRS);
        if (attrs != null && attrs.has("textAlign")) {
            return switch (attrs.get("textAlign").asText("left")) {
                case "center" -> Element.ALIGN_CENTER;
                case "right" -> Element.ALIGN_RIGHT;
                case "justify" -> Element.ALIGN_JUSTIFIED;
                default -> defaultAlign;
            };
        }
        return defaultAlign;
    }

    private static void applyBlockSpacing(JsonNode node, Paragraph p) {
        JsonNode attrs = node.get(ATTRS);
        if (attrs == null) {
            return;
        }
        if (attrs.has("paragraphMarginTop")) {
            p.setSpacingBefore(6f);
        }
        if (attrs.has("paragraphMarginBottom")) {
            p.setSpacingAfter(6f);
        }
    }

    private static boolean hasMark(JsonNode textNode, String markType) {
        JsonNode marks = textNode.get(MARKS);
        if (marks == null || !marks.isArray()) {
            return false;
        }
        for (JsonNode mark : marks) {
            if (markType.equals(normalizeType(mark.path(TYPE).asText(null)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInlineFieldType(String type) {
        return "dataelement".equals(type)
                || "emrdataelement".equals(type)
                || "emrtextfield".equals(type)
                || "emrselectfield".equals(type)
                || "emrcheckboxfield".equals(type)
                || "emrmetadatachip".equals(type);
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        return type.replace("_", "").toLowerCase();
    }

    private static String attrText(JsonNode node, String key) {
        JsonNode attrs = node.get(ATTRS);
        if (attrs == null || !attrs.has(key) || attrs.get(key).isNull()) {
            return null;
        }
        return attrs.get(key).asText(null);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
