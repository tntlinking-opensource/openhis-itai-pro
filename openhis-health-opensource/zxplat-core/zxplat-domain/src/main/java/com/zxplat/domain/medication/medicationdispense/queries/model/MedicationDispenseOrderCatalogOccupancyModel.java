/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import com.zxplat.domain.share.enums.DispenseStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品发放占用行读模型（候选库位 ∩ 药品定义；库位级，不含批号）。
 *
 * <p>供目录预减 {@code listMedicationDispensesForOrderCatalogOccupancy}（{@code DRAFT}/{@code PREPARATION}）
 * 与签发 FIFO 额外占用 {@code listMedicationDispensesForInpatientSummarizedOccupancy}（住院 {@code SUMMARIZED}）复用；
 * 候选库房全局，不限就诊。应用层按「表+定义+库位」合并拆零量后，再在 FEFO 批次遍历中顺序消耗。</p>
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

    /** 发放行数量单位编码（与 {@link com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense#unitCode} 一致）。 */
    private String unitCode;

    /** 发放状态（目录行为草稿/待发；FIFO 额外行可为住院汇总 {@code SUMMARIZED}）。 */
    private DispenseStatus statusEnum;
}
