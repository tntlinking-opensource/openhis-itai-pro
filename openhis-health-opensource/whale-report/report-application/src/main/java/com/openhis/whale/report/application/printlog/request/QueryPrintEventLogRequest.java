package com.openhis.whale.report.application.printlog.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class QueryPrintEventLogRequest {

    private Integer pageNo = 1;
    private Integer pageSize = 20;
    /** 操作员姓名 / 模板编码 / clientJobId / 错误信息 */
    private String keyword;
    private String eventType;
    private String status;
    private String targetCode;
    private String targetType;
    private LocalDate createdFrom;
    private LocalDate createdTo;
}
