package com.openhis.whale.report.application.layout.support;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.report.application.layout.model.LayoutBox;
import com.openhis.whale.report.application.layout.model.LayoutDocument;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.model.LayoutPage;
import com.openhis.whale.report.application.layout.model.LayoutPageSizeUtils;
import com.openhis.whale.report.application.pdf.overlay.OverlayBoxResolver;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LayoutValidator {

    private static final float EPS = 0.05f;

    private final OverlayBoxResolver overlayBoxResolver;

    public LayoutValidator(OverlayBoxResolver overlayBoxResolver) {
        this.overlayBoxResolver = overlayBoxResolver;
    }

    /**
     * 校验 layoutJson 与元素包、套打 box 及发布可见性规则。
     *
     * @param document   已解析排版
     * @param packIndex  元素定义索引
     * @param forPublish 是否按发布标准（至少一项可见）
     * @return 错误信息列表，空表示通过
     */
    public List<String> validate(LayoutDocument document, Map<String, PrintElementDef> packIndex, boolean forPublish) {
        List<String> errors = new ArrayList<>();
        if (document == null) {
            errors.add("layoutJson 不能为空");
            return errors;
        }
        if (!StringUtils.hasText(document.getPipeline())) {
            errors.add("pipeline 不能为空");
        }
        List<LayoutItem> items = document.getItems();
        if (items == null || items.isEmpty()) {
            errors.add("items 至少包含一项排版元素");
            return errors;
        }
        boolean anyVisible = false;
        for (int i = 0; i < items.size(); i++) {
            LayoutItem item = items.get(i);
            if (item == null || !StringUtils.hasText(item.getElementCode())) {
                errors.add("items[" + i + "] 缺少 elementCode");
                continue;
            }
            String code = item.getElementCode().trim();
            PrintElementDef def = packIndex.get(code);
            if (def == null) {
                errors.add("未知元素: " + code);
                continue;
            }
            boolean visible = item.getVisible() == null || Boolean.TRUE.equals(item.getVisible());
            if (visible) {
                anyVisible = true;
            }
            if (Boolean.TRUE.equals(def.getRequired()) && !visible) {
                errors.add("必填元素不可隐藏: " + code);
            }
        }
        if (forPublish && !anyVisible) {
            errors.add("发布前至少保留一个可见排版项");
        }
        LayoutPage page = document.getPage();
        if (page != null && "custom".equalsIgnoreCase(page.getSize())) {
            if (page.getWidthMm() == null
                    || page.getWidthMm() <= 0
                    || page.getHeightMm() == null
                    || page.getHeightMm() <= 0) {
                errors.add("自定义纸型须填写有效的 widthMm 与 heightMm");
            }
        }
        if (page != null && "overlay".equalsIgnoreCase(page.getLayoutMode())) {
            LayoutPageSizeUtils.PageSizeMm pageSize = LayoutPageSizeUtils.resolvePageSizeMm(page);
            for (LayoutItem item : items) {
                if (item == null || !StringUtils.hasText(item.getElementCode())) {
                    continue;
                }
                if (item.getVisible() != null && !item.getVisible()) {
                    continue;
                }
                String code = item.getElementCode().trim();
                PrintElementDef def = packIndex.get(code);
                if (item.getBox() == null && (def == null || !StringUtils.hasText(def.getBoxJson()))) {
                    errors.add("套打元素缺少 box: " + code);
                    continue;
                }
                if (def == null) {
                    continue;
                }
                try {
                    LayoutBox effective = overlayBoxResolver.resolveEffectiveBox(item, def, page);
                    validateBoxWithinPage(code, effective, pageSize, errors);
                } catch (BusinessException ex) {
                    if (!errors.contains("套打元素缺少 box: " + code)) {
                        String message = "套打 box 无效: " + code;
                        if (ex.getMessage() != null) {
                            message = ex.getMessage();
                        }
                        errors.add(message);
                    }
                }
            }
        }
        return errors;
    }

    private static void validateBoxWithinPage(
            String elementCode,
            LayoutBox box,
            LayoutPageSizeUtils.PageSizeMm pageSize,
            List<String> errors) {
        float x = nvl(box.getXMm());
        float y = nvl(box.getYMm());
        float w = nvl(box.getWidthMm());
        float h = nvl(box.getHeightMm());
        if (w <= EPS || h <= EPS) {
            errors.add("套打 box 尺寸无效: " + elementCode);
            return;
        }
        if (x < -EPS || y < -EPS) {
            errors.add("套打 box 越界: " + elementCode + "（坐标不能为负）");
        }
        if (x + w > pageSize.widthMm() + EPS) {
            errors.add("套打 box 越界: " + elementCode + "（右边界超出 "
                    + formatMm(pageSize.widthMm()) + "mm）");
        }
        if (y + h > pageSize.heightMm() + EPS) {
            errors.add("套打 box 越界: " + elementCode + "（下边界超出 "
                    + formatMm(pageSize.heightMm()) + "mm）");
        }
    }

    private static float nvl(Float value) {
        if (value != null) {
            return value;
        }
        return 0f;
    }

    private static String formatMm(float value) {
        if (Math.abs(value - Math.round(value)) < EPS) {
            return String.valueOf(Math.round(value));
        }
        return String.format("%.1f", value);
    }
}
