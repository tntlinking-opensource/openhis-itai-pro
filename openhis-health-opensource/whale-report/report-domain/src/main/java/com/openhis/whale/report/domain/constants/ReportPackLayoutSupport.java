package com.openhis.whale.report.domain.constants;

import org.springframework.util.StringUtils;

/**
 * 复制/切换排版模式时，stub 元素包与 layoutMode 的映射。
 */
public final class ReportPackLayoutSupport {

    private ReportPackLayoutSupport() {
    }

    /**
     * 按目标排版模式解析实际元素包编码。
     * <p>例如 flow 骨架 {@code print_page_stub} 复制为 overlay 时使用 {@code print_page_overlay_stub}。</p>
     */
    public static String resolvePackForLayoutMode(String packCode, String targetLayoutMode) {
        if (!StringUtils.hasText(packCode)) {
            return ReportPackConstants.DEFAULT_PACK;
        }
        String pack = packCode.trim();
        if (!StringUtils.hasText(targetLayoutMode)) {
            return pack;
        }
        boolean overlay = ReportPipelineConstants.LAYOUT_MODE_OVERLAY.equalsIgnoreCase(targetLayoutMode.trim());
        if (overlay && ReportPackConstants.PRINT_PAGE_STUB.equals(pack)) {
            return ReportPackConstants.PRINT_PAGE_OVERLAY_STUB;
        }
        if (!overlay && ReportPackConstants.PRINT_PAGE_OVERLAY_STUB.equals(pack)) {
            return ReportPackConstants.PRINT_PAGE_STUB;
        }
        return pack;
    }
}
