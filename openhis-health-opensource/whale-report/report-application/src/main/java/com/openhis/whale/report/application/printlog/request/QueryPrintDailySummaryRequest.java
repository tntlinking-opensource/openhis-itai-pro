package com.openhis.whale.report.application.printlog.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class QueryPrintDailySummaryRequest {

    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String keyword;
    private String eventType;
    private String targetCode;
    private LocalDate statDateFrom;
    private LocalDate statDateTo;
}
