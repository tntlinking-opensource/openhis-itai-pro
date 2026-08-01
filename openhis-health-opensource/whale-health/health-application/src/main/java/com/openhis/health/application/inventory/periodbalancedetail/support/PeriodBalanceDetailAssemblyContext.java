/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.periodbalancedetail.support;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.model.DocInventoryItemSnapshotRowQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;

/**
 * 库房期初期末明细品种级汇总装配上下文。
 */
@Getter
@Setter
public class PeriodBalanceDetailAssemblyContext {

    /** 选中库位主键。 */
    private Long locationId;

    /** 库位形态 code。 */
    private String locationFormEnum;

    /** 物品主表。 */
    private String itemTable;

    /** 期末是否实时。 */
    private boolean endUsesRealtime;

    /** 期初品种快照汇总（itemId → 行）。 */
    private Map<Long, ItemSnapshotSum> beginByItemId = new HashMap<>();

    /** 期末品种快照汇总（备份期末）。 */
    private Map<Long, ItemSnapshotSum> endSnapshotByItemId = new HashMap<>();

    /** 期末实时库存批号行（itemId → 批号行列表）。 */
    private Map<Long, List<DocInventoryItemSnapshotRowQueryModel>> endRealtimeLotRowsByItemId = new HashMap<>();

    /** 期间业务汇总（itemId → 累加器）。 */
    private Map<Long, PeriodBalanceDetailAggregateRow> periodByItemId = new HashMap<>();

    /** 品种展示读模型。 */
    private Map<Long, ProductDefinitionDisplayDto> productByItemId = new HashMap<>();

    /** 页内品种调价历史（itemId → 历史行）。 */
    private Map<Long, List<PriceAdjustmentLineQueryModel>> priceHistoryByItemId = new HashMap<>();

    /** 实时期末批号进价（composite lot key → 包装价）。 */
    private Map<String, BigDecimal> lotCostPriceByKey = new HashMap<>();

    /** 实时期末批号售价（composite lot key → 包装价）。 */
    private Map<String, BigDecimal> lotSalePriceByKey = new HashMap<>();

    /** 发药日备份快照批号四价回落（composite lot key → 快照行）。 */
    private Map<String, DocInventoryItemSnapshotRowQueryModel> snapshotPriceFallbackByKey = new HashMap<>();

    /**
     * 品种级快照数量/金额汇总块。
     */
    @Getter
    @Setter
    public static class ItemSnapshotSum {

        /** 品种主键。 */
        private Long itemId;

        /** 最小单位数量合计。 */
        private BigDecimal quantity = BigDecimal.ZERO;

        /** 进价总额合计。 */
        private BigDecimal costAmount = BigDecimal.ZERO;

        /** 售价总额合计。 */
        private BigDecimal saleAmount = BigDecimal.ZERO;

        /** 包装换算比（展示用，取首行）。 */
        private BigDecimal packageUnitRatio;

        /** 包装单位 code。 */
        private String packageUnitCode;

        /** 批号包装进价追踪（单批号可展示）。 */
        private PeriodBalanceDetailPackageUnitPriceTracker costPackageUnitPriceTracker =
            new PeriodBalanceDetailPackageUnitPriceTracker();

        /** 批号包装售价追踪（单批号可展示）。 */
        private PeriodBalanceDetailPackageUnitPriceTracker salePackageUnitPriceTracker =
            new PeriodBalanceDetailPackageUnitPriceTracker();
    }
}
