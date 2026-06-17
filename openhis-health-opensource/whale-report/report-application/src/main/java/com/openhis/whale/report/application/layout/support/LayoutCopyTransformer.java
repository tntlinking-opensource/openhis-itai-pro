package com.openhis.whale.report.application.layout.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.report.application.layout.model.LayoutBox;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.model.LayoutPage;
import com.openhis.whale.report.application.layout.model.PageOverlayConfig;
import com.openhis.whale.report.application.layout.support.PositionedElementSupport;
import com.openhis.whale.report.domain.constants.ReportPipelineConstants;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复制模板时按目标 layoutMode / pipeline 改写 layoutJson。
 */
@Component
public class LayoutCopyTransformer {

    private final ObjectMapper objectMapper;

    public LayoutCopyTransformer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LayoutDocument transform(
            LayoutDocument source,
            String targetLayoutMode,
            String targetPipeline,
            List<PrintElementDef> targetPackOrdered) {
        if (source == null) {
            throw new BusinessException("RPT_LAYOUT_EMPTY", "源 layoutJson 不能为空");
        }
        String mode = normalizeLayoutMode(targetLayoutMode);
        String pipeline = normalizePipeline(targetPipeline, source);
        LayoutDocument target = deepCopy(source);
        target.setPipeline(pipeline);
        LayoutPage page = target.getPage();
        if (page == null) {
            page = new LayoutPage();
            target.setPage(page);
        }
        page.setLayoutMode(mode);
        applyPipelinePage(page, pipeline, source.getPage());
        applyLayoutModePage(page, mode);
        Map<String, LayoutItem> sourceItems = indexSourceItems(source.getItems());
        List<LayoutItem> rebuilt = new ArrayList<>();
        for (PrintElementDef def : targetPackOrdered) {
            if (def == null || !StringUtils.hasText(def.getElementCode())) {
                continue;
            }
            String code = def.getElementCode().trim();
            LayoutItem sourceItem = resolveSourceItem(sourceItems, code);
            if (sourceItem != null) {
                rebuilt.add(adaptItem(deepCopyItem(sourceItem), def, mode));
            } else if (Boolean.TRUE.equals(def.getRequired())) {
                rebuilt.add(createRequiredItem(def, mode));
            }
        }
        if (rebuilt.isEmpty()) {
            throw new BusinessException("RPT_LAYOUT_ITEMS_EMPTY", "目标模式下无可用排版项");
        }
        target.setItems(rebuilt);
        return target;
    }

    private void applyPipelinePage(LayoutPage page, String pipeline, LayoutPage sourcePage) {
        if (ReportPipelineConstants.PIPE_ROLL.equals(pipeline)) {
            Float width = null;
            if (sourcePage != null && sourcePage.getWidthMm() != null && sourcePage.getWidthMm() > 0) {
                width = sourcePage.getWidthMm();
            }
            if (width == null) {
                width = 80f;
            }
            page.setWidthMm(width);
            if (page.getMarginMm() == null || page.getMarginMm().isEmpty()) {
                page.setMarginMm(List.of(2f, 2f, 2f, 2f));
            }
            return;
        }
        page.setWidthMm(null);
        if (!StringUtils.hasText(page.getSize())) {
            page.setSize("A4");
        }
        if (!StringUtils.hasText(page.getOrientation())) {
            page.setOrientation("portrait");
        }
        if (page.getMarginMm() == null || page.getMarginMm().isEmpty()) {
            page.setMarginMm(List.of(12f, 10f, 12f, 10f));
        }
    }

    private void applyLayoutModePage(LayoutPage page, String mode) {
        if (ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equals(mode)) {
            page.setMarginMm(List.of(0f, 0f, 0f, 0f));
            if (page.getOverlay() == null) {
                page.setOverlay(new PageOverlayConfig());
            }
            return;
        }
        if (page.getMarginMm() == null || isZeroMargins(page.getMarginMm())) {
            page.setMarginMm(List.of(12f, 10f, 12f, 10f));
        }
    }

    private LayoutItem adaptItem(LayoutItem item, PrintElementDef def, String mode) {
        if (ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equals(mode)) {
            if (item.getBox() == null || !hasBoxGeometry(item.getBox())) {
                LayoutBox fromDef = parseBoxJson(def.getBoxJson());
                if (fromDef != null) {
                    item.setBox(fromDef);
                }
            }
            return item;
        }
        // flow：qrcode 等绝对层保留 box；普通流式元素清 box
        if (PositionedElementSupport.isAbsoluteLayerElement(def)) {
            if (item.getBox() == null || !hasBoxGeometry(item.getBox())) {
                LayoutBox fromDef = parseBoxJson(def.getBoxJson());
                if (fromDef != null) {
                    item.setBox(fromDef);
                }
            }
            return item;
        }
        item.setBox(null);
        item.setBoxDelta(null);
        return item;
    }

    private LayoutItem createRequiredItem(PrintElementDef def, String mode) {
        LayoutItem item = new LayoutItem();
        item.setElementCode(def.getElementCode());
        item.setVisible(true);
        if (ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equals(mode)) {
            LayoutBox fromDef = parseBoxJson(def.getBoxJson());
            if (fromDef != null) {
                item.setBox(fromDef);
            }
        }
        return item;
    }

    private Map<String, LayoutItem> indexSourceItems(List<LayoutItem> items) {
        Map<String, LayoutItem> map = new LinkedHashMap<>();
        if (items == null) {
            return map;
        }
        for (LayoutItem item : items) {
            if (item == null || !StringUtils.hasText(item.getElementCode())) {
                continue;
            }
            map.putIfAbsent(item.getElementCode().trim(), item);
        }
        return map;
    }

    /**
     * flow stub 复制为 overlay stub 时，{@code docTitle} 可映射到 {@code overlayField}。
     */
    private static LayoutItem resolveSourceItem(Map<String, LayoutItem> sourceItems, String targetCode) {
        LayoutItem direct = sourceItems.get(targetCode);
        if (direct != null) {
            return direct;
        }
        if ("overlayField".equals(targetCode)) {
            LayoutItem title = sourceItems.get("docTitle");
            if (title != null) {
                return title;
            }
        }
        return null;
    }

    private LayoutDocument deepCopy(LayoutDocument source) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(source), LayoutDocument.class);
        } catch (Exception e) {
            throw new BusinessException("RPT_LAYOUT_COPY_FAILED", "layoutJson 复制失败: " + e.getMessage(), e);
        }
    }

    private LayoutItem deepCopyItem(LayoutItem item) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(item), LayoutItem.class);
        } catch (Exception e) {
            throw new BusinessException("RPT_LAYOUT_COPY_FAILED", "排版项复制失败: " + e.getMessage(), e);
        }
    }

    private LayoutBox parseBoxJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            LayoutBox box = objectMapper.readValue(json, LayoutBox.class);
            if (hasBoxGeometry(box)) {
                return box;
            }
        } catch (Exception ignored) {
            // 无默认 box 时由后续校验提示
        }
        return null;
    }

    private static boolean hasBoxGeometry(LayoutBox box) {
        return box.getXMm() != null && box.getYMm() != null
                && box.getWidthMm() != null && box.getHeightMm() != null;
    }

    private static boolean isZeroMargins(List<Float> marginMm) {
        if (marginMm == null || marginMm.isEmpty()) {
            return true;
        }
        for (Float value : marginMm) {
            if (value != null && Math.abs(value) > 0.001f) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeLayoutMode(String layoutMode) {
        if (!StringUtils.hasText(layoutMode)) {
            return ReportPipelineConstants.LAYOUT_MODE_FLOW;
        }
        String trimmed = layoutMode.trim();
        if (ReportPipelineConstants.LAYOUT_MODE_FLOW.equalsIgnoreCase(trimmed)
                || ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equalsIgnoreCase(trimmed)) {
            return trimmed.toLowerCase();
        }
        throw new BusinessException("RPT_LAYOUT_MODE_INVALID", "layoutMode 仅支持 flow 或 overlay");
    }

    private static String normalizePipeline(String targetPipeline, LayoutDocument source) {
        if (StringUtils.hasText(targetPipeline)) {
            String trimmed = targetPipeline.trim();
            if (ReportPipelineConstants.PIPE_PAGE.equalsIgnoreCase(trimmed)
                    || ReportPipelineConstants.PIPE_ROLL.equalsIgnoreCase(trimmed)) {
                return trimmed.toLowerCase();
            }
            throw new BusinessException("RPT_PIPELINE_INVALID", "pipeline 仅支持 page 或 roll");
        }
        if (source != null && StringUtils.hasText(source.getPipeline())) {
            return source.getPipeline().trim();
        }
        return ReportPipelineConstants.PIPE_PAGE;
    }
}
