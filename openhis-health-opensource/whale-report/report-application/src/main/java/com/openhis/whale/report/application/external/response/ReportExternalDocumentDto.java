package com.openhis.whale.report.application.external.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ReportExternalDocumentDto {

    private String documentCode;
    private String title;
    private Boolean available;
    private String contentType;
    private String documentUrl;
    private String message;
    private Map<String, Object> paramsEcho;
}
