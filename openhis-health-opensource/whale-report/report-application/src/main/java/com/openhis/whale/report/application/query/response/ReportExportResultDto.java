package com.openhis.whale.report.application.query.response;

import lombok.Data;

@Data
public class ReportExportResultDto {

    private String contentType;
    private String fileName;
    private String bodyBase64;
}
