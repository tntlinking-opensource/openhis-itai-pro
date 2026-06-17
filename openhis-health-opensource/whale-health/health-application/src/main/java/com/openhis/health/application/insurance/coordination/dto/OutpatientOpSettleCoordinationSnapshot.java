/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.dto;

import com.openhis.health.application.outpatient.financial.charge.request.ConfirmOutpatientChargeSettlementRequest;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * OP_SETTLE 协同单 request_snapshot 载荷（Worker 平台 2207 + 本地落账用）。
 */
@Data
@Accessors(chain = true)
public class OutpatientOpSettleCoordinationSnapshot {

    /** 支付草稿主键。 */
    private Long paymentDraftId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 操作员主键。 */
    private Long operatorId;
    /** 本次结算收费项 id 列表。 */
    private List<Long> chargeItemIds;
    /** 医保收费批次号列表。 */
    private List<String> chrgBchnos;
    /** 确认结算请求，供 Worker 本地落账回放。 */
    private ConfirmOutpatientChargeSettlementRequest confirmRequest;
}
