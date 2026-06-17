package com.openhis.whale.report.application.template.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CopyTemplateAsNewRequest {

    @NotBlank
    private String sourceTemplateCode;

    @NotBlank
    @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]{0,63}", message = "模板编码须以字母开头，仅含字母、数字、下划线")
    private String newTemplateCode;

    private String newTemplateName;

    @NotBlank
    @Pattern(regexp = "flow|overlay", message = "layoutMode 仅支持 flow 或 overlay")
    private String layoutMode;

    @Pattern(regexp = "page|roll", message = "pipeline 仅支持 page 或 roll")
    private String pipeline;
}
