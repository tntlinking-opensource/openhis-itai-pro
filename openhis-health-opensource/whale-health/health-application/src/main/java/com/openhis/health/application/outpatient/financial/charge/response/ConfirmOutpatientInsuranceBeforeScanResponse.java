/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 扫码前医保 2207 确认结果。
 */
@Data
@Accessors(chain = true)
public class ConfirmOutpatientInsuranceBeforeScanResponse {

    /** 本次确认（或复用已确认）的批次数量。 */
    private int confirmedBatchCount;

    /** 是否全部复用草稿上已有 CONFIRMED 回执（未再次出站 2207）。 */
    private boolean reusedExisting;

    /**
     * 全部 2207 确认后的权威个人现金（Σ psn_cash + 自费段）。
     *
     * <p>银联扫码应付以此为准，禁止再信 2206 {@code displayAmount}。</p>
     */
    private BigDecimal patientCashAmount;
}
