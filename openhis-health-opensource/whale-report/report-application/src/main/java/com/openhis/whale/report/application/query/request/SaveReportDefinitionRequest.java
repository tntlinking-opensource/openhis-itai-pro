package com.openhis.whale.report.application.query.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveReportDefinitionRequest {

    @NotNull
    private Long id;

    private String name;

    private Boolean exportPdfEnabled;

    private String pdfTemplateCode;
}
