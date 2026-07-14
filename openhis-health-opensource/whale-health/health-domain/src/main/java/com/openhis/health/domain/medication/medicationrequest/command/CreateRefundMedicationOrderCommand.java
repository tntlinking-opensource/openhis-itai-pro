/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建退药请求命令。
 */
@Getter
@Setter
/**
 * CreateRefundMedicationOrderCommand。
 */
public class CreateRefundMedicationOrderCommand {
    /** 操作人标识。 */
    /**
     * operatorId。
     */
    private Long operatorId;
    /** 原始药品申请标识。 */
    /**
     * originRequestId。
     */
    private Long originRequestId;
}
