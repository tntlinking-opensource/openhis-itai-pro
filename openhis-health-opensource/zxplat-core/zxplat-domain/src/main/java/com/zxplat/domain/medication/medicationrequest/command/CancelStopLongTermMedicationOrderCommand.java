/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消长期医嘱停嘱命令（IF-IP-DOR-25）。
 */
@Getter
@Setter
public class CancelStopLongTermMedicationOrderCommand {

    /** 操作人标识。 */
    private Long operatorId;

    /** 药品申请标识。 */
    private Long id;
}
