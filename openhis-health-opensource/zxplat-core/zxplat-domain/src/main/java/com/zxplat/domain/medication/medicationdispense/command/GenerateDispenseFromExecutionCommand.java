/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 由执行记录生成发放单命令。
 */
@Getter
@Setter
public class GenerateDispenseFromExecutionCommand {
    /** 药品申请标识。 */
    private Long medReqId;
    /** 执行记录标识。 */
    private Long procedureId;
    /** 计划发药时间。 */
    private LocalDateTime plannedDispenseTime;
    /** 发药位置标识。 */
    private Long locationId;
    /** 批号（FIFO 分配后写入，可空）。 */
    private String lotNumber;
    /** 应发数量。 */
    private BigDecimal quantity;
    /** 发放单位编码。 */
    private String unitCode;
    /** 药品定义标识。 */
    private Long medicationId;
    /** 就诊标识。 */
    private Long encounterId;
    /** 患者标识。 */
    private Long patientId;
}
