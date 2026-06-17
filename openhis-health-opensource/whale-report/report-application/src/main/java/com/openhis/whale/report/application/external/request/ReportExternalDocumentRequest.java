package com.openhis.whale.report.application.external.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ReportExternalDocumentRequest {

    @NotBlank
    private String documentCode;

    private Map<String, Object> params;
}
