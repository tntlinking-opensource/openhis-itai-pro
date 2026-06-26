package com.openhis.whale.einvoice.boss.contracts.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 本地日结核对统计（health + boss 单据）。
 */
@Data
public class ReconcileLocalStatsDto {

    /** 已开具笔数。 */
    private long issuedCount;

    /** 已开具金额合计。 */
    private BigDecimal issuedTotalAmt = BigDecimal.ZERO;
}
