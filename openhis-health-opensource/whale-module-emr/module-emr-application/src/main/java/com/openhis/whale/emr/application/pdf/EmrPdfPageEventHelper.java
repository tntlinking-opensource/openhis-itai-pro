/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.openhis.whale.emr.application.pdf.config.PrintConfig;
import com.openhis.whale.emr.application.pdf.model.PdfRenderOptions;
import lombok.RequiredArgsConstructor;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 页眉、页脚、水印绘制。
 */
@RequiredArgsConstructor
public class EmrPdfPageEventHelper extends PdfPageEventHelper {

    private static final float TOTAL_TMPL_WIDTH = 36f;
    private static final float TOTAL_TMPL_HEIGHT = 14f;
    private static final float FOOTER_FONT_SIZE = 9f;

    private final PrintConfig config;
    private final PdfRenderOptions options;
    private final EmrPdfFontFactory fontFactory;

    /** 总页数占位模板，onCloseDocument 写入真实页数 */
    private PdfTemplate totalPagesTemplate;

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
        totalPagesTemplate = writer.getDirectContent().createTemplate(TOTAL_TMPL_WIDTH, TOTAL_TMPL_HEIGHT);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        int page = writer.getPageNumber();
        float pageWidth = document.getPageSize().getWidth();
        float pageHeight = document.getPageSize().getHeight();
        Font smallFont = fontFactory.bodyFont(FOOTER_FONT_SIZE, Font.NORMAL);

        if (config.getHeader().isEnabled() && hasText(config.getHeader().getTemplate())) {
            String text = resolveTemplate(config.getHeader().getTemplate(), page, null);
            ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    new Phrase(text, smallFont),
                    pageWidth / 2f,
                    pageHeight - PdfUnitUtils.mmToPt(Math.max(config.getHeader().getHeightMm(), 10f) / 2f),
                    0);
        }

        if (config.getFooter().isEnabled()) {
            String template = config.getFooter().getTemplate();
            if (!hasText(template) && config.getFooter().isShowPageNumber()) {
                template = "第 {page} 页 (共 {total} 页)";
            }
            if (hasText(template)) {
                float footerBandMm = Math.max(config.getFooter().getHeightMm(), 8f);
                float y = PdfUnitUtils.mmToPt(footerBandMm);
                if (template.contains("{total}") && totalPagesTemplate != null) {
                    drawFooterWithTotal(writer.getDirectContent(), template, page, pageWidth, y, smallFont);
                } else {
                    String text = resolveTemplate(template, page, "?");
                    ColumnText.showTextAligned(
                            writer.getDirectContent(),
                            Element.ALIGN_CENTER,
                            new Phrase(text, smallFont),
                            pageWidth / 2f,
                            y,
                            0);
                }
            }
        }

        drawWatermark(writer, pageWidth, pageHeight);
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
        if (totalPagesTemplate == null) {
            return;
        }
        Font smallFont = fontFactory.bodyFont(FOOTER_FONT_SIZE, Font.NORMAL);
        // document.close 触发时 getPageNumber 即为总页数
        String total = String.valueOf(writer.getPageNumber());
        ColumnText.showTextAligned(
                totalPagesTemplate,
                Element.ALIGN_LEFT,
                new Phrase(total, smallFont),
                0,
                1,
                0);
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
                new Phrase(wm.getText(), wmFont),
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
        String documentTypeName = "";
        if (options.getDocumentTypeName() != null) {
            documentTypeName = options.getDocumentTypeName();
        }
        String withPage = template
                .replace("{page}", String.valueOf(page))
                .replace("{patientName}", meta.getOrDefault("patientName", ""))
                .replace("{gender}", meta.getOrDefault("gender", ""))
                .replace("{age}", meta.getOrDefault("age", ""))
                .replace("{documentTitle}", documentTitle)
                .replace("{documentTypeName}", documentTypeName)
                .replace("{orgName}", meta.getOrDefault("orgName", ""))
                .replace("{deptName}", meta.getOrDefault("deptName", ""))
                .replace("{printTime}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        if (totalReplacement != null) {
            return withPage.replace("{total}", totalReplacement);
        }
        return withPage;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
