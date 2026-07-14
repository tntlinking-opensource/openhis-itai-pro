/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-IP-ARG-08：单 encounter 滚费处理计数。
 */
@Getter
@Setter
public class AutoRollEncounterProcessResult {

    /** 本就诊是否至少成功落账 1 条 ChargeItem。 */
    private boolean processedEncounter;

    /** 新建 ChargeItem 条数。 */
    private int createdChargeItemCount;

    /** 幂等/边界跳过条数。 */
    private int skippedCount;

    /** 单条落账失败条数。 */
    private int failedCount;
}
