package com.openhis.whale.einvoice.boss.contracts.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 博思报文明细行读模型。
 */
@Data
public class BossLineDto {

    /** 明细主键。 */
    private Long lineId;

    /** 行号。 */
    private Integer lineNo;

    /** 博思收费项目编码。 */
    private String chargeCode;

    /** 博思收费项目名称。 */
    private String chargeName;

    /** 标准金额。 */
    private BigDecimal stdAmount;

    /** 实际开票金额（调额后以本字段为准）。 */
    private BigDecimal amount;

    /** 是否经人工调额。 */
    private boolean adjusted;
}
