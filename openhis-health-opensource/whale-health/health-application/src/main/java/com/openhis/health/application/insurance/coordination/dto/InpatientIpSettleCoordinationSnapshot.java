/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * IP_SETTLE 协同单 request_snapshot 载荷（Worker 本地落账用）。
 */
@Data
@Accessors(chain = true)
public class InpatientIpSettleCoordinationSnapshot {

    /** 支付草稿主键。 */
    private Long paymentDraftId;
    /** 本地预结算记录主键。 */
    private Long preSettleId;
    /** 医保平台预结算 id。 */
    private Long ybPreSettleId;
    /** 住院就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 操作员主键。 */
    private Long operatorId;
    /** 本次结算收费项 id 列表。 */
    private List<Long> chargeItemIds;
    /** 是否出院全结。 */
    private Boolean dischargeFullSettle;
    /** 本批次结算前已结金额。 */
    private BigDecimal settledBeforeCurrentBatch;
    /** 账单日期。 */
    private LocalDateTime billDate;
}
