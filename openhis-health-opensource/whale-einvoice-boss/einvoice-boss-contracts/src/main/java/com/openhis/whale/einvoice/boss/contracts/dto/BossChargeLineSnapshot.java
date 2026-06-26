package com.openhis.whale.einvoice.boss.contracts.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * health 收费明细快照（用于初始化 einv_boss_line）。
 */
@Data
public class BossChargeLineSnapshot {

    /** 收费明细主键。 */
    private Long chargeItemId;

    /** 收费项目定义主键。 */
    private Long definitionId;

    /** 院内内部编码（对照表 lookup key）。 */
    private String internalCode;

    /** 开票金额。 */
    private BigDecimal amount;

    /** 数量。 */
    private BigDecimal quantity;

    /** 计量单位。 */
    private String unit;

    /** 单价。 */
    private BigDecimal unitPrice;
}
