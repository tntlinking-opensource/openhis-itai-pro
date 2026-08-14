/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.document.inventoryitemsnapshot.queries;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 备份库存分页/汇总/导出共用筛选条件（DM-DOC-001 SDD §4.6.1）。
 */
@Getter
@Setter
public class DocInventoryItemSnapshotPageQuery {

    /** 租户（Gate 注入）。 */
    private Long tenantId;

    /** 备份批次单号（接口 {@code backupBusNo}）。 */
    private String busNo;

    /** 备份关键字；匹配名称/拼音/五笔/编码/商品名，不含批号（AS BR-INV-DTL-010）。 */
    private String keyword;

    /** 库房主键多选。 */
    private List<Long> warehouseIds;

    /** 供应商主键多选。 */
    private List<Long> supplierIds;

    /** 药品分类 code 多选。 */
    private List<String> medicationCategoryCodes;

    /** 耗材分类 code 多选。 */
    private List<String> deviceCategoryCodes;

    /** 供应状态 code 多选。 */
    private List<String> inventoryStatusEnums;

    /** 医保等级多选。 */
    private List<Integer> chrgitmLvs;

    /**
     * 基药标识筛选（药典 {@code basic_flag}）；非空时仅保留药品行且 {@code item_id} 落在预解析列表。
     */
    private Boolean basicFlag;

    /**
     * 应用层按基药标识预解析的药品定义主键；与 {@link #basicFlag} 成对使用。
     */
    private List<Long> resolvedMedicationDefinitionIds;

    /**
     * 零库存筛选：{@code true} 数量等于 0；{@code false} 数量大于 0；{@code null} 不限。
     */
    private Boolean zeroStockFlag;

    /** 剩余过期天数上限（AS BR-INV-DTL-003）。 */
    private Integer remainingDaysLe;

    /** 物品表筛选（药/耗类型）。 */
    private String itemTable;
}
