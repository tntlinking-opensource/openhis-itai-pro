/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.pdf.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 打印版式配置（与《打印预览设计》§4.1 对齐）。
 * <p>
 * 默认值落实《病历书写基本规范》第三十二条「统一纸张、字体、字号及排版格式」：
 * A4；页边距上/下 25.4mm、左/右 31.8mm；宋体；正文 12pt（小四）；标题 16pt；行距 1.5。
 * </p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class PrintConfig {

    private PaperConfig paper = PaperConfig.a4Portrait();
    private MarginConfig margins = MarginConfig.printStandard();
    private HeaderConfig header = HeaderConfig.disabled();
    private FooterConfig footer = FooterConfig.standard();
    private WatermarkConfig watermark = WatermarkConfig.disabled();
    private FontConfig font = FontConfig.defaults();
    private String dateFormat = "yyyy-MM-dd";
    private String datetimeFormat = "yyyy-MM-dd HH:mm";
    private boolean showDeletedNodes;
    private String blankPagePolicy = "hide";

    public PrintConfig copy() {
        PrintConfig c = new PrintConfig();
        if (paper != null) {
            c.paper = paper.copy();
        } else {
            c.paper = PaperConfig.a4Portrait();
        }
        if (margins != null) {
            c.margins = margins.copy();
        } else {
            c.margins = MarginConfig.printStandard();
        }
        if (header != null) {
            c.header = header.copy();
        } else {
            c.header = HeaderConfig.disabled();
        }
        if (footer != null) {
            c.footer = footer.copy();
        } else {
            c.footer = FooterConfig.standard();
        }
        if (watermark != null) {
            c.watermark = watermark.copy();
        } else {
            c.watermark = WatermarkConfig.disabled();
        }
        if (font != null) {
            c.font = font.copy();
        } else {
            c.font = FontConfig.defaults();
        }
        c.dateFormat = dateFormat;
        c.datetimeFormat = datetimeFormat;
        c.showDeletedNodes = showDeletedNodes;
        c.blankPagePolicy = blankPagePolicy;
        return c;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class PaperConfig {
        private float widthMm = 210f;
        private float heightMm = 297f;
        private String orientation = "portrait";

        public static PaperConfig a4Portrait() {
            return new PaperConfig();
        }

        public PaperConfig copy() {
            return new PaperConfig().setWidthMm(widthMm).setHeightMm(heightMm).setOrientation(orientation);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class MarginConfig {
        private float topMm = 25.4f;
        private float rightMm = 31.8f;
        private float bottomMm = 25.4f;
        private float leftMm = 31.8f;
        /** 屏上 ProseMirror padding 实测：正文顶边距（mm），优先于 topMm+header */
        private float contentTopMm;
        /** 屏上 ProseMirror padding 实测：正文底边距（mm），优先于 bottomMm+footer */
        private float contentBottomMm;

        public static MarginConfig printStandard() {
            return new MarginConfig();
        }

        public MarginConfig copy() {
            return new MarginConfig()
                    .setTopMm(topMm)
                    .setRightMm(rightMm)
                    .setBottomMm(bottomMm)
                    .setLeftMm(leftMm)
                    .setContentTopMm(contentTopMm)
                    .setContentBottomMm(contentBottomMm);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class HeaderConfig {
        private boolean enabled;
        private float heightMm = 15f;
        private String template = "";
        private boolean firstPageDifferent;

        public static HeaderConfig disabled() {
            return new HeaderConfig();
        }

        public HeaderConfig copy() {
            return new HeaderConfig()
                    .setEnabled(enabled)
                    .setHeightMm(heightMm)
                    .setTemplate(template)
                    .setFirstPageDifferent(firstPageDifferent);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class FooterConfig {
        private boolean enabled = true;
        private float heightMm = 12f;
        private String template = "第 {page} 页 (共 {total} 页)";
        private boolean showPageNumber = true;

        public static FooterConfig standard() {
            return new FooterConfig();
        }

        public FooterConfig copy() {
            return new FooterConfig()
                    .setEnabled(enabled)
                    .setHeightMm(heightMm)
                    .setTemplate(template)
                    .setShowPageNumber(showPageNumber);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class WatermarkConfig {
        private boolean enabled;
        private String text = "";
        private float opacity = 0.15f;
        private float angle = 45f;
        private float fontSizePt = 48f;

        public static WatermarkConfig disabled() {
            return new WatermarkConfig();
        }

        public WatermarkConfig copy() {
            return new WatermarkConfig()
                    .setEnabled(enabled)
                    .setText(text)
                    .setOpacity(opacity)
                    .setAngle(angle)
                    .setFontSizePt(fontSizePt);
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class FontConfig {
        private String family = "SimSun";
        private float defaultSizePt = 12f;
        private float titleSizePt = 16f;
        private float lineHeight = 1.5f;

        public static FontConfig defaults() {
            return new FontConfig();
        }

        public FontConfig copy() {
            return new FontConfig()
                    .setFamily(family)
                    .setDefaultSizePt(defaultSizePt)
                    .setTitleSizePt(titleSizePt)
                    .setLineHeight(lineHeight);
        }
    }
}
