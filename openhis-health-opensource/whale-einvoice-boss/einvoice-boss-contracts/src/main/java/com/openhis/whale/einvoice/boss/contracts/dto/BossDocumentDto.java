package com.openhis.whale.einvoice.boss.contracts.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 博思电子票据单据摘要（不含整段 payload JSON）。
 */
@Data
public class BossDocumentDto {

    /** 单据主键。 */
    private Long documentId;

    /** 关联 health 票据主键。 */
    private Long invoiceId;

    /** 关联支付主键。 */
    private Long paymentId;

    /** 开票种类（REG/AMB/IMP）。 */
    private String issueKind;

    /** 业务流水号。 */
    private String busNo;

    /** 开票总额。 */
    private BigDecimal totalAmt;

    /** 支付金额。 */
    private BigDecimal paymentAmt;

    /** 金额一致性状态。 */
    private String amountStatus;

    /** 博思票号。 */
    private String bossBillNo;
}
