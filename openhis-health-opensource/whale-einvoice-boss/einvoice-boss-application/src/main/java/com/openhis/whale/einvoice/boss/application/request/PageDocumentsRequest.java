package com.openhis.whale.einvoice.boss.application.request;

import lombok.Data;

import java.time.LocalDate;

/**
 * 电子发票分页查询条件。
 */
@Data
public class PageDocumentsRequest {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String keyword;
    private String billNo;
    private String invoiceStatus;
    private String issueKind;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
