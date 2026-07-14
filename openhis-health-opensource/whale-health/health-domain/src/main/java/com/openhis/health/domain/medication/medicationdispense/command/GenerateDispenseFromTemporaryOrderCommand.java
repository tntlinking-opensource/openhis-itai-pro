/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.enums.MedicationDispenseType;

import java.math.BigDecimal;

/**
 * 由临时医嘱生成发放单命令。
 */
@Getter
@Setter
public class GenerateDispenseFromTemporaryOrderCommand {
    /** 药品申请标识。 */
    private Long medReqId;
    /** 药品定义标识。 */
    private Long medicationId;
    /** 就诊标识。 */
    private Long encounterId;
    /** 患者标识。 */
    private Long patientId;
    /** 发药位置标识。 */
    private Long locationId;
    /** 应发数量。 */
    private BigDecimal quantity;
    /** 发放单位编码。 */
    private String unitCode;
    /** 发放类型编码。 */
    private MedicationDispenseType dispenseEnum;
}
