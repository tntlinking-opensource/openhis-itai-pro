/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 调整药品医嘱分组命令。
 */
@Getter
@Setter
public class AdjustMedicationOrderGroupCommand {
    /** 操作人标识。 */
    private Long operatorId;
    /** 待调整医嘱标识集合。 */
    private List<Long> ids;
    /** 目标分组标识。 */
    private Long groupId;
}
