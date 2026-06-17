package com.openhis.whale.report.application.binding.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PrintBindingItemDto {

    private String bindingCode;
    private String bindingName;
    private String bizModule;
    private String scene;
    private String defaultTemplateCode;
    private String templateCode;
    private Boolean tenantOverride;
    private List<PrintBindingTemplateOptionDto> candidates = new ArrayList<>();
}
