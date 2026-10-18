/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.pdf;

/**
 * 毫米 / CSS 长度与 PDF 点（pt）换算。
 */
public final class PdfUnitUtils {

    private static final float MM_TO_PT = 72f / 25.4f;
    /** CSS px @96dpi → pt @72dpi */
    private static final float PX_TO_PT = 72f / 96f;

    private PdfUnitUtils() {
    }

    public static float mmToPt(float mm) {
        return mm * MM_TO_PT;
    }

    /**
     * 解析 CSS 长度到 pt：支持 pt/mm/cm/em/px，以及纯数字（按 pt）。
     *
     * @param css         如 {@code 12pt}、{@code 2em}、{@code 1cm}、{@code 0.25em}
     * @param emBasePt    {@code em} 相对基准（通常为当前段正文字号）
     * @return 解析失败返回 {@code null}
     */
    public static Float parseCssLengthToPt(String css, float emBasePt) {
        if (css == null) {
            return null;
        }
        String s = css.trim().toLowerCase();
        if (s.isEmpty() || "null".equals(s) || "normal".equals(s) || "auto".equals(s)) {
            return null;
        }
        try {
            if (s.endsWith("pt")) {
                return Float.parseFloat(s.substring(0, s.length() - 2).trim());
            }
            if (s.endsWith("mm")) {
                return mmToPt(Float.parseFloat(s.substring(0, s.length() - 2).trim()));
            }
            if (s.endsWith("cm")) {
                return mmToPt(Float.parseFloat(s.substring(0, s.length() - 2).trim()) * 10f);
            }
            if (s.endsWith("em")) {
                return emBasePt * Float.parseFloat(s.substring(0, s.length() - 2).trim());
            }
            if (s.endsWith("px")) {
                return Float.parseFloat(s.substring(0, s.length() - 2).trim()) * PX_TO_PT;
            }
            if (s.matches("^[+-]?[0-9]*\\.?[0-9]+$")) {
                return Float.parseFloat(s);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }
}
