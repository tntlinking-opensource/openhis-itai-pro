package com.openhis.whale.report.application.template.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveTemplateDraftRequest {

    @NotNull
    private Long templateId;

    @NotBlank
    private String layoutJson;
}
