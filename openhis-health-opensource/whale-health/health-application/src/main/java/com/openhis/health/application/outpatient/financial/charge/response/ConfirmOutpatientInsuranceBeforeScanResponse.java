/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.response;

import lombok.Data;
import lombok.experimental.Accessors;

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
}
