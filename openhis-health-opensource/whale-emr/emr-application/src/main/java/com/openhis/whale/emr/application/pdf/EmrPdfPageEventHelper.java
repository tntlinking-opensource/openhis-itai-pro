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

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
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

    private final PrintConfig config;
    private final PdfRenderOptions options;
    private final EmrPdfFontFactory fontFactory;

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        int page = writer.getPageNumber();
        float pageWidth = document.getPageSize().getWidth();
        float pageHeight = document.getPageSize().getHeight();
        Font smallFont = fontFactory.bodyFont(9f, Font.NORMAL);

        if (config.getHeader().isEnabled() && hasText(config.getHeader().getTemplate())) {
            String text = resolveTemplate(config.getHeader().getTemplate(), page);
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
                template = "第 {page} 页";
            }
            if (hasText(template)) {
                String text = resolveTemplate(template, page);
                ColumnText.showTextAligned(
                        writer.getDirectContent(),
                        Element.ALIGN_CENTER,
                        new Phrase(text, smallFont),
                        pageWidth / 2f,
                        PdfUnitUtils.mmToPt(Math.max(config.getFooter().getHeightMm(), 8f)),
                        0);
            }
        }

        drawWatermark(writer, pageWidth, pageHeight);
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

    private String resolveTemplate(String template, int page) {
        Map<String, String> meta = options.safeMetadata();
        return template
                .replace("{page}", String.valueOf(page))
                .replace("{total}", "?")
                .replace("{patientName}", meta.getOrDefault("patientName", ""))
                .replace("{gender}", meta.getOrDefault("gender", ""))
                .replace("{age}", meta.getOrDefault("age", ""))
                .replace("{documentTitle}", options.getDocumentTitle() != null ? options.getDocumentTitle() : "")
                .replace("{documentTypeName}", options.getDocumentTypeName() != null ? options.getDocumentTypeName() : "")
                .replace("{orgName}", meta.getOrDefault("orgName", ""))
                .replace("{deptName}", meta.getOrDefault("deptName", ""))
                .replace("{printTime}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
