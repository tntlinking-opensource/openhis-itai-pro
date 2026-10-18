/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 签退药品医嘱命令。
 */
@Getter
@Setter
/**
 * SignOffMedicationOrderCommand。
 */
public class SignOffMedicationOrderCommand {
    /** 操作人标识。 */
    /**
     * operatorId。
     */
    private Long operatorId;
    /** 药品申请标识。 */
    /**
     * id。
     */
    private Long id;
}
