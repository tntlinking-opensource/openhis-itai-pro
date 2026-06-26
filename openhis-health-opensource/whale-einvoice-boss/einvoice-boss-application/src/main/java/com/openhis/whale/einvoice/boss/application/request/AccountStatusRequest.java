package com.openhis.whale.einvoice.boss.application.request;

import lombok.Data;

/**
 * 入账状态查询请求（P3）。
 */
@Data
public class AccountStatusRequest {

    private Long documentId;
    private String billBatchCode;
    private String billNo;
}
