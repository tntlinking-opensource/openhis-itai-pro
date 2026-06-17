package com.openhis.whale.report.application.layout.model;

import org.springframework.util.StringUtils;

/**
 * 纸型物理尺寸（mm），与 {@link com.openhis.whale.report.application.pdf.LayoutJsonPageRenderer} 一致。
 */
public final class LayoutPageSizeUtils {

    private static final float A4_WIDTH_MM = 210f;
    private static final float A4_HEIGHT_MM = 297f;
    private static final float A5_WIDTH_MM = 148f;
    private static final float A5_HEIGHT_MM = 210f;

    private LayoutPageSizeUtils() {
    }

    public record PageSizeMm(float widthMm, float heightMm) {
    }

    public static PageSizeMm resolvePageSizeMm(LayoutPage page) {
        if (page != null && isCustomPageSize(page)) {
            float widthMm = page.getWidthMm() != null && page.getWidthMm() > 0
                    ? page.getWidthMm()
                    : A4_WIDTH_MM;
            float heightMm = page.getHeightMm() != null && page.getHeightMm() > 0
                    ? page.getHeightMm()
                    : A4_HEIGHT_MM;
            return new PageSizeMm(widthMm, heightMm);
        }
        String size = "A4";
        if (page != null && StringUtils.hasText(page.getSize())) {
            size = page.getSize().trim();
        }
        boolean landscape = page != null && "landscape".equalsIgnoreCase(page.getOrientation());
        float widthMm;
        float heightMm;
        if ("A5".equalsIgnoreCase(size)) {
            widthMm = A5_WIDTH_MM;
            heightMm = A5_HEIGHT_MM;
        } else {
            widthMm = A4_WIDTH_MM;
            heightMm = A4_HEIGHT_MM;
        }
        if (landscape) {
            float tmp = widthMm;
            widthMm = heightMm;
            heightMm = tmp;
        }
        return new PageSizeMm(widthMm, heightMm);
    }

    /** page 管线自定义纸型：size=custom 且宽高均 &gt; 0。 */
    public static boolean isCustomPageSize(LayoutPage page) {
        if (page == null || !"custom".equalsIgnoreCase(page.getSize())) {
            return false;
        }
        return page.getWidthMm() != null
                && page.getWidthMm() > 0
                && page.getHeightMm() != null
                && page.getHeightMm() > 0;
    }
}
