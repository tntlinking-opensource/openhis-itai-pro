package com.openhis.whale.report.application.printlog.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class PrintDailySummaryItemDto {

    private LocalDate statDate;
    private Long operatorId;
    private String operatorName;
    private String targetType;
    private String targetCode;
    private String eventType;
    private Integer successCount;
    private Integer failCount;
    private Integer totalCopies;
    private LocalDateTime lastEventAt;
}
