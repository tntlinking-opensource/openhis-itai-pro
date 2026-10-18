/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.support;

import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.inventoryitem.queries.InventoryProductAggregateKey;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryProductQuantityAggregateModel;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 库存预警：全院品种拆零合计后解析命中键（工作台计数 / 列表品种筛），服务采购补货。
 *
 * <p>与效期预警分工：效期看批号近效期；本过滤只按<strong>品种</strong>判定（含全院合计为 0 的断货），
 * 不按库房、不按批号设阈值。阈值优先级（AS-INV-WHS-004 BR-INV-DTL-024）：目录
 * {@code itemMinQuantity}/{@code itemMaxQuantity}（库存单位）优先，均未维护回落系统设置包装阈值。</p>
 *
 * <p>跨聚合只读组装：库存汇总经 {@link InventoryItemQueryService}，单位换算与警戒量经
 * {@link ProductDefinitionDisplayTerminologyAssembler}（B.6.4）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryLowStockWarningFilter {

    private final InventoryItemQueryService inventoryItemQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 解析全院汇总后命中库存预警的品种键。
     *
     * <p>目录已维护警戒线：库存单位数量 &lt; 最低值 或 &gt; 最高值（仅对已维护侧生效）；
     * 目录未维护：包装单位合计 &lt; {@code globalMaxPackageQuantityExclusive}。</p>
     *
     * @param globalMaxPackageQuantityExclusive 全局包装单位上限（不含），目录未维护时使用；如 30 表示 &lt; 30 包装
     * @return 命中键列表；永不为 null
     */
    public List<InventoryProductAggregateKey> resolveLowStockProductKeys(
        BigDecimal globalMaxPackageQuantityExclusive) {
        List<InventoryProductQuantityAggregateModel> aggregates =
            this.inventoryItemQueryService.listHospitalWideProductQuantityAggregates();
        // 无库存行则无预警键
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
        // 加载候选：经 ProductDefinitionDisplayTerminologyAssembler 取单位换算比与目录警戒量（B.6.4）
        Map<String, ProductDefinitionDisplayDto> products =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookups));
        List<InventoryProductAggregateKey> keys = new ArrayList<>();
        for (InventoryProductQuantityAggregateModel aggregate : aggregates) {
            if (aggregate == null || aggregate.getItemId() == null
                || StringUtils.isEmpty(aggregate.getItemTable())) {
                continue;
            }
            // 全院合计为 0 也要预警（与按库房汇总不同：全院断货不正常）
            BigDecimal totalSplit = aggregate.getTotalSplitQuantity();
            if (totalSplit == null) {
                totalSplit = BigDecimal.ZERO;
            }
            String compositeKey =
                ProductDefinitionKeyUtil.buildCompositeKey(aggregate.getItemTable(), aggregate.getItemId());
            ProductDefinitionDisplayDto product = products.get(compositeKey);
            // 按目录警戒线或全局包装阈值判定是否命中
            if (!this.matchesInventoryWarning(product, totalSplit, globalMaxPackageQuantityExclusive)) {
                continue;
            }
            keys.add(new InventoryProductAggregateKey(
                aggregate.getItemTable().trim(),
                aggregate.getItemId()));
        }
        return keys;
    }

    /**
     * 库存预警品种数（全院汇总后命中目录警戒线或全局包装阈值）。
     *
     * @param globalMaxPackageQuantityExclusive 全局包装单位上限（不含）
     * @return 命中品种数
     */
    public int countLowStockWarningProducts(BigDecimal globalMaxPackageQuantityExclusive) {
        return this.resolveLowStockProductKeys(globalMaxPackageQuantityExclusive).size();
    }

    /**
     * 单品种是否命中库存预警：目录警戒线优先，未维护回落全局包装阈值。
     *
     * @param product                           产品定义展示；可空
     * @param totalSplit                        全院拆零合计（可为 0，表示全院断货）
     * @param globalMaxPackageQuantityExclusive 全局包装上限（不含）
     * @return true 命中预警
     */
    private boolean matchesInventoryWarning(
        ProductDefinitionDisplayDto product,
        BigDecimal totalSplit,
        BigDecimal globalMaxPackageQuantityExclusive) {
        BigDecimal catalogMin = null;
        BigDecimal catalogMax = null;
        if (product != null) {
            catalogMin = product.getItemMinQuantity();
            catalogMax = product.getItemMaxQuantity();
        }
        boolean hasCatalogMin = catalogMin != null;
        boolean hasCatalogMax = catalogMax != null;
        // 目录任一侧已维护：整品种改走目录警戒线，不再套用全局包装阈值
        if (hasCatalogMin || hasCatalogMax) {
            return this.matchesCatalogThreshold(product, totalSplit, catalogMin, catalogMax,
                hasCatalogMin, hasCatalogMax);
        }
        return this.matchesGlobalPackageThreshold(product, totalSplit, globalMaxPackageQuantityExclusive);
    }

    /**
     * 目录警戒线命中：库存单位合计低于已维护最低值，或高于已维护最高值。
     *
     * @param product      产品定义（取库存单位换算比）
     * @param totalSplit   全院拆零合计
     * @param catalogMin   最低警戒（库存单位）；未维护可为 null
     * @param catalogMax   最高警戒（库存单位）；未维护可为 null
     * @param hasCatalogMin 是否已维护最低警戒
     * @param hasCatalogMax 是否已维护最高警戒
     * @return true 命中
     */
    private boolean matchesCatalogThreshold(
        ProductDefinitionDisplayDto product,
        BigDecimal totalSplit,
        BigDecimal catalogMin,
        BigDecimal catalogMax,
        boolean hasCatalogMin,
        boolean hasCatalogMax) {
        BigDecimal stockRatio = null;
        if (product != null) {
            stockRatio = product.getStockUnitRatio();
        }
        // 无有效库存单位换算比时无法将目录警戒量落到拆零口径，跳过以免误报
        if (stockRatio == null || stockRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (hasCatalogMin) {
            BigDecimal minSplit = catalogMin.multiply(stockRatio);
            // 低于最低警戒线 → 低库存预警（DM-ADM-008 / DM-MED-001）
            if (totalSplit.compareTo(minSplit) < 0) {
                return true;
            }
        }
        if (hasCatalogMax) {
            BigDecimal maxSplit = catalogMax.multiply(stockRatio);
            // 高于最高警戒线 → 超储预警
            if (totalSplit.compareTo(maxSplit) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 全局包装阈值命中：包装单位合计 &lt; 系统设置值。
     *
     * @param product                           产品定义（取包装单位换算比）
     * @param totalSplit                        全院拆零合计
     * @param globalMaxPackageQuantityExclusive 包装上限（不含）
     * @return true 命中
     */
    private boolean matchesGlobalPackageThreshold(
        ProductDefinitionDisplayDto product,
        BigDecimal totalSplit,
        BigDecimal globalMaxPackageQuantityExclusive) {
        // 阈值非法时不产生预警键，避免误筛全表
        if (globalMaxPackageQuantityExclusive == null
            || globalMaxPackageQuantityExclusive.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal packageRatio = null;
        if (product != null) {
            packageRatio = product.getPackageUnitRatio();
        }
        // 无有效包装换算比时跳过，避免除零或误判
        if (packageRatio == null || packageRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal thresholdSplit = globalMaxPackageQuantityExclusive.multiply(packageRatio);
        return totalSplit.compareTo(thresholdSplit) < 0;
    }
}
