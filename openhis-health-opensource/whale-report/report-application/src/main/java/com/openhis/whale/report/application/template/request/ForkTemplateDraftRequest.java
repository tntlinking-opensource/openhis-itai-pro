package com.openhis.whale.report.application.template.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForkTemplateDraftRequest {

    @NotBlank
    private String templateCode;
}
