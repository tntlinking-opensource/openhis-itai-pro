package com.openhis.whale.report.application.template.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublishTemplateRequest {

    @NotNull
    private Long templateId;
}
