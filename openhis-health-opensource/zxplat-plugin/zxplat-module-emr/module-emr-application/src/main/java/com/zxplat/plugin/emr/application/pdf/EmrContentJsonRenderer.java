/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.zxplat.framework.core.application.util.FileUploadUtils;
import com.zxplat.plugin.emr.application.pdf.config.PrintConfig;
import com.zxplat.plugin.emr.application.pdf.model.PdfRenderOptions;
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

import static com.zxplat.framework.core.application.util.StringUtils.firstNonBlank;
import static com.zxplat.framework.core.application.util.StringUtils.nullToEmpty;

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
    /**
     * 中文宋体 Word 单倍行距相对字号的系数。
     * 工具栏 1.5 倍 × 1.55 ≈ 2.33，小四约 28pt；直接用 fontSize×1.5 会看成单倍。
     */
    private static final float CJK_SINGLE_SPACING_FACTOR = 1.55f;
    /**
     * 无 fillRemainShare 的行预留高度。一行 12pt×2.33 加格 padding，估矮会把末行踢到第 2 页。
     */
    private static final float FILL_REMAIN_NON_WRITE_ROW_MM = 16f;
    /** fillRemain 垫高后相对页底再留的空隙，抵 setSplitLate 差 1pt 就整行翻页。 */
    private static final float FILL_REMAIN_PAGE_SLACK_MM = 8f;

    /**
     * 签字位常见角色 label（无「签名」二字，但落款处用于手写姓名）。
     * 「接诊医生」仅在「医生签字/医师签名」等上下文后才清空，见 {@link #isPersonSignNameField}。
     */
    private static final Set<String> SIGN_ROLE_NAME_LABELS = Set.of(
            "上级医师",
            "主治医师",
            "住院医师",
            "会诊医师",
            "申请医师",
            "邀请医师",
            "报告医师",
            "经治医师",
            "科主任");

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
            document.add(printParagraph("（空文档）", bodyFont));
            return false;
        }
        JsonNode content = root.get(CONTENT);
        if (content == null || !content.isArray() || content.isEmpty()) {
            document.add(printParagraph("（空文档）", bodyFont));
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
        for (int i = 0; i < blocks.size(); i++) {
            if (checkPreviewPageLimit()) {
                return;
            }
            JsonNode block = blocks.get(i);
            // 入院基础信息 admPh*：编辑器段落两列；打印时左右成对合成无框两列表
            if (isAdmPhParagraph(block) && i + 1 < blocks.size() && isAdmPhParagraph(blocks.get(i + 1))) {
                renderAdmPhPairAsRow(block, blocks.get(i + 1));
                i += 1;
                continue;
            }
            renderNode(block);
        }
    }

    /**
     * 是否入院基础信息段落（admPh*，编辑器 inline-block 两列）。
     */
    private boolean isAdmPhParagraph(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if (!"paragraph".equals(normalizeType(node.path(TYPE).asText(null)))) {
            return false;
        }
        String name = attrText(node, "name");
        return name != null && name.startsWith("admPh");
    }

    /**
     * 将左右两个 admPh 段落打成一行无框两列表，保持与编辑器两列对齐。
     */
    private void renderAdmPhPairAsRow(JsonNode left, JsonNode right) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setWidths(new float[]{1f, 1f});
        table.setSpacingBefore(1f);
        table.setSpacingAfter(1f);
        table.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        table.getDefaultCell().setPadding(1f);
        table.getDefaultCell().setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(buildAdmPhCell(left));
        table.addCell(buildAdmPhCell(right));
        document.add(table);
    }

    private PdfPCell buildAdmPhCell(JsonNode para) {
        Phrase phrase = new Phrase();
        phrase.setLeading(bodyLeadingPt(bodyFont));
        appendBlockTextToPhrase(phrase, para);
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(1f);
        cell.setPaddingLeft(0f);
        cell.setPaddingRight(6f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setLeading(bodyLeadingPt(bodyFont), 0f);
        return cell;
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
            Paragraph p = printParagraph(title, titleFont);
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
            Paragraph p = printParagraph(title, sectionFont);
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
            p.setLeading(bodyLeadingPt(bodyFont));
            appendCheckboxChunk(p, node, bodyFont);
            document.add(p);
            return;
        }
        String label = firstNonBlank(attrText(node, "labelSnapshot"), attrText(node, "title"), attrText(node, "name"));
        String value = extractDataElementValue(node);
        // 签字位块级数据元：只打标签+下划线，不打姓名
        if (shouldBlankSignatureSlot(node, false)
                || (StringUtils.hasText(label) && textHasSignCue(label))) {
            value = "______";
        }
        String line;
        if (StringUtils.hasText(label)) {
            line = label + "：" + nullToEmpty(value);
        } else {
            line = nullToEmpty(value);
        }
        if (!StringUtils.hasText(line)) {
            return;
        }
        Paragraph p = printParagraph(line, bodyFont);
        p.setLeading(bodyLeadingPt(bodyFont));
        document.add(p);
    }

    private void renderMetadataChipParagraph(JsonNode node) throws Exception {
        // 签字位：预览/打印留白手写，不打姓名或签名图（块级 chip 仅按 label/fieldKey 判断）
        if (shouldBlankSignatureSlot(node, false)) {
            Paragraph blank = printParagraph("______", bodyFont);
            blank.setLeading(bodyLeadingPt(bodyFont));
            document.add(blank);
            return;
        }
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
                document.add(printParagraph(" ", bodyFont));
            }
            return;
        }
        Paragraph p = printParagraph(trimmed, bodyFont);
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
            Paragraph p = printParagraph(text, bodyFont);
            document.add(p);
        }
    }

    private void renderFreeText(JsonNode node) throws Exception {
        if (node.has(CONTENT) && node.get(CONTENT).isArray() && !node.get(CONTENT).isEmpty()) {
            Paragraph p = buildParagraphFromInline(node.get(CONTENT), bodyFont);
            if (p != null) {
                document.add(p);
            }
            return;
        }
        // 签字位 atom：预览/打印留白手写（按 label/fieldKey）
        if (shouldBlankSignatureSlot(node, false)) {
            Paragraph blank = printParagraph("______", bodyFont);
            blank.setLeading(bodyLeadingPt(bodyFont));
            document.add(blank);
            return;
        }
        // 方案 B atom：值在 attrs.value，无 content
        String value = extractDataElementDisplay(node);
        if (value == null || value.isEmpty()) {
            return;
        }
        Paragraph p = new Paragraph();
        p.setLeading(bodyLeadingPt(bodyFont));
        appendFieldDisplayValue(p, value, bodyFont);
        document.add(p);
    }

    private void renderHeading(JsonNode node) throws Exception {
        int level = node.path(ATTRS).path("level").asInt(1);
        float size = printConfig.getFont().getTitleSizePt() - Math.max(0, level - 1) * 2f;
        Font font = fontFactory.bodyFont(Math.max(size, bodyFont.getSize()), Font.BOLD);
        renderParagraph(node, font, Element.ALIGN_LEFT);
    }

    private void renderParagraph(JsonNode node, Font baseFont, int alignment) throws Exception {
        // 匿名空段仍占 leading，会多挤出一页半空白；屏上同样不把文末空段撑成新纸
        if (!StringUtils.hasText(attrText(node, "name")) && !hasPrintableInlineContent(node)) {
            return;
        }
        boolean signPara = isSignParagraphName(attrText(node, "name"));
        Paragraph p;
        if (node.has(CONTENT)) {
            p = buildParagraphFromInline(node.get(CONTENT), baseFont, signPara);
        } else {
            p = new Paragraph("", baseFont);
        }
        if (p == null) {
            p = new Paragraph("", baseFont);
        }
        p.setAlignment(resolveTextAlign(node, alignment));
        p.setLeading(bodyLeadingPt(baseFont));
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

    /**
     * 组装页眉「院徽 + 医院名」Phrase；医院名走混排字体。
     *
     * @param inlineNodes hospitalTitle 段 content
     * @param titleFont   医院名字号（约 22pt 加粗）
     * @return 可居中输出的 Phrase
     */
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
                    addPrintText(phrase, text, titleFont);
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
                    addPrintText(phrase, v, titleFont);
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
                    "admissionDiagnosisStructured",
                    "disposal",
                    "diagnosis" -> {
                p.setSpacingBefore(2f);
                p.setSpacingAfter(2f);
                p.setLeading(bodyLeadingPt(baseFont));
            }
            // 中/西医初步诊断：续行与首行诊断值左缘对齐（悬挂缩进），不用空格从页边垫位
            case "tcmPreliminaryDiagnosis", "westernPreliminaryDiagnosis" -> {
                p.setSpacingBefore(2f);
                p.setSpacingAfter(2f);
                p.setLeading(bodyLeadingPt(baseFont));
                applyDiagnosisHangingIndent(node, p, baseFont);
            }
            case "historyTitle", "peTitle", "diagnosisTitle" -> {
                p.setSpacingBefore(6f);
                p.setSpacingAfter(4f);
                p.setLeading(bodyLeadingPt(baseFont));
            }
            // 入院记录：表头/核实/签名已改为正文段，与编辑器 p[data-name] 同行距、无框线
            case "patientHeader", "verifyBlock" -> {
                p.setSpacingBefore(1f);
                p.setSpacingAfter(1f);
                p.setLeading(bodyLeadingPt(baseFont));
            }
            case "signBlock" -> {
                p.setAlignment(Element.ALIGN_RIGHT);
                p.setSpacingBefore(2f);
                p.setSpacingAfter(2f);
                p.setLeading(bodyLeadingPt(baseFont));
            }
            case "pageHeaderHint" -> {
                p.setSpacingBefore(2f);
                p.setSpacingAfter(6f);
                p.setLeading(bodyLeadingPt(baseFont));
            }
            case "noticeIntro" -> {
                p.setFirstLineIndent(baseFont.getSize() * 2f);
                p.setSpacingBefore(4f);
                p.setSpacingAfter(6f);
                p.setLeading(bodyLeadingPt(baseFont));
            }
            case "noticeItem" -> {
                p.setSpacingBefore(2f);
                p.setSpacingAfter(2f);
                p.setLeading(bodyLeadingPt(baseFont));
            }
            case "noticeConfirm" -> {
                p.setFirstLineIndent(baseFont.getSize() * 2f);
                p.setSpacingBefore(6f);
                p.setSpacingAfter(8f);
                p.setLeading(bodyLeadingPt(baseFont));
            }
            case "riskPatientInfo", "riskGuardianSign" -> {
                p.setSpacingBefore(4f);
                p.setSpacingAfter(4f);
                p.setLeading(bodyLeadingPt(baseFont));
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
        return buildParagraphFromInline(inlineNodes, baseFont, false);
    }

    /**
     * 组装段落；{@code afterSignCue} 为真时，其后姓名类 chip/自由文本打印留白（手写签字）。
     *
     * @param inlineNodes 行内节点
     * @param baseFont    正文字体
     * @param afterSignCue 是否已处于签字上下文（命名签名段或上文含签名/签字）
     * @return 段落
     */
    private Paragraph buildParagraphFromInline(JsonNode inlineNodes, Font baseFont, boolean afterSignCue) {
        if (inlineNodes == null || !inlineNodes.isArray()) {
            return new Paragraph("", baseFont);
        }
        Paragraph p = new Paragraph();
        p.setLeading(bodyLeadingPt(baseFont));
        boolean cue = afterSignCue;
        for (JsonNode inline : inlineNodes) {
            if (!inline.isObject()) {
                continue;
            }
            String type = normalizeType(inline.path(TYPE).asText(null));
            if (TYPE_TEXT.equals(type)) {
                String raw = inline.path("text").asText("");
                appendTextNode(p, inline, baseFont);
                if (textHasSignCue(raw)) {
                    cue = true;
                }
            } else if ("image".equals(type)) {
                Chunk imgChunk = buildInlineImageChunk(inline);
                if (imgChunk != null) {
                    p.add(imgChunk);
                }
            } else if ("emrcheckboxfield".equals(type)) {
                appendCheckboxChunk(p, inline, baseFont);
            } else if (isInlineFieldType(type)) {
                appendInlineFieldOrBlank(p, inline, baseFont, cue);
            } else if (inline.has(CONTENT)) {
                for (JsonNode nested : inline.get(CONTENT)) {
                    if (TYPE_TEXT.equals(nested.path(TYPE).asText(null))) {
                        appendTextNode(p, nested, baseFont);
                        if (textHasSignCue(nested.path("text").asText(""))) {
                            cue = true;
                        }
                    }
                }
            }
        }
        return p;
    }

    /**
     * 行内可填域：签字位打印下划线占位，其它照常输出值。
     *
     * @param phrase       目标段落/单元格
     * @param inline       chip / freeText / dataElement
     * @param baseFont     字体
     * @param afterSignCue 签字上下文
     */
    private void appendInlineFieldOrBlank(Phrase phrase, JsonNode inline, Font baseFont, boolean afterSignCue) {
        if (shouldBlankSignatureSlot(inline, afterSignCue)) {
            String widget = inline.path(ATTRS).path("widgetOverride").asText("").trim();
            if ("multiline".equals(widget)) {
                return;
            }
            addPrintText(phrase, "______", baseFont);
            return;
        }
        String v = extractDataElementDisplay(inline);
        if (v == null || v.isEmpty()) {
            String widget = inline.path(ATTRS).path("widgetOverride").asText("").trim();
            if ("multiline".equals(widget)) {
                return;
            }
            // 空自由文本不打下划线。签字位已在上面留手写线，不走这里。
            if ("emrfreetext".equals(normalizeType(inline.path(TYPE).asText(null)))) {
                return;
            }
            addPrintText(phrase, "______", baseFont);
            return;
        }
        appendFieldDisplayValue(phrase, v, baseFont);
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

    /**
     * 把 text 节点（含加粗/下划线 mark）写入段落；英文走混排字体避免全角宽。
     *
     * @param p        目标段落
     * @param textNode ProseMirror text 节点
     * @param baseFont 回退字号
     */
    private void appendTextNode(Paragraph p, JsonNode textNode, Font baseFont) {
        String text = textNode.path("text").asText("");
        // 空槽 placeholder 不进 PDF（B17）；空串同样跳过
        if (text.isEmpty() || isUnfilledPlaceholderText(text)) {
            return;
        }
        if (!renderOptions.isIncludeTrackChanges() && hasMark(textNode, "deletion")) {
            return;
        }
        // 同一 text 节点内「签名：张三」去掉姓名，留标签手写
        text = stripInlineSignNames(text);
        if (text.isEmpty()) {
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
        addPrintText(p, text, f);
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
                fontFactory.prependText(p, prefix, bodyFont);
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
        document.add(printParagraph(" ", bodyFont));
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
        EmrTableLayoutSupport.TableLayout tableLayout = EmrTableLayoutSupport.parseTable(node, colCount);
        // 有 colWidths 按协议画；缺省按角色/均分，不再按表名写死毫米
        boolean usedProtocolWidths = EmrTableLayoutSupport.applyColumnWidths(table, tableLayout);
        if (!usedProtocolWidths) {
            applyRoleDefaultColumnWidths(table, colCount, node);
        }
        FillRemainRowHeights fillRemainHeights = null;
        // 长书写表整行切页，避免记录从格子中间横切把落款孤到下一页
        if (EmrTableLayoutSupport.shouldSplitLate(tableLayout) || tableHasNamedWriteArea(node)) {
            table.setSplitLate(true);
            table.setSpacingBefore(2f);
            table.setSpacingAfter(0f);
        }
        if (tableLayout.hasFillRemain()) {
            fillRemainHeights = measureFillRemainRowHeights(node, rows, colCount);
        }
        // 无框：attrs.border=false，或 patientHeader 等角色没写 border（09 §4 缺省无框）
        String tableName = attrText(node, "name");
        boolean borderlessEmrTable = EmrTableLayoutSupport.isBorderless(tableLayout)
                || (isBorderlessTableRole(tableName) && tableLayout.border() == null);
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
                    String rowLabel = firstRowLabel(row);
                    String cellName = attrText(cell, "name");
                    boolean rowSignCue = textHasSignCue(rowLabel) || isSignParagraphName(cellName);
                    Phrase phrase = buildCellPhrase(cell, rowSignCue);
                    float cellLeading = cellLeadingPt(cell);
                    EmrTableLayoutSupport.CellLayout cellLayout = EmrTableLayoutSupport.parseCell(cell);
                    boolean protocolCell = EmrTableLayoutSupport.hasCellLayout(cellLayout);
                    // 有表就 composite 折行 + 显式 leading。PdfPCell(Phrase) 会按内容撑宽，多列竖排（B20）
                    PdfPCell pdfCell = createColumnWrappingCell(phrase, cellLeading);
                    pdfCell.setPadding(5f);
                    pdfCell.setLeading(cellLeading, 0f);
                    pdfCell.setColspan(colspan);
                    if (rowspan > 1) {
                        pdfCell.setRowspan(rowspan);
                    }
                    if (protocolCell) {
                        // 书写区按本页剩余高度份额垫；无 fillRemain 则只用 minHeightMm
                        Float fillPt = null;
                        if (cellLayout.fillRemainShare() != null && fillRemainHeights != null) {
                            fillPt = fillRemainHeights.heightPtForRow(rowIndex);
                        }
                        EmrTableLayoutSupport.applyCell(pdfCell, cellLayout, fillPt);
                    } else {
                        // 无协议 attrs：只按角色目录缺省
                        applyNamedCellLayout(pdfCell, cell);
                    }
                    paintGridBorder(
                            pdfCell,
                            writtenCols,
                            colspan,
                            colCount,
                            rowIndex == rows.size() - 1,
                            borderlessEmrTable);
                    // 入院表头缺省不折行，避免姓名/职业被窄列折成竖排
                    if ("patientHeader".equals(tableName) && !Boolean.TRUE.equals(cellLayout.wrap())) {
                        pdfCell.setNoWrap(true);
                        if (!protocolCell) {
                            pdfCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                            pdfCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
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
                paintGridBorder(empty, writtenCols, 1, colCount, rowIndex == rows.size() - 1, borderlessEmrTable);
                table.addCell(empty);
                writtenCols++;
            }
            rowIndex++;
        }
        document.add(table);
    }

    /**
     * 角色目录里缺省无框的表。明确写了 border 时不走这里。
     *
     * @param tableName table.attrs.name
     * @return patientHeader / pageHeader / verifyBlock / signBlock 表
     */
    private static boolean isBorderlessTableRole(String tableName) {
        return "patientHeader".equals(tableName)
                || "pageHeader".equals(tableName)
                || "verifyBlock".equals(tableName)
                || "signBlock".equals(tableName);
    }

    /**
     * 无 colWidths 时的角色缺省列宽：标签/值两列、签字左右栏，其余均分。
     * 不出现表名，不写死会诊八列毫米。
     *
     * @param table     目标表
     * @param colCount  列数
     * @param tableNode table 节点
     */
    private static void applyRoleDefaultColumnWidths(PdfPTable table, int colCount, JsonNode tableNode) {
        if (colCount < 2) {
            return;
        }
        try {
            if (colCount == 2 && tableHasCellName(tableNode, "sectionLabel")) {
                table.setWidths(new float[]{1.15f, 8.85f});
            } else if (colCount == 2 && tableHasCellName(tableNode, "noticeSignRight")) {
                table.setWidths(new float[]{6.2f, 3.8f});
            } else {
                float[] widths = new float[colCount];
                for (int i = 0; i < colCount; i++) {
                    widths[i] = 1f;
                }
                table.setWidths(widths);
            }
            table.setWidthPercentage(100f);
        } catch (Exception ignored) {
            // 列宽失败不影响正文
        }
    }

    /**
     * 表内是否出现指定单元格角色名。
     *
     * @param tableNode table JSON
     * @param cellName  attrs.name
     * @return 有则为 true
     */
    private static boolean tableHasCellName(JsonNode tableNode, String cellName) {
        if (tableNode == null || !tableNode.has(CONTENT) || !tableNode.get(CONTENT).isArray()) {
            return false;
        }
        if (!StringUtils.hasText(cellName)) {
            return false;
        }
        for (JsonNode row : tableNode.get(CONTENT)) {
            if (rowHasCellName(row, cellName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 长书写区：有 midBody / tallBody / fillRemain 则整行切页。
     *
     * @param tableNode table JSON
     * @return 需要 setSplitLate
     */
    private static boolean tableHasNamedWriteArea(JsonNode tableNode) {
        return tableHasCellName(tableNode, "midBody")
                || tableHasCellName(tableNode, "tallBody")
                || tableHasCellName(tableNode, "purposeBody");
    }

    /**
     * 按列宽在格内折行的 composite 单元格。
     * {@code new PdfPCell(Phrase)} 是 text-mode：格宽跟内容走，同行后续格被挤到下一行，
     * 入院表头 / 风险评估看起来就像左栏竖排（B20）。
     *
     * @param phrase     格内已拼好的 Chunk
     * @param leadingPt  行距（pt），与编辑器段落 lineHeight 同一行盒；单行也占满
     * @return 可按列宽换行的单元格
     */
    private PdfPCell createColumnWrappingCell(Phrase phrase, float leadingPt) {
        Paragraph paragraph = new Paragraph();
        float leading = leadingPt;
        // 未传入行距时回退正文 1.5 倍，避免 composite 格挤成单倍
        if (leading <= 0f) {
            leading = bodyLeadingPt(bodyFont);
        }
        paragraph.setLeading(leading);
        if (phrase != null && !phrase.isEmpty()) {
            for (Element element : phrase) {
                // FontSelector 产物必须按 Chunk 拷贝；整 Phrase add 会盖掉 1.5 倍行距
                if (element instanceof Chunk) {
                    paragraph.add(element);
                    continue;
                }
                paragraph.add(element);
            }
        }
        PdfPCell cell = new PdfPCell();
        cell.addElement(paragraph);
        // 行盒跟编辑器：1.5 倍含整行。useAscender 会把只有一行的格子收到字面高度，打印比编辑器紧。
        cell.setUseAscender(false);
        cell.setUseDescender(true);
        cell.setNoWrap(false);
        return cell;
    }

    /**
     * 按单元格角色名给缺省行高/对齐。毫米只跟角色走，不出现表名。
     *
     * @param pdfCell 目标格
     * @param cell    JSON 单元格
     */
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
            case "sectionLabel" -> {
                pdfCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                pdfCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            }
            case "sectionValue" -> {
                pdfCell.setHorizontalAlignment(firstParagraphAlign(cell));
                pdfCell.setVerticalAlignment(Element.ALIGN_TOP);
            }
            case "noticeSignRight", "pageHeaderRight", "signRight" -> {
                pdfCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                pdfCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            }
            case "noticeSignLeft", "pageHeaderLeft", "signLeft", "signSpacer" -> {
                pdfCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                pdfCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            }
            case "midBody" -> {
                pdfCell.setVerticalAlignment(Element.ALIGN_TOP);
                pdfCell.setMinimumHeight(mmToPt(12f));
                pdfCell.setPaddingTop(3f);
            }
            case "purposeBody" -> {
                applyPurposeBodyLayout(pdfCell);
            }
            case "tallBody" -> {
                // 书写区只用 minimumHeight，禁止 setFixedHeight 吞落款
                pdfCell.setVerticalAlignment(Element.ALIGN_TOP);
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

    /**
     * 格子线只画一次。相邻格各画四边时，中间线会叠成两倍粗。
     *
     * @param cell       目标格
     * @param colStart   该格起始列
     * @param colspan    跨列数
     * @param colCount   表列数
     * @param lastRow    是否表的最后一行
     * @param borderless 无框表
     */
    private static void paintGridBorder(
            PdfPCell cell,
            int colStart,
            int colspan,
            int colCount,
            boolean lastRow,
            boolean borderless) {
        if (cell == null) {
            return;
        }
        if (borderless) {
            cell.setBorder(PdfPCell.NO_BORDER);
            return;
        }
        int span = Math.max(1, colspan);
        int flags = Rectangle.LEFT | Rectangle.TOP;
        if (colStart + span >= colCount) {
            flags |= Rectangle.RIGHT;
        }
        if (lastRow) {
            flags |= Rectangle.BOTTOM;
        }
        cell.setBorder(flags);
        cell.setBorderWidth(0.5f);
    }

    /**
     * 非书写行按列宽估折行后的高度（mm）。窄列长标签会高于 minHeightMm。
     *
     * @param row          表行
     * @param colShares    列宽份额；无效时返回 0
     * @param tableWidthMm 表宽（mm）
     * @return 估算高度；算不出为 0
     */
    private float estimateWrappedRowMm(JsonNode row, float[] colShares, float tableWidthMm) {
        if (row == null || colShares == null || colShares.length == 0 || tableWidthMm <= 0f) {
            return 0f;
        }
        if (!row.has(CONTENT) || !row.get(CONTENT).isArray()) {
            return 0f;
        }
        float shareSum = 0f;
        for (float share : colShares) {
            shareSum += share;
        }
        if (shareSum <= 0f) {
            return 0f;
        }
        float emMm = bodyFont.getSize() * 25.4f / 72f;
        float leadingMm = bodyLeadingPt(bodyFont) * 25.4f / 72f;
        float maxMm = 0f;
        int col = 0;
        for (JsonNode cell : row.get(CONTENT)) {
            String cellType = normalizeType(cell.path(TYPE).asText(null));
            if (!"tablecell".equals(cellType) && !"tableheader".equals(cellType)) {
                continue;
            }
            int span = Math.max(1, cell.path(ATTRS).path("colspan").asInt(1));
            float colMm = 0f;
            for (int i = 0; i < span && col + i < colShares.length; i++) {
                colMm += tableWidthMm * colShares[col + i] / shareSum;
            }
            EmrTableLayoutSupport.CellLayout layout = EmrTableLayoutSupport.parseCell(cell);
            float padH = 10f * 25.4f / 72f;
            float padV = padH;
            if (layout.paddingMm() != null && layout.paddingMm().length == 4) {
                padV = layout.paddingMm()[0] + layout.paddingMm()[2];
                padH = layout.paddingMm()[1] + layout.paddingMm()[3];
            }
            float innerMm = colMm - padH;
            if (innerMm < emMm) {
                innerMm = emMm;
            }
            int lines = 1;
            boolean singleLine = Boolean.TRUE.equals(layout.nowrap()) && !Boolean.TRUE.equals(layout.wrap());
            if (!singleLine) {
                float textMm = visibleTextWidthMm(cell, emMm);
                if (textMm > innerMm) {
                    lines = (int) Math.ceil(textMm / innerMm);
                }
                if (lines > 6) {
                    lines = 6;
                }
            }
            float heightMm = lines * leadingMm + padV;
            if (heightMm > maxMm) {
                maxMm = heightMm;
            }
            col += span;
        }
        return maxMm;
    }

    /**
     * 格内可见文字的大致宽度（mm）。汉字按一字宽，ASCII 按半字。
     *
     * @param node 单元格或其子节点
     * @param emMm 正文字号对应的一字宽（mm）
     * @return 宽度
     */
    private float visibleTextWidthMm(JsonNode node, float emMm) {
        if (node == null || !node.isObject() || emMm <= 0f) {
            return 0f;
        }
        String type = normalizeType(node.path(TYPE).asText(null));
        if (TYPE_TEXT.equals(type)) {
            return textRunWidthMm(node.path("text").asText(""), emMm);
        }
        if (isInlineFieldType(type)) {
            String value = node.path(ATTRS).path("value").asText("");
            if (!StringUtils.hasText(value) || isUnfilledPlaceholderText(value)) {
                return 0f;
            }
            return textRunWidthMm(value.trim(), emMm);
        }
        float width = 0f;
        if (node.has(CONTENT) && node.get(CONTENT).isArray()) {
            for (JsonNode child : node.get(CONTENT)) {
                width += visibleTextWidthMm(child, emMm);
            }
        }
        return width;
    }

    private static float textRunWidthMm(String text, float emMm) {
        if (text == null || text.isEmpty() || emMm <= 0f) {
            return 0f;
        }
        float width = 0f;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                continue;
            }
            if (c <= 0x7f) {
                width += emMm * 0.5f;
            } else {
                width += emMm;
            }
        }
        return width;
    }

    /**
     * purposeBody：一行文案，不要垫成摘要那样的书写区。
     *
     * @param pdfCell 目的格
     */
    private void applyPurposeBodyLayout(PdfPCell pdfCell) {
        pdfCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        pdfCell.setMinimumHeight(mmToPt(12f));
        pdfCell.setPaddingTop(6f);
        pdfCell.setPaddingBottom(6f);
        pdfCell.setPaddingLeft(8f);
        pdfCell.setPaddingRight(8f);
    }

    /**
     * 行内是否有指定 data-name 的单元格。
     *
     * @param row      表行
     * @param cellName attrs.name
     * @return 有则为 true
     */
    private static boolean rowHasCellName(JsonNode row, String cellName) {
        if (row == null || !row.has(CONTENT) || !row.get(CONTENT).isArray()) {
            return false;
        }
        if (!StringUtils.hasText(cellName)) {
            return false;
        }
        for (JsonNode cell : row.get(CONTENT)) {
            if (cellName.equals(attrText(cell, "name"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 协议 fillRemainShare 行垫高（pt）。下标与表行一致。
     *
     * @param heightPtByRow 每行最小高度；非书写行为 0
     */
    private record FillRemainRowHeights(float[] heightPtByRow) {
        /**
         * 取指定行的剩余高度份额对应 pt。
         *
         * @param rowIndex 表行下标
         * @return pt；越界或非书写行为 0
         */
        private float heightPtForRow(int rowIndex) {
            if (heightPtByRow == null || rowIndex < 0 || rowIndex >= heightPtByRow.length) {
                return 0f;
            }
            return heightPtByRow[rowIndex];
        }
    }

    /**
     * 按格 fillRemainShare 分配本页剩余高度。
     * 先扣无该字段的行（minHeightMm 或引擎默认非书写行高），剩下按份额分给书写区。
     * 空单铺满当前页且落款不挤进页脚；禁止 setFixedHeight。
     *
     * @param tableNode 表 JSON，用来取列宽份额
     * @param rows      表全部行
     * @param colCount  列数
     * @return 每行垫高（pt）
     */
    private FillRemainRowHeights measureFillRemainRowHeights(JsonNode tableNode, List<JsonNode> rows, int colCount) {
        float remainingPt = 0f;
        if (writer != null && document != null) {
            remainingPt = writer.getVerticalPosition(true) - document.bottom();
        }
        float tableWidthMm = 0f;
        if (document != null) {
            tableWidthMm = (document.right() - document.left()) * 25.4f / 72f;
        }
        float[] colShares = EmrTableLayoutSupport.readColWidths(tableNode, colCount);
        float reservedPt = mmToPt(FILL_REMAIN_PAGE_SLACK_MM);
        float[] shares = new float[rows.size()];
        float shareTotal = 0f;
        for (int i = 0; i < rows.size(); i++) {
            JsonNode row = rows.get(i);
            Float share = EmrTableLayoutSupport.rowFillRemainShare(row);
            if (share != null) {
                // 书写行不预扣高度，后面按份额分剩余
                shares[i] = share;
                shareTotal += share;
                continue;
            }
            // 折行标签（如「申请会诊目的」）比 minHeightMm 高。只按最小高度扣，书写区会把落款挤到下一页。
            float rowMm = FILL_REMAIN_NON_WRITE_ROW_MM;
            Float minMm = EmrTableLayoutSupport.rowMinHeightMm(row);
            if (minMm != null && minMm > rowMm) {
                rowMm = minMm;
            }
            float wrappedMm = estimateWrappedRowMm(row, colShares, tableWidthMm);
            if (wrappedMm > rowMm) {
                rowMm = wrappedMm;
            }
            reservedPt += mmToPt(rowMm);
        }
        float writingPt = remainingPt - reservedPt;
        if (writingPt < mmToPt(24f)) {
            writingPt = mmToPt(24f);
        }
        float[] heightPt = new float[rows.size()];
        if (shareTotal > 0f) {
            for (int i = 0; i < rows.size(); i++) {
                if (shares[i] <= 0f) {
                    continue;
                }
                heightPt[i] = writingPt * shares[i] / shareTotal;
            }
        }
        return new FillRemainRowHeights(heightPt);
    }

    /**
     * 单元格行距：段上有 lineHeight 则按块属性折算，否则用正文 1.5 倍。
     *
     * @param cell JSON 单元格
     * @return leading（pt）
     */
    private float cellLeadingPt(JsonNode cell) {
        if (cell != null && cell.has(CONTENT) && cell.get(CONTENT).isArray()) {
            for (JsonNode block : cell.get(CONTENT)) {
                if (!"paragraph".equals(normalizeType(block.path(TYPE).asText(null)))) {
                    continue;
                }
                String raw = block.path(ATTRS).path("lineHeight").asText(null);
                Float leading = resolveLineHeightLeading(raw, bodyFont.getSize());
                if (leading != null && leading > 0f) {
                    return leading;
                }
            }
        }
        return bodyLeadingPt(bodyFont);
    }

    /**
     * 行首格固定文案（不含填写值），用来判断这一行是否进入签字上下文。
     *
     * @param row tableRow
     * @return 文案；无则空串
     */
    private static String firstRowLabel(JsonNode row) {
        if (row == null || !row.has(CONTENT) || !row.get(CONTENT).isArray()) {
            return "";
        }
        for (JsonNode cell : row.get(CONTENT)) {
            String text = collectFixedText(cell).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    /**
     * 格内首段 textAlign → OpenPDF 对齐；缺省左对齐（与模版 JSON 一致）。
     *
     * @param cell JSON 单元格
     * @return ALIGN_LEFT / CENTER / RIGHT / JUSTIFIED
     */
    private static int firstParagraphAlign(JsonNode cell) {
        if (cell == null || !cell.has(CONTENT) || !cell.get(CONTENT).isArray()) {
            return Element.ALIGN_LEFT;
        }
        for (JsonNode block : cell.get(CONTENT)) {
            if (!"paragraph".equals(normalizeType(block.path(TYPE).asText(null)))) {
                continue;
            }
            String align = block.path(ATTRS).path("textAlign").asText("");
            if ("center".equalsIgnoreCase(align)) {
                return Element.ALIGN_CENTER;
            }
            if ("right".equalsIgnoreCase(align)) {
                return Element.ALIGN_RIGHT;
            }
            if ("justify".equalsIgnoreCase(align)) {
                return Element.ALIGN_JUSTIFIED;
            }
            return Element.ALIGN_LEFT;
        }
        return Element.ALIGN_LEFT;
    }

    /**
     * 收集节点树里的固定 text（不含数据元填写值）。
     *
     * @param node 单元格或子节点
     * @return 拼接文案
     */
    private static String collectFixedText(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        appendFixedText(node, sb);
        return sb.toString();
    }

    /**
     * 递归追加固定 text 节点。
     *
     * @param node 当前节点
     * @param sb   输出
     */
    private static void appendFixedText(JsonNode node, StringBuilder sb) {
        if (node == null || !node.isObject()) {
            return;
        }
        String type = normalizeType(node.path(TYPE).asText(null));
        if (TYPE_TEXT.equals(type)) {
            sb.append(node.path("text").asText(""));
            return;
        }
        if (node.has(CONTENT) && node.get(CONTENT).isArray()) {
            for (JsonNode child : node.get(CONTENT)) {
                appendFixedText(child, sb);
            }
        }
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
        return buildCellPhrase(cell, false);
    }

    /**
     * 单元格 Phrase。
     *
     * @param cell         表格单元格
     * @param afterSignCue 行首含签名/签字或 signBlock 表时，姓名域留白
     * @return 单元格内容
     */
    private Phrase buildCellPhrase(JsonNode cell, boolean afterSignCue) {
        Phrase phrase = new Phrase();
        phrase.setLeading(bodyLeadingPt(bodyFont));
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
            boolean blockCue = afterSignCue || isSignParagraphName(attrText(block, "name"));
            appendBlockTextToPhrase(phrase, block, blockCue);
        }
        if (phrase.isEmpty()) {
            phrase.add(new Chunk("", bodyFont));
        }
        return phrase;
    }

    private void appendBlockTextToPhrase(Phrase phrase, JsonNode block) {
        appendBlockTextToPhrase(phrase, block, false);
    }

    private void appendBlockTextToPhrase(Phrase phrase, JsonNode block, boolean afterSignCue) {
        if (block == null || !block.isObject()) {
            return;
        }
        String type = normalizeType(block.path(TYPE).asText(null));
        if ("emrcheckboxfield".equals(type)) {
            appendCheckboxChunk(phrase, block, bodyFont);
            return;
        }
        if (isInlineFieldType(type)) {
            appendInlineFieldOrBlank(phrase, block, bodyFont, afterSignCue);
            return;
        }
        if (!block.has(CONTENT) || !block.get(CONTENT).isArray()) {
            return;
        }
        boolean cue = afterSignCue || isSignParagraphName(attrText(block, "name"));
        for (JsonNode inline : block.get(CONTENT)) {
            if (!inline.isObject()) {
                continue;
            }
            String inlineType = normalizeType(inline.path(TYPE).asText(null));
            if (TYPE_TEXT.equals(inlineType)) {
                String raw = inline.path("text").asText("");
                appendTextNodeToPhrase(phrase, inline);
                if (textHasSignCue(raw)) {
                    cue = true;
                }
            } else if ("image".equals(inlineType)) {
                Chunk imgChunk = buildInlineImageChunk(inline);
                if (imgChunk != null) {
                    phrase.add(imgChunk);
                }
            } else if ("emrcheckboxfield".equals(inlineType)) {
                appendCheckboxChunk(phrase, inline, bodyFont);
            } else if (isInlineFieldType(inlineType)) {
                appendInlineFieldOrBlank(phrase, inline, bodyFont, cue);
            } else if (inline.has(CONTENT) && inline.get(CONTENT).isArray()) {
                for (JsonNode nested : inline.get(CONTENT)) {
                    if (TYPE_TEXT.equals(normalizeType(nested.path(TYPE).asText(null)))) {
                        appendTextNodeToPhrase(phrase, nested);
                        if (textHasSignCue(nested.path("text").asText(""))) {
                            cue = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * 表格单元格内的 text 节点，规则同 {@link #appendTextNode}。
     *
     * @param phrase   单元格 Phrase
     * @param textNode ProseMirror text 节点
     */
    private void appendTextNodeToPhrase(Phrase phrase, JsonNode textNode) {
        String text = textNode.path("text").asText("");
        // 表格内空槽 placeholder 同样不进 PDF（B17）
        if (text.isEmpty() || isUnfilledPlaceholderText(text)) {
            return;
        }
        if (!renderOptions.isIncludeTrackChanges() && hasMark(textNode, "deletion")) {
            return;
        }
        text = stripInlineSignNames(text);
        if (text.isEmpty()) {
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
        addPrintText(phrase, text, f);
    }

    /** 数据元展示值；签名图等 data URL 不进表格文本。保留行首空格，供自由文本缩进打印。 */
    private String extractDataElementDisplay(JsonNode node) {
        String value = extractDataElementValue(node);
        if (value == null || value.isEmpty()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("data:image/")) {
            return "";
        }
        return value;
    }

    /**
     * 中/西医初步诊断悬挂缩进：续行左缘与首行诊断值对齐，而不是对齐「中/西」字。
     * OpenPDF 的 {@code indentationLeft} 作用于每一行，{@code firstLineIndent} 为负则首行拉回段首。
     *
     * @param node     命名段落 JSON（须含标签文本 + 可填域）
     * @param p        已拼好 inline 的段落
     * @param baseFont 正文字体，用于按实际字号测前缀宽度
     */
    private void applyDiagnosisHangingIndent(JsonNode node, Paragraph p, Font baseFont) {
        if (!node.has(CONTENT)) {
            return;
        }
        float prefixWidth = measureInlinePrefixWidthBeforeFirstField(node.get(CONTENT), baseFont);
        if (prefixWidth <= 0f) {
            return;
        }
        p.setIndentationLeft(prefixWidth);
        p.setFirstLineIndent(-prefixWidth);
    }

    /**
     * 测量可填域之前的标签前缀宽度（全角缩进 +「中医/西医初步诊断：」）。
     * 遇到第一个 dataelement / freetext 即停，其后是诊断值，不计入悬挂宽度。
     *
     * @param inlineNodes 段落 content
     * @param baseFont    正文字体
     * @return 前缀宽度（pt）；无前缀或无法测量时为 0
     */
    private float measureInlinePrefixWidthBeforeFirstField(JsonNode inlineNodes, Font baseFont) {
        if (inlineNodes == null || !inlineNodes.isArray()) {
            return 0f;
        }
        BaseFont bf = fontFactory.getBaseFont();
        if (bf == null) {
            return 0f;
        }
        float width = 0f;
        float defaultSize = baseFont.getSize();
        for (JsonNode inline : inlineNodes) {
            if (!inline.isObject()) {
                continue;
            }
            String type = normalizeType(inline.path(TYPE).asText(null));
            if (isInlineFieldType(type)) {
                break;
            }
            if (!TYPE_TEXT.equals(type)) {
                continue;
            }
            if (!renderOptions.isIncludeTrackChanges() && hasMark(inline, "deletion")) {
                continue;
            }
            String text = inline.path("text").asText("");
            if (text.isEmpty()) {
                continue;
            }
            float sizePt = resolveMarkFontSizePt(inline, defaultSize);
            width += bf.getWidthPoint(text, sizePt);
        }
        return width;
    }

    /**
     * 正文 Word n 倍行距（pt）：字号 × 倍数 × 中文单倍系数 1.55。
     * 小四 12pt × 1.5 × 1.55 ≈ 28pt，与屏上 --simple-editor-line-height: 2.33 对齐。
     *
     * @param font 段落字体；空则用正文默认字号
     * @return PDF Paragraph leading（基线距）
     */
    private float bodyLeadingPt(Font font) {
        float size = 12f;
        // 正文默认字号（小四 12pt）
        if (bodyFont != null && bodyFont.getSize() > 0f) {
            size = bodyFont.getSize();
        }
        // 当前段若放大/缩小，按该段字号折算 28pt 级行距
        if (font != null && font.getSize() > 0f) {
            size = font.getSize();
        }
        float multiple = 1.5f;
        if (printConfig != null && printConfig.getFont() != null && printConfig.getFont().getLineHeight() > 0f) {
            multiple = printConfig.getFont().getLineHeight();
        }
        return size * multiple * CJK_SINGLE_SPACING_FACTOR;
    }

    /**
     * 写入可印文本。CJK CID 字体把半角英文按全角宽走字时，由字体工厂拆到 Times。
     *
     * @param phrase 目标段落或单元格
     * @param text   原文
     * @param font   当前字号与样式
     */
    private void addPrintText(Phrase phrase, String text, Font font) {
        fontFactory.appendText(phrase, text, font);
    }

    /**
     * 用混排字体建段落（leading 由调用方再设）。
     *
     * @param text 原文
     * @param font 字号与样式
     * @return 新段落
     */
    private Paragraph printParagraph(String text, Font font) {
        Paragraph p = new Paragraph();
        fontFactory.appendText(p, text, font);
        return p;
    }

    /**
     * 把自由文本/数据元原文写入 Phrase：保留行首缩进，按换行切段以沿用段落 leading。
     *
     * @param phrase 目标段落或单元格
     * @param value  attrs.value 原文（可含换行与空格）
     * @param font   正文字体
     */
    private void appendFieldDisplayValue(Phrase phrase, String value, Font font) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String[] lines = value.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                phrase.add(Chunk.NEWLINE);
            }
            String line = preserveLeadingSpacesForPdf(lines[i]);
            if (line.isEmpty()) {
                continue;
            }
            addPrintText(phrase, line, font);
        }
    }

    /**
     * OpenPDF 在换行后会吃掉行首半角空格；改成不换行空格以保留屏上 pre-wrap 缩进。全角空格原样保留。
     *
     * @param line 单行文本
     * @return 行首半角空格已换成 NBSP 的文本
     */
    private static String preserveLeadingSpacesForPdf(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        int i = 0;
        int n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (c != ' ' && c != '\t') {
                break;
            }
            i++;
        }
        if (i == 0) {
            return line;
        }
        StringBuilder sb = new StringBuilder(line.length());
        for (int j = 0; j < i; j++) {
            if (line.charAt(j) == '\t') {
                sb.append('\u00A0').append('\u00A0').append('\u00A0').append('\u00A0');
            } else {
                sb.append('\u00A0');
            }
        }
        sb.append(line.substring(i));
        return sb.toString();
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
            // 文本节点用 asText；数字/布尔等走 toString，避免把 JSON 引号打进纸面
            if (v.isTextual()) {
                fromAttrs = v.asText();
            } else {
                fromAttrs = v.toString();
            }
        }
        // 数据元 value 若仍是「请在这里填写…」则当空槽，不打上纸
        if (isUnfilledPlaceholderText(fromAttrs)) {
            fromAttrs = "";
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

    /**
     * 未填填写岛的 placeholder 文案不进正式打印（编辑器里是灰字提示）。
     *
     * @param value 节点 value 或 text
     * @return true 则按空槽处理
     */
    private static boolean isUnfilledPlaceholderText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String t = value.trim();
        // 自由文本岛未填
        if (t.startsWith("请在这里填写")) {
            return true;
        }
        // 下拉/选择岛未填
        if (t.startsWith("请在这里选择")) {
            return true;
        }
        return false;
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

    /**
     * 块级 lineHeight：无单位视为 Word 倍数（再乘中文单倍 1.55）；带单位则按绝对 pt。
     *
     * @param raw        attrs.lineHeight，如 1.5 或 28pt
     * @param fontSizePt 当前段字号
     * @return leading（pt）；无法解析时 null
     */
    private static Float resolveLineHeightLeading(String raw, float fontSizePt) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        if (s.matches("^[0-9]*\\.?[0-9]+$")) {
            try {
                float multiple = Float.parseFloat(s);
                return fontSizePt * multiple * CJK_SINGLE_SPACING_FACTOR;
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

    /**
     * 勾选：√/□ + 标签 + 全角空格。宋体没有 ☑，系统 checkbox 也不能进 PDF，必须用字。
     *
     * @param phrase 目标段落或单元格
     * @param node   checkbox 节点
     * @param font   正文字体样式
     */
    private void appendCheckboxChunk(Phrase phrase, JsonNode node, Font font) {
        boolean checked = node.path(ATTRS).path("checked").asBoolean(false)
                || "true".equalsIgnoreCase(node.path(ATTRS).path("checked").asText(""));
        String label = attrText(node, "label");
        // 与编辑器 EmrCheckboxFieldView 同一套字：未勾 □、已勾 √
        String mark = "□";
        if (checked) {
            mark = "√";
        }
        String text = mark;
        if (StringUtils.hasText(label)) {
            text = mark + label;
        }
        addPrintText(phrase, text + "　", font);
    }

    /**
     * 段内是否有可印内容。空 text 不算；填写岛屿/图片/勾选仍算，避免把空控件段误删。
     */
    private boolean hasPrintableInlineContent(JsonNode node) {
        if (node == null || !node.has(CONTENT) || !node.get(CONTENT).isArray()) {
            return false;
        }
        for (JsonNode inline : node.get(CONTENT)) {
            if (!inline.isObject()) {
                continue;
            }
            String type = normalizeType(inline.path(TYPE).asText(null));
            if (TYPE_TEXT.equals(type)) {
                if (StringUtils.hasText(inline.path("text").asText(""))) {
                    return true;
                }
            } else if ("image".equals(type) || isInlineFieldType(type)) {
                return true;
            } else if (inline.has(CONTENT) && hasPrintableInlineContent(inline)) {
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
                || "emrmetadatachip".equals(type)
                || "emrfreetext".equals(type);
    }

    /**
     * 打印预览/正式打印：签字位姓名留白供手写。
     * 「接诊医生」信息条（无签名/签字上下文）仍打印姓名；签字日期/时间仍打印。
     *
     * @param node         chip / freeText / dataElement
     * @param afterSignCue 上文或命名段已进入签字上下文
     * @return true 则打印下划线占位，不输出姓名/签名图
     */
    private boolean shouldBlankSignatureSlot(JsonNode node, boolean afterSignCue) {
        if (node == null || !node.isObject()) {
            return false;
        }
        if (isSignatureLabelSlot(node)) {
            return true;
        }
        if (isSignatureImageField(node)) {
            return true;
        }
        if (afterSignCue && isPersonSignNameField(node)) {
            return true;
        }
        return false;
    }

    /** label 含「签名/签字」且非日期时间 → 手写姓名位 */
    private boolean isSignatureLabelSlot(JsonNode node) {
        String label = fieldLabel(node);
        if (!StringUtils.hasText(label)) {
            return false;
        }
        if (label.contains("日期") || label.contains("时间")) {
            return false;
        }
        return label.contains("签名") || label.contains("签字");
    }

    private boolean isSignatureImageField(JsonNode node) {
        String key = fieldKeyOf(node);
        if (!StringUtils.hasText(key)) {
            return false;
        }
        return key.toLowerCase().contains("signatureimage");
    }

    /**
     * 签字上下文后应留白的姓名类域（含 doctorName / 接诊医生）。
     * 无签字上下文时 doctorName 仍可作「接诊医生」信息展示。
     */
    private boolean isPersonSignNameField(JsonNode node) {
        String key = fieldKeyOf(node);
        if ("doctorName".equals(key)
                || "attendingPhysician".equals(key)
                || "verifierSign".equals(key)) {
            return true;
        }
        String label = fieldLabel(node);
        if (!StringUtils.hasText(label)) {
            return false;
        }
        String trimmed = label.trim();
        if (SIGN_ROLE_NAME_LABELS.contains(trimmed)) {
            return true;
        }
        return "接诊医生".equals(trimmed) || "医师签名".equals(trimmed);
    }

    /** doctorSign / signLine* / noticeSign* 等落款命名段 */
    private static boolean isSignParagraphName(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        String n = name.trim();
        if (n.startsWith("signLine") || n.startsWith("noticeSign")) {
            return true;
        }
        return "doctorSign".equals(n)
                || "riskGuardianSign".equals(n)
                || "signBlock".equals(n)
                || "signLine".equals(n);
    }

    /** 文案是否进入签字上下文（含会诊医师等落款角色，不含单独的「接诊医生」信息条） */
    private static boolean textHasSignCue(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (text.contains("签名") || text.contains("签字")) {
            return true;
        }
        for (String role : SIGN_ROLE_NAME_LABELS) {
            if (text.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 同一 text 节点内「签名：张三」去掉姓名，保留标签供手写。
     * 不含「请签字确认」等无冒号说明句。
     */
    private static String stripInlineSignNames(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        if (!text.contains("签名") && !text.contains("签字")) {
            return text;
        }
        return text.replaceAll("(签名|签字)([）)])?[：:]\\s*[^\\s　：:，,；;]{1,20}", "$1$2：");
    }

    private static String fieldKeyOf(JsonNode node) {
        JsonNode attrs = node.get(ATTRS);
        if (attrs == null) {
            return "";
        }
        String key = attrs.path("fieldKey").asText("");
        if (StringUtils.hasText(key)) {
            return key;
        }
        return attrs.path("elementId").asText("");
    }

    private String fieldLabel(JsonNode node) {
        return firstNonBlank(
                attrText(node, "label"),
                attrText(node, "labelSnapshot"),
                attrText(node, "title"),
                attrText(node, "name"));
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
