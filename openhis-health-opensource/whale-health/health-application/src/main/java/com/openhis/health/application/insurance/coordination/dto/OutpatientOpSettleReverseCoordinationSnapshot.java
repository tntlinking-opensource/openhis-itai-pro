/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * OP_SETTLE_REVERSE 协同单 request_snapshot（Worker 2208 撤销用）。
 */
@Data
@Accessors(chain = true)
public class OutpatientOpSettleReverseCoordinationSnapshot {

    /** 原支付单主键。 */
    private Long originalPaymentId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 操作员主键。 */
    private Long operatorId;
    /** 医保合同主键。 */
    private Long contractId;
    /** 医保账户主键。 */
    private Long accountId;
    /** 待撤销的医保结算记录 id 列表。 */
    private List<Long> settlementRecordIds;
}
