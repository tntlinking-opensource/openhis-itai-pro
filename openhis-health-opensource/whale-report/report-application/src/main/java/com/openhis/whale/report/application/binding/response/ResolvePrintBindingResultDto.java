package com.openhis.whale.report.application.binding.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolvePrintBindingResultDto {

    private String bindingCode;
    private String templateCode;
    private String defaultTemplateCode;
    private Boolean tenantOverride;
}
