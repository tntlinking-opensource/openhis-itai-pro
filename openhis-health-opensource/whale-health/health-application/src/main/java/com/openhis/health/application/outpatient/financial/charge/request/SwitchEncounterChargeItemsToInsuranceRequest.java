/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 就诊收费项转医保请求。
 */
@Data
@Accessors(chain = true)
public class SwitchEncounterChargeItemsToInsuranceRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /**
     * 勾选待转医保的收费项主键列表；空或 null 时回落为就诊下全部可切换待收行（兼容旧调用）。
     */
    private List<Long> chargeItemIds;
}
