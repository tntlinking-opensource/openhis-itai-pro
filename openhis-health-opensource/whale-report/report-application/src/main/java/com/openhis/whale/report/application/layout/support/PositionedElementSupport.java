package com.openhis.whale.report.application.layout.support;
import com.openhis.whale.report.application.layout.model.LayoutPage;
import com.openhis.whale.report.application.layout.model.LayoutItem;
import com.openhis.whale.report.application.layout.model.LayoutBoxDelta;
import com.openhis.whale.report.application.layout.model.LayoutBox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.report.domain.entity.PrintElementDef;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 绝对定位元素（qrcode/barcode/image）的 box 解析与可见性判断。
 */
public final class PositionedElementSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> POSITIONED_TYPES = Set.of("qrcode", "barcode", "image");

    private PositionedElementSupport() {
    }

    public static boolean isPositionedType(String renderType) {
        return renderType != null && POSITIONED_TYPES.contains(renderType.trim().toLowerCase());
    }

    /** qrcode / barcode / image 始终走绝对坐标层，不参与 flow 排版 */
    public static boolean isAbsoluteLayerElement(PrintElementDef def) {
        return def != null && isPositionedType(def.getRenderType());
    }

    public static boolean isVisible(LayoutItem item) {
        if (item == null) {
            return true;
        }
        if (item.getVisible() == null) {
            return true;
        }
        return Boolean.TRUE.equals(item.getVisible());
    }

    public static boolean hasBoxGeometry(LayoutBox box) {
        return box != null
                && box.getXMm() != null
                && box.getYMm() != null
                && box.getWidthMm() != null
                && box.getHeightMm() != null
                && box.getWidthMm() > 0
                && box.getHeightMm() > 0;
    }

    public static boolean hasResolvableBox(LayoutItem item, PrintElementDef def) {
        return hasBoxGeometry(resolveBaseBox(item, def));
    }

    public static boolean usesAbsoluteBox(LayoutItem item, PrintElementDef def) {
        return isAbsoluteLayerElement(def) && hasResolvableBox(item, def);
    }

    public static LayoutBox resolveBaseBox(LayoutItem item, PrintElementDef def) {
        if (item != null && hasBoxGeometry(item.getBox())) {
            return copyBox(item.getBox());
        }
        String boxJson = null;
        if (def != null) {
            boxJson = def.getBoxJson();
        }
        LayoutBox fromDef = parseBoxJson(boxJson);
        if (hasBoxGeometry(fromDef)) {
            return copyBox(fromDef);
        }
        return defaultBoxFor(def);
    }

    /**
     * 合并 item.box、boxDelta 与 page.overlay.printOffsetMm 得到有效绝对坐标框。
     *
     * @param item  layout 项
     * @param def   元素定义（缺省 box 兜底）
     * @param page  页级 overlay 偏移
     * @return 有效 box；无法解析几何时为 null
     */
    public static LayoutBox resolveEffectiveBox(
            LayoutItem item,
            PrintElementDef def,
            LayoutPage page) {
        LayoutBox base = resolveBaseBox(item, def);
        if (!hasBoxGeometry(base)) {
            return null;
        }
        float dx = 0f;
        float dy = 0f;
        LayoutBoxDelta delta = null;
        if (item != null) {
            delta = item.getBoxDelta();
        }
        if (delta != null) {
            if (delta.getDxMm() != null) {
                dx = delta.getDxMm();
            }
            if (delta.getDyMm() != null) {
                dy = delta.getDyMm();
            }
        }
        float ox = 0f;
        float oy = 0f;
        if (page != null && page.getOverlay() != null && page.getOverlay().getPrintOffsetMm() != null) {
            var offset = page.getOverlay().getPrintOffsetMm();
            if (offset.size() >= 2) {
                if (offset.get(0) != null) {
                    ox = offset.get(0);
                }
                if (offset.get(1) != null) {
                    oy = offset.get(1);
                }
            }
        }
        LayoutBox effective = copyBox(base);
        effective.setXMm(nvl(base.getXMm()) + dx + ox);
        effective.setYMm(nvl(base.getYMm()) + dy + oy);
        return effective;
    }

    private static float nvl(Float value) {
        if (value != null) {
            return value;
        }
        return 0f;
    }

    /** 元素目录与 layout 均无 box 时的最后兜底（优先 box_json） */
    private static LayoutBox defaultBoxFor(PrintElementDef def) {
        if (def == null || !isPositionedType(def.getRenderType())) {
            return null;
        }
        if ("qrcode".equalsIgnoreCase(def.getRenderType())) {
            LayoutBox box = new LayoutBox();
            box.setXMm(22f);
            box.setYMm(120f);
            box.setWidthMm(36f);
            box.setHeightMm(36f);
            return box;
        }
        if ("image".equalsIgnoreCase(def.getRenderType())) {
            LayoutBox box = new LayoutBox();
            box.setXMm(42f);
            box.setYMm(192f);
            box.setWidthMm(34f);
            box.setHeightMm(34f);
            return box;
        }
        return null;
    }

    private static LayoutBox copyBox(LayoutBox source) {
        LayoutBox copy = new LayoutBox();
        copy.setXMm(source.getXMm());
        copy.setYMm(source.getYMm());
        copy.setWidthMm(source.getWidthMm());
        copy.setHeightMm(source.getHeightMm());
        copy.setOverflow(source.getOverflow());
        copy.setRowHeightMm(source.getRowHeightMm());
        return copy;
    }

    private static LayoutBox parseBoxJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, LayoutBox.class);
        } catch (Exception e) {
            return null;
        }
    }
}
