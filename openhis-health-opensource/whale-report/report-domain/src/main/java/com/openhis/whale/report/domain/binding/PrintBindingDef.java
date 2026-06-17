package com.openhis.whale.report.domain.binding;

import java.util.List;

/**
 * 平台预置的业务打印绑定点（binding_code → 默认 template_code）。
 */
public record PrintBindingDef(
        String bindingCode,
        String name,
        String bizModule,
        String scene,
        String defaultTemplateCode,
        List<String> suggestedTemplateCodes) {

    public PrintBindingDef {
        if (suggestedTemplateCodes == null) {
            suggestedTemplateCodes = List.of();
        }
    }
}
