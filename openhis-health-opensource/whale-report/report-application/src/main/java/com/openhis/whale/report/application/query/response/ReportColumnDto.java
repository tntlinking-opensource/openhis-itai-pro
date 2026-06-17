package com.openhis.whale.report.application.query.response;

import lombok.Data;

@Data
public class ReportColumnDto {

    private String field;
    private String label;
    private String align;
    private Integer width;
}
