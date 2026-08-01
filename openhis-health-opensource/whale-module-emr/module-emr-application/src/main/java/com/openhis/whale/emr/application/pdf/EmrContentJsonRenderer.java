/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

import static com.openhis.whale.core.application.util.StringUtils.firstNonBlank;
import static com.openhis.whale.core.application.util.StringUtils.nullToEmpty;

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
            case "paragraph" -> {
                String paraName = attrText(node, "name");
                // 正文 printFooter 仅供编辑器预览；正式页脚由 PdfPageEventHelper 绘制，避免末页重复
                if ("printFooter".equals(paraName) && isCustomPrintFooterEnabled()) {
                    return;
                }
                if ("hospitalTitle".equals(paraName)) {
                    // 客户 Word 医院名约 22pt 加粗
                    Font hospitalNameFont = fontFactory.bodyFont(22f, Font.BOLD);
                    renderHospitalTitle(node, hospitalNameFont);
                } else if ("formTitle".equals(paraName)) {
                    // 客户 Word 门诊病历标题约 16pt 加粗
                    Font formTitleFont = fontFactory.bodyFont(
                            printConfig.getFont().getTitleSizePt(),
                            Font.BOLD);
                    renderParagraph(node, formTitleFont, Element.ALIGN_CENTER);
                } else if ("printFooter".equals(paraName)) {
                    Font footerFont = fontFactory.bodyFont(9f, Font.NORMAL);
                    renderParagraph(node, footerFont, Element.ALIGN_CENTER);
                } else {
                    renderParagraph(node, bodyFont, Element.ALIGN_LEFT);
                }
            }
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
            case "horizontalrule", "emrhorizontaldashed", "emrstyledhorizontalrule" -> renderHorizontalRule(node);
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
        String type = normalizeType(node.path(TYPE).asText(null));
        if ("emrcheckboxfield".equals(type)) {
            Paragraph p = new Paragraph();
            p.setLeading(bodyFont.getSize() * printConfig.getFont().getLineHeight());
            appendCheckboxChunk(p, node, bodyFont);
            document.add(p);
            return;
        }
        String label = firstNonBlank(attrText(node, "labelSnapshot"), attrText(node, "title"), attrText(node, "name"));
        String value = extractDataElementValue(node);
        String line;
        if (StringUtils.hasText(label)) {
            line = label + "：" + nullToEmpty(value);
        } else {
            line = nullToEmpty(value);
        }
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
            // 与数据元一致：空值回退 metadata（fieldKey / elementId）
            value = extractDataElementValue(node);
        }
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
        applyBlockLayoutAttrs(node, p, baseFont);
        applyNamedParagraphLayout(node, p, baseFont);
        document.add(p);
    }

    /**
     * 页眉「院徽 + 医院名」：整组水平居中，院徽相对标题字垂直居中。
     * 不走通用 inline image（attrs 宽高常为屏像素，当 pt 放大后会与标题错位）。
     */
    private void renderHospitalTitle(JsonNode node, Font titleFont) throws Exception {
        Phrase phrase = buildHospitalTitlePhrase(node.get(CONTENT), titleFont);
        Paragraph p = new Paragraph(phrase);
        p.setAlignment(Element.ALIGN_CENTER);
        float logoFit = hospitalLogoFitPt(titleFont);
        float lineHeight = printConfig.getFont().getLineHeight();
        p.setLeading(Math.max(titleFont.getSize() * lineHeight, logoFit + 2f));
        p.setSpacingBefore(2f);
        p.setSpacingAfter(2f);
        document.add(p);
    }

    private Phrase buildHospitalTitlePhrase(JsonNode inlineNodes, Font titleFont) {
        Phrase phrase = new Phrase();
        if (inlineNodes == null || !inlineNodes.isArray()) {
            return phrase;
        }
        for (JsonNode inline : inlineNodes) {
            if (!inline.isObject()) {
                continue;
            }
            String type = normalizeType(inline.path(TYPE).asText(null));
            if (TYPE_TEXT.equals(type)) {
                String text = inline.path("text").asText("");
                if (!text.isEmpty()) {
                    phrase.add(new Chunk(text, titleFont));
                }
            } else if ("image".equals(type)) {
                Chunk logo = buildHospitalLogoChunk(inline, titleFont);
                if (logo != null) {
                    phrase.add(logo);
                }
            } else if (isInlineFieldType(type)) {
                String v = extractDataElementDisplay(inline);
                // 医院名空值不占位，避免预览出现空白方框感
                if (StringUtils.hasText(v)) {
                    phrase.add(new Chunk(v, titleFont));
                }
            }
        }
        return phrase;
    }

    /** 院徽边长约 1.35× 标题字号，上限 26pt（与屏幕 2.2em 观感接近） */
    private static float hospitalLogoFitPt(Font titleFont) {
        float size = titleFont != null ? titleFont.getSize() : 18f;
        return Math.min(26f, Math.max(18f, size * 1.35f));
    }

    private Chunk buildHospitalLogoChunk(JsonNode imageNode, Font titleFont) {
        String src = attrText(imageNode, "src");
        if (!StringUtils.hasText(src)) {
            return null;
        }
        try {
            com.lowagie.text.Image img = loadPdfImage(src.trim());
            if (img == null) {
                return null;
            }
            float fit = hospitalLogoFitPt(titleFont);
            img.scaleToFit(fit, fit);
            // OpenPDF：offsetY 相对文字基线；使院徽中线贴近标题字中线
            float fontSize = titleFont != null ? titleFont.getSize() : 18f;
            float offsetY = (fontSize - img.getScaledHeight()) / 2f - fontSize * 0.12f;
            return new Chunk(img, 0, offsetY, true);
        } catch (Exception e) {
            return null;
        }
    }

    /** 入院安全告知书等段落名：紧凑行距/首行缩进，贴近 Word A4 单页 */
    private void applyNamedParagraphLayout(JsonNode node, Paragraph p, Font baseFont) {
        String name = attrText(node, "name");
        if (!StringUtils.hasText(name)) {
            return;
        }
        switch (name) {
            case "hospitalTitle" -> {
                p.setSpacingBefore(2f);
                p.setSpacingAfter(2f);
            }
            case "formTitle" -> {
                p.setSpacingBefore(2f);
                p.setSpacingAfter(6f);
            }
            // 门诊/入院临床段：贴近纸质（行距 1.5，段前段后约 2pt）
            case "chiefComplaint",
                    "presentIllness",
                    "pastHistory",
                    "allergyHistory",
                    "familyHistory",
                    "personalHistory",
                    "marriageChildHistory",
                    "menstrualHistory",
                    "tcmFourExams",
                    "physicalExam",
                    "vitals",
                    "generalCondition",
                    "skinMucosa",
                    "lymphNodes",
                    "headSensory",
                    "neckExam",
                    "chestExam",
                    "heartExam",
                    "heartBorderTitle",
                    "midclavicularNote",
                    "peripheralVessel",
                    "abdomenExam",
                    "genitaliaAnus",
                    "spineExam",
                    "limbsJoints",
                    "nervousSystem",
                    "auxiliaryExam",
                    "impressionDiagnosis",
                    "westernDiagnosis",
                    "tcmPreliminaryDiagnosis",
                    "westernPreliminaryDiagnosis",
                    "admissionDiagnosisStructured",
                    "disposal",
                    "diagnosis" -> {
                p.setSpacingBefore(2f);
                p.setSpacingAfter(2f);
                p.setLeading(baseFont.getSize() * printConfig.getFont().getLineHeight());
            }
            case "historyTitle", "peTitle", "diagnosisTitle" -> {
                p.setSpacingBefore(6f);
                p.setSpacingAfter(4f);
                p.setLeading(baseFont.getSize() * printConfig.getFont().getLineHeight());
            }
            case "pageHeaderHint" -> {
                p.setSpacingBefore(2f);
                p.setSpacingAfter(6f);
                p.setLeading(baseFont.getSize() * printConfig.getFont().getLineHeight());
            }
            case "noticeIntro" -> {
                p.setFirstLineIndent(baseFont.getSize() * 2f);
                p.setSpacingBefore(4f);
                p.setSpacingAfter(6f);
                p.setLeading(baseFont.getSize() * printConfig.getFont().getLineHeight());
            }
            case "noticeItem" -> {
                p.setSpacingBefore(2f);
                p.setSpacingAfter(2f);
                p.setLeading(baseFont.getSize() * printConfig.getFont().getLineHeight());
            }
            case "noticeConfirm" -> {
                p.setFirstLineIndent(baseFont.getSize() * 2f);
                p.setSpacingBefore(6f);
                p.setSpacingAfter(8f);
                p.setLeading(baseFont.getSize() * printConfig.getFont().getLineHeight());
            }
            case "riskPatientInfo", "riskGuardianSign" -> {
                p.setSpacingBefore(4f);
                p.setSpacingAfter(4f);
                p.setLeading(baseFont.getSize() * printConfig.getFont().getLineHeight());
            }
            case "riskSectionTitle" -> {
                p.setSpacingBefore(6f);
                p.setSpacingAfter(4f);
            }
            default -> {
            }
        }
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
            } else if ("image".equals(type)) {
                Chunk imgChunk = buildInlineImageChunk(inline);
                if (imgChunk != null) {
                    p.add(imgChunk);
                }
            } else if ("emrcheckboxfield".equals(type)) {
                appendCheckboxChunk(p, inline, baseFont);
            } else if (isInlineFieldType(type)) {
                String v = extractDataElementDisplay(inline);
                if (!StringUtils.hasText(v)) {
                    String widget = inline.path(ATTRS).path("widgetOverride").asText("").trim();
                    // 单行空值用下划线占位（贴近纸质）；多行段落留白
                    v = "multiline".equals(widget) ? "" : "______";
                }
                p.add(new Chunk(v, baseFont));
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

    /** 段落内院徽等 inline image → Chunk；院徽按标题字号缩放，避免 attrs 像素当 pt */
    private Chunk buildInlineImageChunk(JsonNode imageNode) {
        String src = attrText(imageNode, "src");
        if (!StringUtils.hasText(src)) {
            return null;
        }
        try {
            com.lowagie.text.Image img = loadPdfImage(src.trim());
            if (img == null) {
                return null;
            }
            String title = attrText(imageNode, "title");
            if ("hospitalLogo".equals(title)) {
                float fit = hospitalLogoFitPt(titleFont != null ? titleFont : bodyFont);
                img.scaleToFit(fit, fit);
                float fontSize = titleFont != null ? titleFont.getSize() : bodyFont.getSize();
                float offsetY = (fontSize - img.getScaledHeight()) / 2f - fontSize * 0.12f;
                return new Chunk(img, 0, offsetY, true);
            }
            float w = (float) imageNode.path(ATTRS).path("width").asDouble(0);
            float h = (float) imageNode.path(ATTRS).path("height").asDouble(0);
            // 屏像素 attrs 常为 30~80；当 pt 会过大。超过 28 时按 contain 缩到 28pt
            if (w > 0 && h > 0 && w <= 28f && h <= 28f) {
                img.scaleAbsolute(w, h);
            } else if (w > 0 && h > 0) {
                img.scaleToFit(Math.min(w, 28f), Math.min(h, 28f));
            } else {
                img.scaleToFit(28f, 28f);
            }
            return new Chunk(img, 0, -4f, true);
        } catch (Exception e) {
            return null;
        }
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
        float sizePt = resolveMarkFontSizePt(textNode, baseFont.getSize());
        Font f = fontFactory.bodyFont(sizePt, style);
        p.add(new Chunk(text, f));
    }

    private void renderList(JsonNode node, String type) throws Exception {
        if (!node.has(CONTENT) || !node.get(CONTENT).isArray()) {
            return;
        }
        int index = 1;
        for (JsonNode item : node.get(CONTENT)) {
            if (!"listitem".equals(normalizeType(item.path(TYPE).asText(null)))) {
                continue;
            }
            JsonNode itemContent = item.get(CONTENT);
            if (itemContent == null || !itemContent.isArray()) {
                continue;
            }
            for (JsonNode block : itemContent) {
                String prefix;
                if ("orderedlist".equals(type)) {
                    prefix = index + ". ";
                } else {
                    prefix = "• ";
                }
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
            // normalizeType("tableRow") → "tablerow"；须用归一化名比较
            if (!"tablerow".equals(normalizeType(row.path(TYPE).asText(null)))) {
                continue;
            }
            rows.add(row);
            colCount = Math.max(colCount, countRowColumns(row));
        }
        if (rows.isEmpty() || colCount <= 0) {
            return;
        }
        PdfPTable table = new PdfPTable(colCount);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(4f);
        table.setSpacingAfter(4f);
        table.setSplitLate(false);
        applyDischargeFormColumnWidths(table, colCount, node);
        applyAdmissionSafetySignTableLayout(table, colCount, node);
        String tableName = attrText(node, "name");
        boolean dischargeForm = "dischargeDiagnosisForm".equals(tableName)
                || "consultRequestForm".equals(tableName);
        boolean safetySign = "admissionSafetySign".equals(tableName);
        boolean patientHeader = "patientHeader".equals(tableName);
        boolean pageHeader = "pageHeader".equals(tableName);
        boolean verifyBlock = "verifyBlock".equals(tableName);
        boolean signBlock = "signBlock".equals(tableName);
        boolean borderlessEmrTable = patientHeader || pageHeader || verifyBlock || signBlock;
        if (borderlessEmrTable) {
            table.setSpacingBefore(2f);
            table.setSpacingAfter(2f);
        }
        int rowIndex = 0;
        for (JsonNode row : rows) {
            int writtenCols = 0;
            if (row.has(CONTENT) && row.get(CONTENT).isArray()) {
                for (JsonNode cell : row.get(CONTENT)) {
                    String cellType = normalizeType(cell.path(TYPE).asText(null));
                    if (!"tablecell".equals(cellType) && !"tableheader".equals(cellType)) {
                        continue;
                    }
                    int colspan = Math.max(1, cell.path(ATTRS).path("colspan").asInt(1));
                    int rowspan = Math.max(1, cell.path(ATTRS).path("rowspan").asInt(1));
                    // 防止 colspan 超出剩余列导致 OpenPDF 抛错
                    int remain = colCount - writtenCols;
                    if (remain <= 0) {
                        break;
                    }
                    if (colspan > remain) {
                        colspan = remain;
                    }
                    Phrase phrase = buildCellPhrase(cell);
                    PdfPCell pdfCell = new PdfPCell(phrase);
                    pdfCell.setPadding(5f);
                    pdfCell.setColspan(colspan);
                    if (rowspan > 1) {
                        pdfCell.setRowspan(rowspan);
                    }
                    applyNamedCellLayout(pdfCell, cell);
                    // 首行姓名/性别/年龄/住院号：标签与值居中
                    if (dischargeForm && rowIndex == 0) {
                        pdfCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        pdfCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    }
                    if (safetySign) {
                        pdfCell.setBorder(PdfPCell.NO_BORDER);
                        pdfCell.setPaddingTop(8f);
                        pdfCell.setPaddingBottom(4f);
                        String cellName = attrText(cell, "name");
                        if ("noticeSignRight".equals(cellName)) {
                            pdfCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        } else {
                            pdfCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                        }
                    }
                    // 入院/门诊页眉与签字：无框线（心浊音界等有框表不走此分支）
                    if (borderlessEmrTable) {
                        pdfCell.setBorder(PdfPCell.NO_BORDER);
                        pdfCell.setPaddingTop(2f);
                        pdfCell.setPaddingBottom(2f);
                        pdfCell.setPaddingLeft(2f);
                        pdfCell.setPaddingRight(2f);
                        String cellName = attrText(cell, "name");
                        if (signBlock && ("signRight".equals(cellName) || "signSpacer".equals(cellName))) {
                            if ("signRight".equals(cellName)) {
                                pdfCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                            } else {
                                pdfCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                            }
                        } else if (pageHeader && "pageHeaderRight".equals(cellName)) {
                            pdfCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        } else if (pageHeader && "pageHeaderLeft".equals(cellName)) {
                            pdfCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                        } else if (verifyBlock && "signRight".equals(cellName)) {
                            pdfCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        } else if (patientHeader || pageHeader || verifyBlock) {
                            pdfCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                        }
                    }
                    if ("tableheader".equals(cellType)) {
                        pdfCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    }
                    table.addCell(pdfCell);
                    writtenCols += colspan;
                }
            }
            while (writtenCols < colCount) {
                PdfPCell empty = new PdfPCell(new Phrase("", bodyFont));
                if (safetySign || borderlessEmrTable) {
                    empty.setBorder(PdfPCell.NO_BORDER);
                }
                table.addCell(empty);
                writtenCols++;
            }
            rowIndex++;
        }
        document.add(table);
    }

    /**
     * 出院诊断书等表单：首列略窄（竖排标签），其余均分。
     * 通过 table attrs.name=dischargeDiagnosisForm 识别。
     */
    private static void applyDischargeFormColumnWidths(PdfPTable table, int colCount, JsonNode tableNode) {
        if (colCount <= 0) {
            return;
        }
        String tableName = attrText(tableNode, "name");
        if ((!"dischargeDiagnosisForm".equals(tableName) && !"consultRequestForm".equals(tableName))
                || colCount < 2) {
            return;
        }
        try {
            if (colCount == 8) {
                // 首列加宽，避免「申请会诊目的」等长标签被裁切
                table.setWidths(new float[]{2.05f, 1.95f, 1.1f, 1.05f, 1.1f, 1.0f, 1.35f, 2.2f});
            } else {
                float[] widths = new float[colCount];
                widths[0] = 1.15f;
                float rest = (colCount - 1) > 0 ? (12.85f / (colCount - 1)) : 1f;
                for (int i = 1; i < colCount; i++) {
                    widths[i] = rest;
                }
                table.setWidths(widths);
            }
        } catch (Exception ignored) {
            // 列宽失败不影响正文
        }
    }

    /** 入院安全告知书签字行：无框线左右分栏（签字 | 日期） */
    private static void applyAdmissionSafetySignTableLayout(PdfPTable table, int colCount, JsonNode tableNode) {
        if (colCount < 2) {
            return;
        }
        if (!"admissionSafetySign".equals(attrText(tableNode, "name"))) {
            return;
        }
        try {
            table.setWidths(new float[]{6.2f, 3.8f});
            table.setSpacingBefore(2f);
            table.setSpacingAfter(2f);
        } catch (Exception ignored) {
            // 列宽失败不影响正文
        }
    }

    /** 按单元格 data-name 控制行高/对齐，贴近纸质诊断书 */
    private void applyNamedCellLayout(PdfPCell pdfCell, JsonNode cell) {
        String name = attrText(cell, "name");
        pdfCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (!StringUtils.hasText(name)) {
            return;
        }
        switch (name) {
            case "verticalLabel" -> {
                pdfCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                pdfCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                pdfCell.setPaddingTop(3f);
                pdfCell.setPaddingBottom(3f);
                pdfCell.setMinimumHeight(mmToPt(42f));
            }
            case "formLabel", "formValue" -> {
                pdfCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                pdfCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            }
            case "midBody" -> {
                pdfCell.setVerticalAlignment(Element.ALIGN_TOP);
                pdfCell.setMinimumHeight(mmToPt(12f));
                pdfCell.setPaddingTop(3f);
            }
            case "tallBody" -> {
                pdfCell.setVerticalAlignment(Element.ALIGN_TOP);
                // 压缩到约一页：摘要/指导各约 42mm（此前 62mm 易溢出第二页）
                pdfCell.setMinimumHeight(mmToPt(42f));
                pdfCell.setPaddingTop(3f);
            }
            case "signBlock" -> {
                pdfCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                pdfCell.setPaddingTop(6f);
                pdfCell.setPaddingBottom(6f);
                pdfCell.setMinimumHeight(mmToPt(16f));
            }
            default -> {
            }
        }
    }

    private static float mmToPt(float mm) {
        return mm * 72f / 25.4f;
    }

    /** 按 colspan 累计一行占用的列数 */
    private static int countRowColumns(JsonNode row) {
        if (row == null || !row.has(CONTENT) || !row.get(CONTENT).isArray()) {
            return 0;
        }
        int cols = 0;
        for (JsonNode cell : row.get(CONTENT)) {
            String cellType = normalizeType(cell.path(TYPE).asText(null));
            if (!"tablecell".equals(cellType) && !"tableheader".equals(cellType)) {
                continue;
            }
            cols += Math.max(1, cell.path(ATTRS).path("colspan").asInt(1));
        }
        return cols;
    }

    /** 单元格 Phrase：支持纯文本与行内数据元/元数据 chip */
    private Phrase buildCellPhrase(JsonNode cell) {
        Phrase phrase = new Phrase();
        phrase.setLeading(bodyFont.getSize() * printConfig.getFont().getLineHeight());
        if (cell == null || !cell.has(CONTENT) || !cell.get(CONTENT).isArray()) {
            phrase.add(new Chunk("", bodyFont));
            return phrase;
        }
        boolean firstBlock = true;
        for (JsonNode block : cell.get(CONTENT)) {
            if (!firstBlock) {
                phrase.add(new Chunk("\n", bodyFont));
            }
            firstBlock = false;
            appendBlockTextToPhrase(phrase, block);
        }
        if (phrase.isEmpty()) {
            phrase.add(new Chunk("", bodyFont));
        }
        return phrase;
    }

    private void appendBlockTextToPhrase(Phrase phrase, JsonNode block) {
        if (block == null || !block.isObject()) {
            return;
        }
        String type = normalizeType(block.path(TYPE).asText(null));
        if ("emrcheckboxfield".equals(type)) {
            appendCheckboxChunk(phrase, block, bodyFont);
            return;
        }
        if (isInlineFieldType(type)) {
            String v = extractDataElementDisplay(block);
            if (StringUtils.hasText(v)) {
                phrase.add(new Chunk(v, bodyFont));
            }
            return;
        }
        if (!block.has(CONTENT) || !block.get(CONTENT).isArray()) {
            return;
        }
        for (JsonNode inline : block.get(CONTENT)) {
            if (!inline.isObject()) {
                continue;
            }
            String inlineType = normalizeType(inline.path(TYPE).asText(null));
            if (TYPE_TEXT.equals(inlineType)) {
                appendTextNodeToPhrase(phrase, inline);
            } else if ("image".equals(inlineType)) {
                Chunk imgChunk = buildInlineImageChunk(inline);
                if (imgChunk != null) {
                    phrase.add(imgChunk);
                }
            } else if ("emrcheckboxfield".equals(inlineType)) {
                appendCheckboxChunk(phrase, inline, bodyFont);
            } else if (isInlineFieldType(inlineType)) {
                String v = extractDataElementDisplay(inline);
                if (!StringUtils.hasText(v)) {
                    String widget = inline.path(ATTRS).path("widgetOverride").asText("").trim();
                    v = "multiline".equals(widget) ? "" : "______";
                }
                if (StringUtils.hasText(v)) {
                    phrase.add(new Chunk(v, bodyFont));
                }
            } else if (inline.has(CONTENT) && inline.get(CONTENT).isArray()) {
                for (JsonNode nested : inline.get(CONTENT)) {
                    if (TYPE_TEXT.equals(normalizeType(nested.path(TYPE).asText(null)))) {
                        appendTextNodeToPhrase(phrase, nested);
                    }
                }
            }
        }
    }

    private void appendTextNodeToPhrase(Phrase phrase, JsonNode textNode) {
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
        float sizePt = resolveMarkFontSizePt(textNode, bodyFont.getSize());
        Font f = fontFactory.bodyFont(sizePt, style);
        phrase.add(new Chunk(text, f));
    }

    /** 数据元展示值；签名图等 data URL 不进表格文本 */
    private String extractDataElementDisplay(JsonNode node) {
        String value = extractDataElementValue(node);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("data:image/")) {
            return "";
        }
        return trimmed;
    }

    private void renderHorizontalRule(JsonNode node) throws Exception {
        // 实线横线（对齐 Word：页眉下粗线 / 签字前细线）；须启用 TOP 边框，NO_BORDER 不会画线
        float width = 1f;
        if (node != null && node.path(ATTRS).has("lineWidth") && !node.path(ATTRS).get("lineWidth").isNull()) {
            float raw = (float) node.path(ATTRS).path("lineWidth").asDouble(1d);
            if (raw >= 1f && raw <= 24f) {
                width = raw;
            }
        }
        java.awt.Color color = java.awt.Color.BLACK;
        String colorText = node != null ? attrText(node, "lineColor") : null;
        if (StringUtils.hasText(colorText) && colorText.startsWith("#") && colorText.length() >= 7) {
            try {
                color = java.awt.Color.decode(colorText.substring(0, 7));
            } catch (NumberFormatException ignored) {
                // keep black
            }
        }
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100f);
        line.setSpacingBefore(4f);
        line.setSpacingAfter(6f);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.TOP);
        cell.setBorderWidthTop(width);
        cell.setBorderColorTop(color);
        cell.setFixedHeight(Math.max(2f, width + 1f));
        cell.setPadding(0f);
        line.addCell(cell);
        document.add(line);
    }

    private void newPage() throws Exception {
        if (checkPreviewPageLimit()) {
            return;
        }
        document.newPage();
        pagesRendered = writer.getPageNumber();
    }

    private boolean isCustomPrintFooterEnabled() {
        if (printConfig == null || printConfig.getFooter() == null || !printConfig.getFooter().isEnabled()) {
            return false;
        }
        String template = printConfig.getFooter().getTemplate();
        if (template == null || template.isBlank()) {
            return false;
        }
        // 仅页码的默认页脚不算「自定义戒烟页脚」
        String t = template.trim();
        return !("第 {page} 页".equals(t) || "{page}".equals(t));
    }

    private boolean checkPreviewPageLimit() {
        if (!renderOptions.isPreviewMode()) {
            return false;
        }
        int max = 20;
        if (renderOptions.getPreviewMaxPages() > 0) {
            max = renderOptions.getPreviewMaxPages();
        }
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

    /**
     * 数据元取值：优先节点 attrs.value；空时回退请求 metadata（按 elementId / fieldKey）。
     * 模板打印预览无就诊上下文时，前端会传 orgName 等，须在此生效。
     */
    private String extractDataElementValue(JsonNode node) {
        JsonNode attrs = node.get(ATTRS);
        if (attrs == null) {
            return "";
        }
        String fromAttrs = "";
        if (attrs.has("value") && !attrs.get("value").isNull()) {
            JsonNode v = attrs.get("value");
            fromAttrs = v.isTextual() ? v.asText() : v.toString();
        }
        if (StringUtils.hasText(fromAttrs)) {
            return fromAttrs;
        }
        String elementId = attrs.path("elementId").asText("");
        if (!StringUtils.hasText(elementId)) {
            elementId = attrs.path("fieldKey").asText("");
        }
        if (!StringUtils.hasText(elementId) || renderOptions == null) {
            return "";
        }
        var meta = renderOptions.safeMetadata();
        String fromMeta = meta.get(elementId);
        if (StringUtils.hasText(fromMeta)) {
            return fromMeta;
        }
        // 机构名别名：模板预览 / 页脚等可能传 hospitalName
        if ("orgName".equals(elementId)) {
            return firstNonBlank(meta.get("hospitalName"), meta.get("organizationName"), "");
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

    /**
     * 回放块级版式 attrs（与前端 emrBlockLayout 一致）：
     * lineHeight / textIndent / paragraphMarginTop|Bottom。
     */
    private void applyBlockLayoutAttrs(JsonNode node, Paragraph p, Font baseFont) {
        JsonNode attrs = node.get(ATTRS);
        if (attrs == null) {
            return;
        }
        float fontSize = baseFont.getSize();
        if (attrs.has("lineHeight") && !attrs.get("lineHeight").isNull()) {
            Float leading = resolveLineHeightLeading(attrs.get("lineHeight").asText(""), fontSize);
            if (leading != null) {
                p.setLeading(leading);
            }
        }
        if (attrs.has("textIndent") && !attrs.get("textIndent").isNull()) {
            Float indent = PdfUnitUtils.parseCssLengthToPt(attrs.get("textIndent").asText(""), fontSize);
            if (indent != null) {
                p.setFirstLineIndent(indent);
            }
        }
        if (attrs.has("paragraphMarginTop") && !attrs.get("paragraphMarginTop").isNull()) {
            Float top = PdfUnitUtils.parseCssLengthToPt(attrs.get("paragraphMarginTop").asText(""), fontSize);
            if (top != null) {
                p.setSpacingBefore(top);
            }
        }
        if (attrs.has("paragraphMarginBottom") && !attrs.get("paragraphMarginBottom").isNull()) {
            Float bottom = PdfUnitUtils.parseCssLengthToPt(attrs.get("paragraphMarginBottom").asText(""), fontSize);
            if (bottom != null) {
                p.setSpacingAfter(bottom);
            }
        }
    }

    /** 无单位倍数 → fontSize×倍数；带单位 → 绝对 pt 行高 */
    private static Float resolveLineHeightLeading(String raw, float fontSizePt) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        if (s.matches("^[0-9]*\\.?[0-9]+$")) {
            try {
                return fontSizePt * Float.parseFloat(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return PdfUnitUtils.parseCssLengthToPt(s, fontSizePt);
    }

    /** textStyle.fontSize（如 12pt）→ pt；无则回落默认字号 */
    private static float resolveMarkFontSizePt(JsonNode textNode, float defaultSizePt) {
        JsonNode textStyle = findMarkAttrs(textNode, "textstyle");
        if (textStyle == null || !textStyle.has("fontSize") || textStyle.get("fontSize").isNull()) {
            return defaultSizePt;
        }
        Float pt = PdfUnitUtils.parseCssLengthToPt(textStyle.get("fontSize").asText(""), defaultSizePt);
        return pt != null && pt > 0f ? pt : defaultSizePt;
    }

    private static JsonNode findMarkAttrs(JsonNode textNode, String markTypeNormalized) {
        JsonNode marks = textNode.get(MARKS);
        if (marks == null || !marks.isArray()) {
            return null;
        }
        for (JsonNode mark : marks) {
            if (markTypeNormalized.equals(normalizeType(mark.path(TYPE).asText(null)))) {
                return mark.get(ATTRS);
            }
        }
        return null;
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

    private void appendCheckboxChunk(Phrase phrase, JsonNode node, Font font) {
        boolean checked = node.path(ATTRS).path("checked").asBoolean(false)
                || "true".equalsIgnoreCase(node.path(ATTRS).path("checked").asText(""));
        String label = attrText(node, "label");
        String mark = checked ? "☑" : "□";
        String text = StringUtils.hasText(label) ? mark + label : mark;
        phrase.add(new Chunk(text + "　", font));
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
}
