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

package com.openhis.whale.emr.application.pdf.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 打印版式配置（与《打印预览设计》§4.1 对齐）。
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
        c.paper = paper != null ? paper.copy() : PaperConfig.a4Portrait();
        c.margins = margins != null ? margins.copy() : MarginConfig.printStandard();
        c.header = header != null ? header.copy() : HeaderConfig.disabled();
        c.footer = footer != null ? footer.copy() : FooterConfig.standard();
        c.watermark = watermark != null ? watermark.copy() : WatermarkConfig.disabled();
        c.font = font != null ? font.copy() : FontConfig.defaults();
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

        public static MarginConfig printStandard() {
            return new MarginConfig();
        }

        public MarginConfig copy() {
            return new MarginConfig()
                    .setTopMm(topMm)
                    .setRightMm(rightMm)
                    .setBottomMm(bottomMm)
                    .setLeftMm(leftMm);
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
        private String template = "第 {page} 页";
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
