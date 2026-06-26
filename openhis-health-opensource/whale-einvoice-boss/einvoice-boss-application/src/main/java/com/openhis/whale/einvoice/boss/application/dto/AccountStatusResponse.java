package com.openhis.whale.einvoice.boss.application.dto;

import lombok.Data;

/**
 * 博思入账状态查询结果（P3）。
 */
@Data
public class AccountStatusResponse {

    private String billBatchCode;
    private String billNo;
    private String accountStatus;
    private String accountStatusLabel;
    private String accountDate;
    private boolean bossSuccess;
    private String bossResultCode;
    private String bossMessage;
}
