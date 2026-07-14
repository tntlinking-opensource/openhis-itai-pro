/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.support;

import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryProductAggregateKey;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryProductQuantityAggregateModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 库存预警：全院各库房合计后按包装单位阈值解析命中品种键（供列表筛选与工作台计数）。
 *
 * <p>跨聚合只读组装：库存汇总经 {@link InventoryItemQueryService}，包装换算比经
 * {@link ProductDefinitionDisplayTerminologyAssembler}（B.6.4）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryLowStockWarningFilter {

    private final InventoryItemQueryService inventoryItemQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 解析全院汇总包装数低于阈值的品种键。
     *
     * @param maxPackageQuantityExclusive 包装单位上限（不含），如 30 表示 &lt; 30 包装
     * @return 命中键列表；永不为 null
     */
    public List<InventoryProductAggregateKey> resolveLowStockProductKeys(
        BigDecimal maxPackageQuantityExclusive) {
        // 阈值非法时不产生预警键，避免误筛全表
        if (maxPackageQuantityExclusive == null
            || maxPackageQuantityExclusive.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        List<InventoryProductQuantityAggregateModel> aggregates =
            this.inventoryItemQueryService.listHospitalWideProductQuantityAggregates();
        if (aggregates.isEmpty()) {
            return List.of();
        }
        Set<ProductDefinitionBatchLookup> lookups = new LinkedHashSet<>();
        for (InventoryProductQuantityAggregateModel aggregate : aggregates) {
            if (aggregate == null || aggregate.getItemId() == null
                || StringUtils.isEmpty(aggregate.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(aggregate.getItemTable());
            lookup.setProductDefinitionId(aggregate.getItemId());
            lookups.add(lookup);
        }
        // 加载候选：经 ProductDefinitionDisplayTerminologyAssembler 取 packageUnitRatio（B.6.4）
        Map<String, ProductDefinitionDisplayDto> products =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookups));
        List<InventoryProductAggregateKey> keys = new ArrayList<>();
        for (InventoryProductQuantityAggregateModel aggregate : aggregates) {
            if (aggregate == null || aggregate.getItemId() == null
                || StringUtils.isEmpty(aggregate.getItemTable())) {
                continue;
            }
            BigDecimal totalSplit = aggregate.getTotalSplitQuantity();
            // 全院零库存品种不参与低库存预警计数
            if (totalSplit == null || totalSplit.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String compositeKey =
                ProductDefinitionKeyUtil.buildCompositeKey(aggregate.getItemTable(), aggregate.getItemId());
            ProductDefinitionDisplayDto product = products.get(compositeKey);
            BigDecimal ratio = null;
            if (product != null) {
                ratio = product.getPackageUnitRatio();
            }
            // 无有效换算比时跳过，避免除零或误判
            if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal thresholdSplit = maxPackageQuantityExclusive.multiply(ratio);
            if (totalSplit.compareTo(thresholdSplit) < 0) {
                keys.add(new InventoryProductAggregateKey(
                    aggregate.getItemTable().trim(),
                    aggregate.getItemId()));
            }
        }
        return keys;
    }

    /**
     * 库存预警品种数（全院汇总后 &lt; 阈值）。
     *
     * @param maxPackageQuantityExclusive 包装单位上限（不含）
     * @return 命中品种数
     */
    public int countLowStockWarningProducts(BigDecimal maxPackageQuantityExclusive) {
        return this.resolveLowStockProductKeys(maxPackageQuantityExclusive).size();
    }
}
