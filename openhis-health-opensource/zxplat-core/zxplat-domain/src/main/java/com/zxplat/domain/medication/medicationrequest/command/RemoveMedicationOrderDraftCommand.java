/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 移除药品医嘱草稿命令。
 */
@Getter
@Setter
/**
 * RemoveMedicationOrderDraftCommand。
 */
public class RemoveMedicationOrderDraftCommand {
    /** 药品申请标识。 */
    /**
     * id。
     */
    private Long id;
    /** 操作人标识。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
