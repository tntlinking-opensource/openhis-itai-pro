package com.openhis.whale.report.application.query.response;

import lombok.Data;

import java.util.List;

@Data
public class ReportDefinitionItemDto {

    private Long id;
    private String reportCode;
    private String name;
    /** draft | published */
    private String status;
    private Boolean exportPdfEnabled;
    private String pdfTemplateCode;
    private Boolean allowColumnCustomize;
    private List<ReportColumnDto> columns;

    /** loader | sql */
    private String sourceType;
    private String querySql;
    private String headerJson;
    private String footerJson;
    private String filterJson;
    private String remark;
}
