package com.openhis.whale.einvoice.boss.application.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 金额校验结果。
 */
@Data
public class BossAmountValidateResultDto {

    private boolean valid;
    private BigDecimal lineSum;
    private BigDecimal totalAmt;
    private BigDecimal diff;
}
