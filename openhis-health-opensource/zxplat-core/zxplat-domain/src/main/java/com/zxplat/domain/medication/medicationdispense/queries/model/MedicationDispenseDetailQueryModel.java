/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;
import com.zxplat.domain.share.enums.DispenseStatus;

import java.math.BigDecimal;

/**
 * 发放详情查询模型。
 */
@Getter
@Setter
public class MedicationDispenseDetailQueryModel {
    /** 主键。 */
    private Long id;
    /** 状态枚举编码。 */
    private DispenseStatus statusEnum;
    /** med申请主键。 */
    private Long medReqId;
    /** 数量。 */
    private BigDecimal quantity;
    /** 发药数量。 */
    private BigDecimal dispenseQuantity;
}
