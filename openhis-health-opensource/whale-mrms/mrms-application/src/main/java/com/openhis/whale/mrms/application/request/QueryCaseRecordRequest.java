package com.openhis.whale.mrms.application.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class QueryCaseRecordRequest {
    private String keyword;
    private String statusCode;
    private String deptCode;
    private LocalDate dischargeFrom;
    private LocalDate dischargeTo;
    private Integer pageNo;
    private Integer pageSize;
}
