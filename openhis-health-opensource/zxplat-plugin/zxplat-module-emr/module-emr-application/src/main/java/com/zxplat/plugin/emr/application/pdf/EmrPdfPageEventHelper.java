/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.zxplat.plugin.emr.application.pdf.config.PrintConfig;
import com.zxplat.plugin.emr.application.pdf.model.PdfRenderOptions;
import lombok.RequiredArgsConstructor;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 页眉、页脚、水印绘制。
 */
@RequiredArgsConstructor
public class EmrPdfPageEventHelper extends PdfPageEventHelper {

    /** 总页数槽宽：约容纳 3 位数字（9pt），过宽会导致「共 4      页」空隙 */
    private static final float TOTAL_TMPL_WIDTH = 14f;
    private static final float TOTAL_TMPL_HEIGHT = 14f;
    private static final float FOOTER_FONT_SIZE = 9f;
    /** 与屏上 .simple-editor__page-footer padding-top ≈ 6px 对齐 */
    private static final float FOOTER_BAND_TOP_PAD_MM = 2f;
    /** 页眉下沿、页脚上沿线宽：Word 1 磅 = 1pt，与屏上 --simple-editor-chrome-rule 对齐 */
    private static final float CHROME_RULE_PT = 1f;
    /** 与 DocumentManager 创建标题后缀 yyyy-MM-dd HH:mm 对齐；打印页眉去掉该时间 */
    private static final Pattern CREATE_TIME_SUFFIX =
            Pattern.compile("-\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$");

    private final PrintConfig config;
    private final PdfRenderOptions options;
    private final EmrPdfFontFactory fontFactory;

    /** 总页数占位模板，onCloseDocument 写入真实页数 */
    private PdfTemplate totalPagesTemplate;
    /** 最近一次 onEndPage 的页码（关闭文档时 getPageNumber 常多 1，以此为准） */
    private int lastEndedPageNumber;

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
        totalPagesTemplate = writer.getDirectContent().createTemplate(TOTAL_TMPL_WIDTH, TOTAL_TMPL_HEIGHT);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        int page = writer.getPageNumber();
        lastEndedPageNumber = page;
        float pageWidth = document.getPageSize().getWidth();
        float pageHeight = document.getPageSize().getHeight();
        Font smallFont = fontFactory.bodyFont(FOOTER_FONT_SIZE, Font.NORMAL);

        if (config.getHeader().isEnabled() && hasText(config.getHeader().getTemplate())) {
            drawHeader(writer, document, page, smallFont);
        }

        // 页眉下沿 / 页脚上沿 1 磅线：所有病历每页都画，不依赖页眉页脚是否有文案
        drawHeaderFooterRules(writer, document);

        if (config.getFooter().isEnabled()) {
            String template = config.getFooter().getTemplate();
            if (!hasText(template) && config.getFooter().isShowPageNumber()) {
                template = "第 {page} 页 (共 {total} 页)";
            }
            if (hasText(template)) {
                float y = resolveFooterBaselineY(document);
                if (template.contains("{total}") && totalPagesTemplate != null) {
                    drawFooterWithTotal(writer.getDirectContent(), template, page, pageWidth, y, smallFont);
                } else {
                    String text = resolveTemplate(template, page, "?");
                    ColumnText.showTextAligned(
                            writer.getDirectContent(),
                            Element.ALIGN_CENTER,
                            fontFactory.phrase(text, smallFont),
                            pageWidth / 2f,
                            y,
                            0);
                }
            }
        }

        drawWatermark(writer, pageWidth, pageHeight);
    }

    /**
     * 每页正文区顶、底各画 1 磅实线（页眉下沿 / 页脚上沿），左右对齐页边距。
     * 空页眉页脚也要画，避免入院/病程等文书纸面缺线。
     *
     * @param writer   当前页 PdfWriter
     * @param document 已设正文边距的 Document
     */
    private void drawHeaderFooterRules(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContent();
        float left = document.left();
        float right = document.right();
        float top = document.top();
        float bottom = document.bottom();
        cb.saveState();
        cb.setLineWidth(CHROME_RULE_PT);
        cb.setColorStroke(Color.BLACK);
        cb.moveTo(left, top);
        cb.lineTo(right, top);
        cb.stroke();
        cb.moveTo(left, bottom);
        cb.lineTo(right, bottom);
        cb.stroke();
        cb.restoreState();
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
        if (totalPagesTemplate == null) {
            return;
        }
        Font smallFont = fontFactory.bodyFont(FOOTER_FONT_SIZE, Font.NORMAL);
        // OpenPDF 在 close 时 getPageNumber() 常为「下一页号」= 实际页数+1，用末次 onEndPage 页码
        int totalPages = lastEndedPageNumber;
        if (totalPages <= 0) {
            totalPages = Math.max(1, writer.getPageNumber() - 1);
        }
        String total = String.valueOf(totalPages);
        ColumnText.showTextAligned(
                totalPagesTemplate,
                Element.ALIGN_LEFT,
                fontFactory.phrase(total, smallFont),
                0,
                1,
                0);
    }

    /**
     * 页脚基线：落在正文底边距带顶部（屏上 justify-content:flex-start + padding-top）。
     * 禁止用 footer.heightMm 当距纸底偏移——contentBottomMm 已含整段页脚带，旧算法会把页码画进正文区。
     */
    private float resolveFooterBaselineY(Document document) {
        float marginBottomPt = document.bottomMargin();
        PrintConfig.MarginConfig margins = config.getMargins();
        float bandTopPt = marginBottomPt;
        if (margins != null && margins.getContentBottomMm() > 0.5f) {
            bandTopPt = PdfUnitUtils.mmToPt(margins.getContentBottomMm());
        }
        float footerTopPadPt = PdfUnitUtils.mmToPt(FOOTER_BAND_TOP_PAD_MM);
        float baselineY = bandTopPt - footerTopPadPt - FOOTER_FONT_SIZE * 0.75f;
        return Math.max(PdfUnitUtils.mmToPt(3f), baselineY);
    }

    /**
     * 页眉带内多行居中绘制（支持 emrImg、span 字号与换行）。
     */
    private void drawHeader(PdfWriter writer, Document document, int page, Font font) {
        String text = resolveTemplate(config.getHeader().getTemplate(), page, null);
        if (!hasText(text)) {
            return;
        }
        float pageWidth = document.getPageSize().getWidth();
        float pageHeight = document.getPageSize().getHeight();
        float bandMm = Math.max(config.getHeader().getHeightMm(), 10f);
        float marginLeft = PdfUnitUtils.mmToPt(config.getMargins().getLeftMm());
        float marginRight = PdfUnitUtils.mmToPt(config.getMargins().getRightMm());
        EmrChromeBandPainter.drawHeaderBand(
                writer.getDirectContent(),
                pageWidth,
                pageHeight,
                marginLeft,
                marginRight,
                bandMm,
                text,
                fontFactory);
    }

    /**
     * 页脚含 {total}：先画「第 n 页 (共 」再贴 PdfTemplate，最后画「 页)」，整段水平居中。
     */
    private void drawFooterWithTotal(
            PdfContentByte cb, String template, int page, float pageWidth, float y, Font smallFont) {
        String resolved = resolveTemplate(template, page, "\0");
        int marker = resolved.indexOf('\0');
        String left = marker >= 0 ? resolved.substring(0, marker) : resolved;
        String right = marker >= 0 ? resolved.substring(marker + 1) : "";

        BaseFont bf = fontFactory.getBaseFont();
        float leftW = bf.getWidthPoint(left, FOOTER_FONT_SIZE);
        float rightW = bf.getWidthPoint(right, FOOTER_FONT_SIZE);
        float totalW = leftW + TOTAL_TMPL_WIDTH + rightW;
        float startX = (pageWidth - totalW) / 2f;

        cb.beginText();
        cb.setFontAndSize(bf, FOOTER_FONT_SIZE);
        cb.setTextMatrix(startX, y);
        cb.showText(left);
        cb.endText();

        cb.addTemplate(totalPagesTemplate, startX + leftW, y - 1f);

        if (!right.isEmpty()) {
            cb.beginText();
            cb.setFontAndSize(bf, FOOTER_FONT_SIZE);
            cb.setTextMatrix(startX + leftW + TOTAL_TMPL_WIDTH, y);
            cb.showText(right);
            cb.endText();
        }
    }

    private void drawWatermark(PdfWriter writer, float pageWidth, float pageHeight) {
        PrintConfig.WatermarkConfig wm = config.getWatermark();
        if (!wm.isEnabled() || !hasText(wm.getText())) {
            return;
        }
        PdfContentByte under = writer.getDirectContentUnder();
        under.saveState();
        under.setColorFill(new Color(0.6f, 0.6f, 0.6f, wm.getOpacity()));
        Font wmFont = fontFactory.bodyFont(wm.getFontSizePt(), Font.NORMAL);
        ColumnText.showTextAligned(
                under,
                Element.ALIGN_CENTER,
                fontFactory.phrase(wm.getText(), wmFont),
                pageWidth / 2f,
                pageHeight / 2f,
                wm.getAngle());
        under.restoreState();
    }

    private String resolveTemplate(String template, int page, String totalReplacement) {
        Map<String, String> meta = options.safeMetadata();
        String documentTitle = "";
        if (options.getDocumentTitle() != null) {
            documentTitle = options.getDocumentTitle();
        }
        documentTitle = stripCreateTimeSuffix(documentTitle);
        String documentTypeName = "";
        if (options.getDocumentTypeName() != null) {
            documentTypeName = options.getDocumentTypeName();
        }
        String withPage = template
                .replace("{page}", String.valueOf(page))
                .replace("{patientName}", meta.getOrDefault("patientName", ""))
                .replace("{inpatientNo}", meta.getOrDefault("inpatientNo", ""))
                .replace("{gender}", meta.getOrDefault("gender", ""))
                .replace("{age}", meta.getOrDefault("age", ""))
                .replace("{documentTitle}", documentTitle)
                .replace("{documentTypeName}", documentTypeName)
                .replace("{orgName}", meta.getOrDefault("orgName", ""))
                .replace("{deptName}", meta.getOrDefault("deptName", ""))
                .replace("{printTime}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        // 其余元数据占位（如模板自定义 fieldKey）按 map 回填，避免打印残留 {key}
        for (Map.Entry<String, String> entry : meta.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String token = "{" + key + "}";
            if (withPage.contains(token)) {
                String value = entry.getValue();
                withPage = withPage.replace(token, value != null ? value : "");
            }
        }
        if (totalReplacement != null) {
            return withPage.replace("{total}", totalReplacement);
        }
        return withPage;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 去掉创建文书时拼在标题末尾的「-yyyy-MM-dd HH:mm」，纸面只出文书名。
     *
     * @param title 库内文书标题（可能带创建时刻）
     * @return 打印用标题；无时间后缀则原样
     */
    private static String stripCreateTimeSuffix(String title) {
        if (title == null) {
            return "";
        }
        String trimmed = title.trim();
        Matcher matcher = CREATE_TIME_SUFFIX.matcher(trimmed);
        String name = trimmed;
        if (matcher.find()) {
            String head = trimmed.substring(0, matcher.start()).trim();
            if (!head.isEmpty()) {
                name = head;
            }
        }
        // 去掉模板变体后缀「（标准）」/「标准」，与文书类型名对齐（如入院记录）
        name = name.replaceAll("[（(]\\s*标准\\s*[）)]", "").trim();
        name = name.replaceAll("标准$", "").trim();
        if (name.isEmpty()) {
            return trimmed;
        }
        return name;
    }
}
