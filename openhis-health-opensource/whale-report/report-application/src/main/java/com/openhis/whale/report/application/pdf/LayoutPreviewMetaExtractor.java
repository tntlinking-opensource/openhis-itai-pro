package com.openhis.whale.report.application.pdf;

import com.openhis.whale.report.application.template.response.PreviewLayoutMetaDto;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutPage;
import com.openhis.whale.report.application.layout.model.LayoutPageSizeUtils;
import com.openhis.whale.report.application.layout.model.PageOverlayConfig;
import com.openhis.whale.report.domain.constants.ReportPipelineConstants;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从已解析的 LayoutDocument 提取预览叠图元数据（禁止把 layoutJson 字符串透出 API）。
 */
@Component
public class LayoutPreviewMetaExtractor {

    /**
     * 提取设计器预览叠图所需的纸型与套打信息。
     *
     * @param document 已解析排版
     * @return 结构化元数据
     */
    public PreviewLayoutMetaDto extract(LayoutDocument document) {
        PreviewLayoutMetaDto meta = new PreviewLayoutMetaDto();
        if (document == null || document.getPage() == null) {
            meta.setLayoutMode(ReportPipelineConstants.LAYOUT_MODE_FLOW);
            meta.setPageSize("A4");
            meta.setOrientation("portrait");
            return meta;
        }
        LayoutPage page = document.getPage();
        if (StringUtils.hasText(page.getLayoutMode())) {
            meta.setLayoutMode(page.getLayoutMode().trim());
        } else {
            meta.setLayoutMode(ReportPipelineConstants.LAYOUT_MODE_FLOW);
        }
        if (ReportPipelineConstants.PIPE_ROLL.equalsIgnoreCase(document.getPipeline())) {
            float widthMm = ReportPipelineConstants.DEFAULT_ROLL_WIDTH_MM;
            if (page.getWidthMm() != null && page.getWidthMm() > 0) {
                widthMm = page.getWidthMm();
            }
            meta.setPageSize(String.format("%.0fmm", widthMm));
            meta.setOrientation("portrait");
        } else {
            meta.setPageSize(resolvePageSizeLabel(page));
            meta.setOrientation(resolveOrientation(page));
        }
        PageOverlayConfig overlay = page.getOverlay();
        if (overlay != null) {
            meta.setGuideImageUrl(trimOrNull(overlay.getGuideImageUrl()));
        }
        return meta;
    }

    private static String resolvePageSizeLabel(LayoutPage page) {
        if (LayoutPageSizeUtils.isCustomPageSize(page)) {
            return String.format("%.0f×%.0fmm", page.getWidthMm(), page.getHeightMm());
        }
        if (StringUtils.hasText(page.getSize())) {
            return page.getSize().trim();
        }
        return "A4";
    }

    private static String resolveOrientation(LayoutPage page) {
        if (StringUtils.hasText(page.getOrientation())) {
            return page.getOrientation().trim();
        }
        return "portrait";
    }

    private static String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
