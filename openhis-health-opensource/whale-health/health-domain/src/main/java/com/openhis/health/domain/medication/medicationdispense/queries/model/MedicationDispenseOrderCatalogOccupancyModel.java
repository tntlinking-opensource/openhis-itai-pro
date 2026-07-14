/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.queries.model;

import com.openhis.health.domain.share.enums.DispenseStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 医嘱开立目录用：同就诊下药品发放预占行（候选库位 ∩ 目录药品定义）。
 *
 * <p>供 {@code MedicationDispenseQueryService#listMedicationDispensesForOrderCatalogOccupancy} 返回，装配「还可开」时从物理可用量中扣减；候选库房全局，不限就诊。</p>
 */
@Getter
@Setter
public class MedicationDispenseOrderCatalogOccupancyModel {

    /** 药品定义主键（与目录行药品 id 对齐）。 */
    private Long medicationId;

    /** 发放占用的库房库位（与候选库位集合求交后的实际落位）。 */
    private Long locationId;

    /** 发放行声明数量（预占口径：业务单位，应用层须按 {@code unitCode} 换算为拆零后与库存比对）。 */
    private BigDecimal quantity;

    /** 发放行数量单位编码（与 {@link com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense#unitCode} 一致）。 */
    private String unitCode;

    /** 发放状态（草稿/待发链上状态之一）。 */
    private DispenseStatus statusEnum;
}
