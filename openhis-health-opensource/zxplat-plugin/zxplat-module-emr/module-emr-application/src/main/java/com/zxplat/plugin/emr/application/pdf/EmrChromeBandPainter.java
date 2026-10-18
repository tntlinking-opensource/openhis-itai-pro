/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.pdf;

import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 页眉/页脚带内富文本：{@code {{emrImg w=39:...}}}、{@code <span style="font-size:22pt">}
 * 与占位符。支持屏上同源的 {@code float:left}/{@code float:right} 患者信息行。
 */
@Slf4j
final class EmrChromeBandPainter {

    private static final float DEFAULT_TEXT_PT = 9f;
    private static final float LINE_GAP_PT = 2f;
    /** 屏上 .simple-editor__page-header padding-top:8px */
    private static final float HEADER_SHELL_TOP_PAD_PX = 8f;
    /** 屏上页眉 line-height:1.4 */
    private static final float LINE_HEIGHT_MULT = 1.4f;
    /** 院徽 float 与标题间距（px，与 emr-page-chrome CHROME_LOGO_TEXT_GAP_PX 一致） */
    private static final float LOGO_TEXT_GAP_PX = 8f;

    private static final Pattern IMG_TOKEN = Pattern.compile(
            "\\{\\{emrImg(?:\\s+w=(\\d+))?(?:\\s+x=(\\d+))?(?:\\s+y=(\\d+))?:([^}]+)\\}\\}");
    /** 捕获完整 style，以便保留 float / font-weight */
    private static final Pattern STYLED_SPAN = Pattern.compile(
            "<span\\s+[^>]*style=\"([^\"]*)\"[^>]*>([^<]*)</span>",
            Pattern.CASE_INSENSITIVE);

    private EmrChromeBandPainter() {
    }

    static void drawHeaderBand(
            PdfContentByte cb,
            float pageWidth,
            float pageHeight,
            float marginLeftPt,
            float marginRightPt,
            float bandMm,
            String resolvedTemplate,
            EmrPdfFontFactory fontFactory) {
        if (!StringUtils.hasText(resolvedTemplate)) {
            return;
        }
        float bandPt = PdfUnitUtils.mmToPt(Math.max(bandMm, 10f));
        float shellTopPadPt = pxToPt(HEADER_SHELL_TOP_PAD_PX);
        float rectLeft = marginLeftPt;
        float rectRight = pageWidth - marginRightPt;
        float innerTop = pageHeight - shellTopPadPt;
        float contentBottom = pageHeight - shellTopPadPt - bandPt;
        if (contentBottom >= innerTop) {
            contentBottom = innerTop - bandPt;
        }

        BaseFont baseFont = fontFactory.getBaseFont();
        List<String> lines = normalizeHeaderLines(resolvedTemplate);
        float areaTop = innerTop;
        float lineTop = areaTop;
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            List<Segment> segments = parseLine(line);
            if (segments.isEmpty()) {
                continue;
            }
            List<Segment> positionedLogos = collectPositionedLogos(segments);
            List<Segment> flowSegments = filterFlowSegments(segments);
            float lineHeight = measureLineHeight(segments, baseFont);
            float lineBottom = lineTop - lineHeight;
            if (lineBottom < contentBottom) {
                break;
            }
            float fontSizePt = defaultTextSizePt(flowSegments, baseFont);
            float baselineY = lineTop - resolveAscentPt(baseFont, fontSizePt);

            if (flowSegments.isEmpty()) {
                lineTop = lineBottom - LINE_GAP_PT;
                continue;
            }
            if (hasFloatLayout(flowSegments)) {
                drawFloatLine(cb, flowSegments, baseFont, rectLeft, rectRight, baselineY, lineTop);
            } else if (!positionedLogos.isEmpty()) {
                drawLogoCompanionLine(
                        cb,
                        flowSegments,
                        positionedLogos.get(0),
                        baseFont,
                        rectLeft,
                        rectRight,
                        baselineY,
                        lineTop);
            } else {
                float totalWidth = measureLineWidth(flowSegments, baseFont);
                float x = rectLeft + Math.max(0f, (rectRight - rectLeft - totalWidth) / 2f);
                drawSegments(cb, flowSegments, baseFont, x, baselineY, lineTop);
            }
            lineTop = lineBottom - LINE_GAP_PT;
        }
    }

    /** 行顶到基线：避免首行大字上升部超出纸顶被裁切 */
    private static float resolveAscentPt(BaseFont baseFont, float fontSizePt) {
        float size = fontSizePt > 0 ? fontSizePt : DEFAULT_TEXT_PT;
        float ascent = baseFont.getAscentPoint("中", size);
        if (ascent > 0) {
            return ascent;
        }
        return size * 0.85f;
    }

    private static float pxToPt(float px) {
        Float pt = PdfUnitUtils.parseCssLengthToPt(px + "px", DEFAULT_TEXT_PT);
        return pt != null ? pt : px * 72f / 96f;
    }

    private static float defaultTextSizePt(List<Segment> flowSegments, BaseFont baseFont) {
        float max = DEFAULT_TEXT_PT;
        for (Segment seg : flowSegments) {
            if (seg.clearOnly || !StringUtils.hasText(seg.text)) {
                continue;
            }
            max = Math.max(max, seg.fontSizePt > 0 ? seg.fontSizePt : DEFAULT_TEXT_PT);
        }
        return max;
    }

    private static List<Segment> collectPositionedLogos(List<Segment> segments) {
        List<Segment> out = new ArrayList<>();
        for (Segment seg : segments) {
            if (seg.image && seg.imagePositioned) {
                out.add(seg);
            }
        }
        return out;
    }

    /**
     * logo+标题整组居中；x/y 相对组左上角，标题 padding 仅按 logo 宽度预留。
     */
    private static void drawLogoCompanionLine(
            PdfContentByte cb,
            List<Segment> flowSegments,
            Segment logo,
            BaseFont baseFont,
            float rectLeft,
            float rectRight,
            float baselineY,
            float lineTop) {
        float logoW = measureImageWidthPt(logo);
        float padPt = logoW + pxToPt(LOGO_TEXT_GAP_PX);
        float textW = measureLineWidth(flowSegments, baseFont);
        float spanW = padPt + textW;
        float spanStart = rectLeft + Math.max(0f, (rectRight - rectLeft - spanW) / 2f);
        drawSegments(cb, flowSegments, baseFont, spanStart + padPt, baselineY, lineTop);
        drawPositionedImage(cb, logo, spanStart, lineTop);
    }

    private static float measureImageWidthPt(Segment seg) {
        if (seg.imageWidthPx > 0) {
            Float parsedW = PdfUnitUtils.parseCssLengthToPt(seg.imageWidthPx + "px", DEFAULT_TEXT_PT);
            if (parsedW != null && parsedW > 0) {
                return parsedW;
            }
        }
        Image img = loadImage(seg.imageSrc);
        if (img != null) {
            return img.getWidth();
        }
        return pxToPt(39f);
    }

    /** 屏上 x/y：相对 logo 组行顶；y 增大向下 */
    private static void drawPositionedImage(
            PdfContentByte cb, Segment seg, float anchorLeft, float lineTop) {
        Image img = loadImage(seg.imageSrc);
        if (img == null) {
            return;
        }
        float targetW = img.getWidth();
        if (seg.imageWidthPx > 0) {
            Float parsedW = PdfUnitUtils.parseCssLengthToPt(seg.imageWidthPx + "px", DEFAULT_TEXT_PT);
            if (parsedW != null && parsedW > 0) {
                targetW = parsedW;
            }
        }
        float scale = targetW / img.getWidth();
        float imgW = img.getWidth() * scale;
        float imgH = img.getHeight() * scale;
        Float xPt = PdfUnitUtils.parseCssLengthToPt(seg.imageOffsetXPx + "px", DEFAULT_TEXT_PT);
        Float yPt = PdfUnitUtils.parseCssLengthToPt(seg.imageOffsetYPx + "px", DEFAULT_TEXT_PT);
        float imgX = anchorLeft + (xPt != null ? xPt : 0f);
        float yDown = yPt != null ? yPt : 0f;
        float imgY = lineTop - imgH - yDown;
        img.scaleAbsolute(imgW, imgH);
        img.setAbsolutePosition(imgX, imgY);
        try {
            cb.addImage(img);
        } catch (Exception e) {
            log.warn("页眉图片绘制失败: {}", e.getMessage());
        }
    }

    /**
     * 标题行与 float 患者行粘在同一物理行时拆开，避免住院号画到标题右侧。
     */
    private static List<String> normalizeHeaderLines(String resolvedTemplate) {
        List<String> out = new ArrayList<>();
        for (String rawLine : resolvedTemplate.split("\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            int floatIdx = indexOfFloatSpan(line);
            if (floatIdx > 0 && isTitleOnlyHeaderPrefix(line.substring(0, floatIdx))) {
                String head = line.substring(0, floatIdx).trim();
                String tail = line.substring(floatIdx).trim();
                if (!head.isEmpty()) {
                    out.add(head);
                }
                if (!tail.isEmpty()) {
                    out.add(tail);
                }
                continue;
            }
            out.add(line);
        }
        return out;
    }

    private static int indexOfFloatSpan(String line) {
        Matcher m = Pattern.compile("<span\\b[^>]*\\bstyle=\"[^\"]*\\bfloat:\\s*(?:left|right)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(line);
        return m.find() ? m.start() : -1;
    }

    private static boolean isTitleOnlyHeaderPrefix(String prefix) {
        String head = prefix == null ? "" : prefix.trim();
        if (!StringUtils.hasText(head)) {
            return false;
        }
        if (head.contains("float:left") || head.contains("float:right")) {
            return false;
        }
        return head.contains("{documentTitle}")
                || head.contains("font-size:16pt")
                || head.contains("font-size: 16pt");
    }

    private static List<Segment> filterFlowSegments(List<Segment> segments) {
        List<Segment> out = new ArrayList<>();
        for (Segment seg : segments) {
            if (seg.image && seg.imagePositioned) {
                continue;
            }
            out.add(seg);
        }
        return out;
    }

    /**
     * 屏上 float 行：左组贴左边距，右组贴右边距（姓名… / …住院号）。
     */
    private static void drawFloatLine(
            PdfContentByte cb,
            List<Segment> segments,
            BaseFont baseFont,
            float rectLeft,
            float rectRight,
            float baselineY,
            float lineTop) {
        List<Segment> left = new ArrayList<>();
        List<Segment> right = new ArrayList<>();
        boolean onRight = false;
        for (Segment seg : segments) {
            if (seg.clearOnly) {
                continue;
            }
            if (seg.floatRight) {
                onRight = true;
            }
            if (onRight) {
                right.add(seg);
            } else {
                left.add(seg);
            }
        }
        drawSegments(cb, left, baseFont, rectLeft, baselineY, lineTop);
        float rightW = measureLineWidth(right, baseFont);
        float rightX = Math.max(rectLeft, rectRight - rightW);
        drawSegments(cb, right, baseFont, rightX, baselineY, lineTop);
    }

    private static void drawSegments(
            PdfContentByte cb,
            List<Segment> segments,
            BaseFont baseFont,
            float startX,
            float baselineY,
            float lineTop) {
        float x = startX;
        for (Segment seg : segments) {
            if (seg.clearOnly) {
                continue;
            }
            if (seg.image) {
                if (seg.imagePositioned) {
                    continue;
                }
                Image img = loadImage(seg.imageSrc);
                if (img != null) {
                    float targetW = img.getWidth();
                    if (seg.imageWidthPx > 0) {
                        Float parsedW =
                                PdfUnitUtils.parseCssLengthToPt(seg.imageWidthPx + "px", DEFAULT_TEXT_PT);
                        if (parsedW != null && parsedW > 0) {
                            targetW = parsedW;
                        }
                    }
                    float scale = targetW / img.getWidth();
                    float imgW = img.getWidth() * scale;
                    float imgH = img.getHeight() * scale;
                    float imgY = lineTop - imgH;
                    img.scaleAbsolute(imgW, imgH);
                    img.setAbsolutePosition(x, imgY);
                    try {
                        cb.addImage(img);
                    } catch (Exception e) {
                        log.warn("页眉图片绘制失败: {}", e.getMessage());
                    }
                    x += imgW + pxToPt(LOGO_TEXT_GAP_PX);
                }
            } else if (StringUtils.hasText(seg.text)) {
                float sizePt = seg.fontSizePt > 0 ? seg.fontSizePt : DEFAULT_TEXT_PT;
                cb.beginText();
                cb.setFontAndSize(baseFont, sizePt);
                cb.setTextMatrix(x, baselineY);
                cb.showText(seg.text);
                cb.endText();
                x += baseFont.getWidthPoint(seg.text, sizePt);
            }
        }
    }

    private static boolean hasFloatLayout(List<Segment> segments) {
        for (Segment seg : segments) {
            if (seg.floatLeft || seg.floatRight) {
                return true;
            }
        }
        return false;
    }

    private static List<Segment> parseLine(String line) {
        List<Segment> out = new ArrayList<>();
        int cursor = 0;
        while (cursor < line.length()) {
            Matcher imgM = IMG_TOKEN.matcher(line);
            imgM.region(cursor, line.length());
            Matcher spanM = STYLED_SPAN.matcher(line);
            spanM.region(cursor, line.length());
            int imgStart = imgM.find() ? imgM.start() : Integer.MAX_VALUE;
            int spanStart = spanM.find() ? spanM.start() : Integer.MAX_VALUE;
            int next = Math.min(imgStart, spanStart);
            if (next == Integer.MAX_VALUE) {
                appendPlainText(out, line.substring(cursor));
                break;
            }
            if (next > cursor) {
                appendPlainText(out, line.substring(cursor, next));
            }
            if (next == imgStart) {
                Segment seg = new Segment();
                seg.image = true;
                seg.imageWidthPx = parseIntOrZero(imgM.group(1));
                seg.imageOffsetXPx = imgM.group(2) != null ? parseIntOrZero(imgM.group(2)) : 0;
                seg.imageOffsetYPx = imgM.group(3) != null ? parseIntOrZero(imgM.group(3)) : 0;
                seg.imagePositioned = imgM.group(2) != null || imgM.group(3) != null;
                seg.imageSrc = imgM.group(4) != null ? imgM.group(4).trim() : "";
                out.add(seg);
                cursor = imgM.end();
            } else {
                String style = spanM.group(1) != null ? spanM.group(1) : "";
                String text = spanM.group(2) != null ? spanM.group(2).trim() : "";
                Segment seg = new Segment();
                Float pt = PdfUnitUtils.parseCssLengthToPt(extractCssValue(style, "font-size"), DEFAULT_TEXT_PT);
                seg.fontSizePt = pt != null ? pt : DEFAULT_TEXT_PT;
                seg.floatLeft = style.matches("(?i).*float:\\s*left.*");
                seg.floatRight = style.matches("(?i).*float:\\s*right.*");
                seg.clearOnly = style.matches("(?i).*clear:\\s*both.*") && !StringUtils.hasText(text);
                seg.text = text;
                if (seg.clearOnly || StringUtils.hasText(seg.text) || seg.floatLeft || seg.floatRight) {
                    out.add(seg);
                }
                cursor = spanM.end();
            }
        }
        return out;
    }

    private static String extractCssValue(String style, String prop) {
        Matcher m = Pattern.compile("(?i)" + Pattern.quote(prop) + ":\\s*([^;\"']+)").matcher(style);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static void appendPlainText(List<Segment> out, String raw) {
        String text = raw.replaceAll("<[^>]+>", "").trim();
        if (!StringUtils.hasText(text)) {
            return;
        }
        Segment seg = new Segment();
        seg.text = text;
        seg.fontSizePt = DEFAULT_TEXT_PT;
        out.add(seg);
    }

    private static float measureLineWidth(List<Segment> segments, BaseFont baseFont) {
        float w = 0f;
        for (Segment seg : segments) {
            if (seg.clearOnly) {
                continue;
            }
            if (seg.image) {
                if (seg.imagePositioned) {
                    continue;
                }
                float targetW = 24f;
                if (seg.imageWidthPx > 0) {
                    Float parsedW =
                            PdfUnitUtils.parseCssLengthToPt(seg.imageWidthPx + "px", DEFAULT_TEXT_PT);
                    if (parsedW != null && parsedW > 0) {
                        targetW = parsedW;
                    }
                }
                w += targetW + pxToPt(LOGO_TEXT_GAP_PX);
            } else if (StringUtils.hasText(seg.text)) {
                float sizePt = seg.fontSizePt > 0 ? seg.fontSizePt : DEFAULT_TEXT_PT;
                w += baseFont.getWidthPoint(seg.text, sizePt);
            }
        }
        return w;
    }

    private static float measureLineHeight(List<Segment> segments, BaseFont baseFont) {
        float maxH = DEFAULT_TEXT_PT * 1.2f;
        for (Segment seg : segments) {
            if (seg.clearOnly) {
                continue;
            }
            if (seg.image) {
                if (seg.imagePositioned) {
                    float imgH = measureImageHeightPt(seg);
                    float yPt = pxToPt(seg.imageOffsetYPx);
                    maxH = Math.max(maxH, yPt + imgH);
                    continue;
                }
                maxH = Math.max(maxH, measureImageHeightPt(seg));
            } else if (StringUtils.hasText(seg.text)) {
                float sizePt = seg.fontSizePt > 0 ? seg.fontSizePt : DEFAULT_TEXT_PT;
                maxH = Math.max(maxH, sizePt * LINE_HEIGHT_MULT);
            }
        }
        return maxH;
    }

    /** 按 w= 缩放后的真实图片高度（pt），避免行高过小导致次行文字与 logo 叠字 */
    private static float measureImageHeightPt(Segment seg) {
        Image img = loadImage(seg.imageSrc);
        if (img == null) {
            if (seg.imageWidthPx > 0) {
                Float wPt = PdfUnitUtils.parseCssLengthToPt(seg.imageWidthPx + "px", DEFAULT_TEXT_PT);
                if (wPt != null && wPt > 0) {
                    return wPt;
                }
            }
            return DEFAULT_TEXT_PT * 1.2f;
        }
        float targetW = img.getWidth();
        if (seg.imageWidthPx > 0) {
            Float parsedW = PdfUnitUtils.parseCssLengthToPt(seg.imageWidthPx + "px", DEFAULT_TEXT_PT);
            if (parsedW != null && parsedW > 0) {
                targetW = parsedW;
            }
        }
        float scale = targetW / img.getWidth();
        return img.getHeight() * scale;
    }

    private static Image loadImage(String src) {
        if (!StringUtils.hasText(src)) {
            return null;
        }
        String trimmed = src.trim();
        try {
            if (trimmed.startsWith("data:")) {
                int comma = trimmed.indexOf(',');
                if (comma > 0) {
                    byte[] bytes = Base64.getDecoder().decode(trimmed.substring(comma + 1));
                    return Image.getInstance(bytes);
                }
                return null;
            }
            return Image.getInstance(trimmed);
        } catch (Exception e) {
            log.warn("页眉图片加载失败: {}", e.getMessage());
            return null;
        }
    }

    private static int parseIntOrZero(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final class Segment {
        private boolean image;
        private String imageSrc = "";
        private int imageWidthPx;
        private int imageOffsetXPx;
        private int imageOffsetYPx;
        private boolean imagePositioned;
        private String text = "";
        private float fontSizePt;
        private boolean floatLeft;
        private boolean floatRight;
        private boolean clearOnly;
    }
}
