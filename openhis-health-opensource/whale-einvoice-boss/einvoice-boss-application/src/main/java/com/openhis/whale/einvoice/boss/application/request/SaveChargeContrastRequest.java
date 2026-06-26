package com.openhis.whale.einvoice.boss.application.request;

import lombok.Data;

/**
 * 保存收费对照请求。
 */
@Data
public class SaveChargeContrastRequest {

    private Long id;
    private Long orgId;
    private String internalCode;
    private String bossChargeCode;
    private String bossChargeName;
}
