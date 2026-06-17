package com.openhis.whale.report.application.pdf.element;

import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PrintElementRendererRegistry {

    private final Map<String, PrintElementRenderer> byType = new HashMap<>();

    public PrintElementRendererRegistry(List<PrintElementRenderer> renderers) {
        for (PrintElementRenderer renderer : renderers) {
            byType.put(renderer.renderType().trim().toLowerCase(), renderer);
        }
    }

    public PrintElementRenderer get(String renderType) {
        if (renderType == null) {
            throw new BusinessException("RPT_RENDER_TYPE_UNSUPPORTED", "不支持的 renderType: null");
        }
        PrintElementRenderer renderer = byType.get(renderType.trim().toLowerCase());
        if (renderer == null) {
            throw new BusinessException("RPT_RENDER_TYPE_UNSUPPORTED", "不支持的 renderType: " + renderType);
        }
        return renderer;
    }
}
