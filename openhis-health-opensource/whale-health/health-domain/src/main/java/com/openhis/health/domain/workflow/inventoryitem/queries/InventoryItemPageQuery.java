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
 * 库存项分页查询入参。
 */
@Getter
@Setter
public class InventoryItemPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 项来源表。 */
    private String itemTable;

    /** 项主键。 */
    private Long itemId;

    /** 位置主键。 */
    private Long locationId;

    /**
     * 多库位 IN 筛选（与 {@link #locationId} 同时有值时优先本字段）。
     *
     * <p>规模风险：调用方须传入管辖库位集合（如科室药房），禁止全院库位。</p>
     */
    private List<Long> locationIds;

    /**
     * 物品定义主键 IN 筛选（组套预选 ids 收窄；与 {@link #itemId} 同时有值时优先本字段）。
     *
     * <p>规模风险：须为当前预选/本页 ids，禁止无界全表。</p>
     */
    private List<Long> itemIds;

    /** 库存状态枚举编码。 */
    private String inventoryStatusEnum;

    /** true 时仅返回账面数量大于零的行（与库存明细「库存大于 0」口径一致）。 */
    private Boolean positiveQuantityOnly;

    /**
     * true 时返回账面数量 &gt;= 0 的行（含 0；与 {@link #positiveQuantityOnly} 同时为 true 时本字段优先）。
     *
     * <p>护士划价耗材选品：有库存记录即展示，不因数量 0 隐藏。</p>
     */
    private Boolean includeZeroQuantity;

    /**
     * 关键字预解析的药品定义主键（编码/商品名/拼音五笔等，应用层写入；B.6.4 禁止 inventory Criteria 跨表 JOIN）。
     *
     * <p>与库存冗余列 OR：仅命中商品名时仍能选到对应库存批号行。</p>
     */
    private List<Long> resolvedMedicationDefinitionIdsForKeyword;

    /**
     * 关键字预解析的耗材定义主键（编码/商品名/拼音五笔等，应用层写入；B.6.4）。
     */
    private List<Long> resolvedDeviceDefinitionIdsForKeyword;
}
