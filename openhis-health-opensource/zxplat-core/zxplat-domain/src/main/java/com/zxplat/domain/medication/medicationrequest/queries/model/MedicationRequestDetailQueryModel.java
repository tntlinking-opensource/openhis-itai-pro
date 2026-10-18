/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 医嘱详情查询模型。
 */
@Getter
@Setter
public class MedicationRequestDetailQueryModel {
    /** 主键。 */
    private Long id;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** therapy枚举编码。 */
    private String therapyEnum;
    /**
     * 数量（与 {@link #unitCode} 成套；长临共用字段、语义由 {@code therapy_enum} 区分：
     * 长期=一次执行发药量，临时=开立总量）。
     */
    private BigDecimal quantity;
    /** 计划执行次数（持久化 execute_num，DM-MED-003；护士站 BR-NUR-005）。 */
    private Integer executeNum;
    /** 发药/开立单位（与 {@link #quantity} 成套；不参与剂量 {@code dose} 字段）。 */
    private String unitCode;
}
