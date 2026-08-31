/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.pdf;

import com.openhis.whale.emr.application.pdf.config.PrintConfig;
import com.openhis.whale.emr.application.document.request.GeneratePdfRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 合并系统默认与请求级打印配置。
 */
@Component
public class PrintConfigResolver {

    public PrintConfig resolve(GeneratePdfRequest request) {
        PrintConfig config = defaultConfig();
        if (request == null) {
            return config;
        }
        if (request.getPrintConfig() != null) {
            merge(config, request.getPrintConfig());
        }
        if (StringUtils.hasText(request.getWatermark())) {
            config.getWatermark().setEnabled(true).setText(request.getWatermark());
        }
        if (StringUtils.hasText(request.getHeaderTemplate())) {
            config.getHeader().setEnabled(true).setTemplate(request.getHeaderTemplate());
        }
        if (StringUtils.hasText(request.getFooterTemplate())) {
            config.getFooter().setEnabled(true).setTemplate(request.getFooterTemplate());
        }
        if (Boolean.TRUE.equals(request.getPreviewMode())) {
            if (config.getWatermark().isEnabled() && !StringUtils.hasText(config.getWatermark().getText())) {
                config.getWatermark().setEnabled(false);
            }
        }
        return config;
    }

    public PrintConfig defaultConfig() {
        return new PrintConfig();
    }

    private static void merge(PrintConfig target, PrintConfig patch) {
        if (patch.getPaper() != null) {
            mergePaper(target.getPaper(), patch.getPaper());
        }
        if (patch.getMargins() != null) {
            mergeMargins(target.getMargins(), patch.getMargins());
        }
        if (patch.getHeader() != null) {
            mergeHeader(target.getHeader(), patch.getHeader());
        }
        if (patch.getFooter() != null) {
            mergeFooter(target.getFooter(), patch.getFooter());
        }
        if (patch.getWatermark() != null) {
            mergeWatermark(target.getWatermark(), patch.getWatermark());
        }
        if (patch.getFont() != null) {
            mergeFont(target.getFont(), patch.getFont());
        }
        if (patch.getDateFormat() != null) {
            target.setDateFormat(patch.getDateFormat());
        }
        if (patch.getDatetimeFormat() != null) {
            target.setDatetimeFormat(patch.getDatetimeFormat());
        }
        target.setShowDeletedNodes(patch.isShowDeletedNodes());
        if (patch.getBlankPagePolicy() != null) {
            target.setBlankPagePolicy(patch.getBlankPagePolicy());
        }
    }

    private static void mergePaper(PrintConfig.PaperConfig t, PrintConfig.PaperConfig p) {
        if (p.getWidthMm() > 0) {
            t.setWidthMm(p.getWidthMm());
        }
        if (p.getHeightMm() > 0) {
            t.setHeightMm(p.getHeightMm());
        }
        if (StringUtils.hasText(p.getOrientation())) {
            t.setOrientation(p.getOrientation());
        }
    }

    private static void mergeMargins(PrintConfig.MarginConfig t, PrintConfig.MarginConfig p) {
        if (p.getTopMm() > 0) {
            t.setTopMm(p.getTopMm());
        }
        if (p.getRightMm() > 0) {
            t.setRightMm(p.getRightMm());
        }
        if (p.getBottomMm() > 0) {
            t.setBottomMm(p.getBottomMm());
        }
        if (p.getLeftMm() > 0) {
            t.setLeftMm(p.getLeftMm());
        }
        if (p.getContentTopMm() > 0) {
            t.setContentTopMm(p.getContentTopMm());
        }
        if (p.getContentBottomMm() > 0) {
            t.setContentBottomMm(p.getContentBottomMm());
        }
    }

    private static void mergeHeader(PrintConfig.HeaderConfig t, PrintConfig.HeaderConfig p) {
        t.setEnabled(p.isEnabled());
        if (p.getHeightMm() > 0) {
            t.setHeightMm(p.getHeightMm());
        }
        if (p.getTemplate() != null) {
            t.setTemplate(p.getTemplate());
        }
        t.setFirstPageDifferent(p.isFirstPageDifferent());
    }

    private static void mergeFooter(PrintConfig.FooterConfig t, PrintConfig.FooterConfig p) {
        t.setEnabled(p.isEnabled());
        if (p.getHeightMm() > 0) {
            t.setHeightMm(p.getHeightMm());
        }
        if (p.getTemplate() != null) {
            t.setTemplate(p.getTemplate());
        }
        t.setShowPageNumber(p.isShowPageNumber());
    }

    private static void mergeWatermark(PrintConfig.WatermarkConfig t, PrintConfig.WatermarkConfig p) {
        t.setEnabled(p.isEnabled());
        if (p.getText() != null) {
            t.setText(p.getText());
        }
        if (p.getOpacity() > 0) {
            t.setOpacity(p.getOpacity());
        }
        if (p.getAngle() != 0) {
            t.setAngle(p.getAngle());
        }
        if (p.getFontSizePt() > 0) {
            t.setFontSizePt(p.getFontSizePt());
        }
    }

    private static void mergeFont(PrintConfig.FontConfig t, PrintConfig.FontConfig p) {
        if (StringUtils.hasText(p.getFamily())) {
            t.setFamily(p.getFamily());
        }
        if (p.getDefaultSizePt() > 0) {
            t.setDefaultSizePt(p.getDefaultSizePt());
        }
        if (p.getTitleSizePt() > 0) {
            t.setTitleSizePt(p.getTitleSizePt());
        }
        if (p.getLineHeight() > 0) {
            t.setLineHeight(p.getLineHeight());
        }
    }
}
