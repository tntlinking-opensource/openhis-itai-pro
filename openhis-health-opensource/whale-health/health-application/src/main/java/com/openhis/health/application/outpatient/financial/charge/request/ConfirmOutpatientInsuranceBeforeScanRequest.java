/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 银联扫码前先完成医保 2207（IF-OPC 扫码时序：CHS confirm → Win C → settle）。
 *
 * <p>批次号可显式传入；为空时按支付草稿下 ACTIVE 预结算批次解析（挂号费场景）。</p>
 */
@Data
@Accessors(chain = true)
public class ConfirmOutpatientInsuranceBeforeScanRequest {

    /** 支付草稿主键 {@code fin_payment_reconciliation.id}。 */
    private Long paymentDraftId;

    /** 门诊就诊主键（收费场景校验草稿归属）。 */
    private Long encounterId;

    /** 医保收费批次号（单组兼容）。 */
    private String ybChrgBchno;

    /** 医保收费批次号列表（混合结算多组）。 */
    private List<String> ybChrgBchnos;
}
