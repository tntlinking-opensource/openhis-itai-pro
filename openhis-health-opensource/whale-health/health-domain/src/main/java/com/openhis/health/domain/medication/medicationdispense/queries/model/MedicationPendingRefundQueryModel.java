/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.enums.DispenseStatus;

import java.math.BigDecimal;

/**
 * 待退药查询模型。
 */
@Getter
@Setter
public class MedicationPendingRefundQueryModel {
    /** 主键。 */
    private Long id;
    /** 状态枚举编码。 */
    private DispenseStatus statusEnum;
    /** 药品主键。 */
    private Long medicationId;
    /** 发药数量。 */
    private BigDecimal dispenseQuantity;
}
