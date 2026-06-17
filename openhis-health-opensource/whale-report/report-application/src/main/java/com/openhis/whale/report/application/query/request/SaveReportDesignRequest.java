package com.openhis.whale.report.application.query.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveReportDesignRequest {

    @NotNull
    private Long id;

    private String name;
    private String querySql;
    private String headerJson;
    private String footerJson;
    private String filterJson;
    private String defaultColumnsJson;
    private Boolean exportPdfEnabled;
    private String pdfTemplateCode;
}
