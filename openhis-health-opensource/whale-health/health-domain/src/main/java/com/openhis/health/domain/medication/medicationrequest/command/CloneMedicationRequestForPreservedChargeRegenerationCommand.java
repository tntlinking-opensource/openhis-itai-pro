/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 半退保留链：按源药品申请事实在本聚合内正向克隆新医嘱（DM-MED-003 §6.1.16），供应用编排与 ChargeItem 新锚点对齐。
 */
@Getter
@Setter
public class CloneMedicationRequestForPreservedChargeRegenerationCommand {

    /** 源药品申请主键。 */
    private Long sourceMedicationRequestId;

    /** 操作人主键。 */
    private Long operatorId;
}
