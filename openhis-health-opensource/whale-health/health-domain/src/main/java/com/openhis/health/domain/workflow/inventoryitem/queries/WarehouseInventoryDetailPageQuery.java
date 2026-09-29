/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 药库库存明细（现库）分页/汇总/导出共用筛选条件。
 *
 * <p>机构收窄不通过本入参传递，由应用层在 Gate 阶段解析库房范围后写入 {@link #effectiveWarehouseIds}（B.10.11）。</p>
 */
@Getter
@Setter
public class WarehouseInventoryDetailPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 应用层门控后的库房主键范围（与请求 {@code warehouseIds} 及机构可见库房求交结果）；
     * 空列表表示不按库房主键收窄（须与产品安全策略一致，通常仅在会话缺省机构时出现）。
     */
    private List<Long> effectiveWarehouseIds;

    /** 供应商主键多选，语义为 SQL IN。 */
    private List<Long> supplierIds;

    /**
     * 药品类型编码多选；与 {@code hisrm_product_definition.category_code} 对齐，
     * 且谓词侧要求 {@code item_table} 为药品定义表。
     */
    private List<String> medicationCategoryCodes;

    /**
     * 耗材类型编码多选；与产品定义读模型 {@code category_code} 对齐，
     * 且谓词侧要求 {@code item_table} 为耗材定义表。
     */
    private List<String> deviceCategoryCodes;

    /** 医保等级多选，与药品/耗材主表 {@code chrgitm_lv} 子查询对齐。 */
    private List<Integer> chrgitmLvList;

    /**
     * 重点药类型 code（见 {@code KeyDrugType} 四旗）；非空时仅保留命中药品定义的库存行（耗材排除）。
     * <p>定义主键由应用层 {@code InventoryCatalogFilter} 预解析写入 {@link #resolvedMedicationDefinitionIds}。</p>
     */
    private String keyDrugType;

    /**
     * 药品性质 code（NARCOTIC/PSYCH_ONE/PSYCH_TWO）；与 {@link #keyDrugType} 分字段，同时非空时 AND 求交。
     */
    private String medicinePropertyCode;

    /** 供应状态持久化码多选，取值域为 {@code PublicationStatus#getCode()}。 */
    private List<String> inventoryStatusEnums;

    /**
     * 库存范围筛选：{@code true} 数量等于 0；{@code false} 数量大于 0；{@code null} 不限。
     */
    private Boolean zeroStockOnly;

    /**
     * 近效期窗口天数：效期时刻不晚于「今天 + 天数」当日结束时刻的行；{@code null} 表示不按近效期收窄。
     */
    private Integer expireWithinDays;

    /**
     * 库存预警：仅保留全院品种汇总后命中低库存阈值的批号行；{@code null} 表示不限；
     * 空集合表示无命中（列表应返回空页）。
     */
    private List<InventoryProductAggregateKey> lowStockWarningAggregateKeys;

    /**
     * 应用层解析后的药品定义主键（{@code item_table=med_medication_definition}）；由 {@code InventoryCatalogFilter} 写入。
     */
    private List<Long> resolvedMedicationDefinitionIds;

    /**
     * 应用层解析后的耗材定义主键（{@code item_table=adm_device_definition}）；由 {@code InventoryCatalogFilter} 写入。
     */
    private List<Long> resolvedDeviceDefinitionIds;

    /**
     * 关键字预解析的药品定义主键（编码/商品名等）；由 {@code InventoryCatalogFilter#enrichKeywordDefinitionIds} 写入。
     */
    private List<Long> resolvedMedicationDefinitionIdsForKeyword;

    /**
     * 关键字预解析的耗材定义主键（编码/商品名等）；由 {@code InventoryCatalogFilter#enrichKeywordDefinitionIds} 写入。
     */
    private List<Long> resolvedDeviceDefinitionIdsForKeyword;

    /**
     * 项目类型：药品/耗材定义表名（{@code item_table} 等值）；空/null 不限。
     */
    private String itemTable;
}
