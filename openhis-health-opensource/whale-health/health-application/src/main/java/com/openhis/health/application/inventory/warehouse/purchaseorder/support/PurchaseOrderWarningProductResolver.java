/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.openhis.health.application.inventory.inventorydetail.support.InventoryWarningSettingResolver;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.constants.InventoryWarningConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryProductLatestExpiryNearModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryProductQuantityAggregateModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订货预警快捷选品：合并效期（最晚一批近效）与低储（仅低于最低）品种键（AS-INV-WHS-001 IF-WHS-PO-14）。
 *
 * <p>效期经 {@link InventoryItemQueryService#listProductKeysWhoseLatestLotNearExpiry}；低储复用全院汇总后仅取低于最低 /
 * 全局包装阈值侧（不含超储，BR-WHS-PO-015）。跨聚合目录读经
 * {@link ProductDefinitionDisplayTerminologyAssembler}（B.6.4）。阈值设置复用
 * {@link InventoryWarningSettingResolver}（与明细工作台共用系统设置项，判定规则不同）。</p>
 */
@Component
@RequiredArgsConstructor
public class PurchaseOrderWarningProductResolver {

    /** 库存项只读：最晚效期批与全院拆零合计。 */
    private final InventoryItemQueryService inventoryItemQueryService;

    /** 系统设置效期天数 / 全局包装阈值。 */
    private final InventoryWarningSettingResolver inventoryWarningSettingResolver;

    /** 目录展示与警戒量、换算比（B.6.4）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 按筛选项解析订货预警命中行（品种维，未分页）。
     *
     * @param warningFilter    {@code expire} / {@code lowStock} / {@code all}
     * @param productTableName 表头项目类型对应表名；已由 Gate 校验白名单
     * @return 命中行；永不为 null
     */
    public List<PurchaseWarningHit> resolveWarningProductHits(String warningFilter, String productTableName) {
        String filter = warningFilter.trim();
        boolean needExpire = InventoryWarningConstants.WARNING_MODE_EXPIRE.equals(filter)
            || InventoryWarningConstants.WARNING_FILTER_ALL.equals(filter);
        boolean needLowStock = InventoryWarningConstants.WARNING_MODE_LOW_STOCK.equals(filter)
            || InventoryWarningConstants.WARNING_FILTER_ALL.equals(filter);

        Map<String, PurchaseWarningHit> byKey = new LinkedHashMap<>();

        // 效期：最晚效期批近效（BR-WHS-PO-014）
        if (needExpire) {
            int expireDays = this.inventoryWarningSettingResolver.resolveExpireWarningDays();
            // 加载候选：经 InventoryItem QS 最晚效期批近效（B.6.4，仅本聚合表）
            List<InventoryProductLatestExpiryNearModel> expireRows =
                this.inventoryItemQueryService.listProductKeysWhoseLatestLotNearExpiry(expireDays, productTableName);
            LocalDate today = LocalDate.now();
            for (InventoryProductLatestExpiryNearModel row : expireRows) {
                // 行键残缺无法进入勾选列表
                if (row == null || row.getItemId() == null || StringUtils.isEmpty(row.getItemTable())) {
                    continue;
                }
                String composite = ProductDefinitionKeyUtil.buildCompositeKey(row.getItemTable(), row.getItemId());
                PurchaseWarningHit hit = byKey.get(composite);
                // 同品种仅一行：首次创建或仅补效期标记
                if (hit == null) {
                    hit = new PurchaseWarningHit(row.getItemTable().trim(), row.getItemId());
                    byKey.put(composite, hit);
                }
                hit.markHitExpire();
                LocalDateTime latest = row.getLatestExpirationDate();
                // 有最晚效期则回填剩余天数，供弹窗展示
                if (latest != null) {
                    hit.applyLatestExpiration(latest.toLocalDate(), today);
                }
            }
        }

        // 低储：仅低于最低 / 全局包装阈值（BR-WHS-PO-015，不含超储）
        if (needLowStock) {
            BigDecimal globalPackage =
                this.inventoryWarningSettingResolver.resolveMinPackageQuantityWarning();
            List<InventoryProductQuantityAggregateModel> aggregates =
                this.inventoryItemQueryService.listHospitalWideProductQuantityAggregates();
            Set<ProductDefinitionBatchLookup> lookups = new LinkedHashSet<>();
            for (InventoryProductQuantityAggregateModel aggregate : aggregates) {
                // 无效汇总行跳过
                if (aggregate == null || aggregate.getItemId() == null
                    || StringUtils.isEmpty(aggregate.getItemTable())) {
                    continue;
                }
                // 仅表头项目类型对应表
                if (!productTableName.equals(aggregate.getItemTable().trim())) {
                    continue;
                }
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(aggregate.getItemTable().trim());
                lookup.setProductDefinitionId(aggregate.getItemId());
                lookups.add(lookup);
            }
            // 加载候选：目录警戒量与换算比（B.6.4）
            Map<String, ProductDefinitionDisplayDto> products =
                this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookups));
            for (InventoryProductQuantityAggregateModel aggregate : aggregates) {
                if (aggregate == null || aggregate.getItemId() == null
                    || StringUtils.isEmpty(aggregate.getItemTable())) {
                    continue;
                }
                // 与表头药/耗不一致则丢弃
                if (!productTableName.equals(aggregate.getItemTable().trim())) {
                    continue;
                }
                BigDecimal totalSplit = aggregate.getTotalSplitQuantity();
                // 合计为 null 视为全院断货 0，仍可按低于最低命中
                if (totalSplit == null) {
                    totalSplit = BigDecimal.ZERO;
                }
                String composite =
                    ProductDefinitionKeyUtil.buildCompositeKey(aggregate.getItemTable(), aggregate.getItemId());
                ProductDefinitionDisplayDto product = products.get(composite);
                // 未命中「低于最低」则跳过（超储不进订货快捷）
                if (!this.matchesBelowMinOnly(product, totalSplit, globalPackage)) {
                    continue;
                }
                PurchaseWarningHit hit = byKey.get(composite);
                // 可能已因效期入表：补低储标记与包装量
                if (hit == null) {
                    hit = new PurchaseWarningHit(aggregate.getItemTable().trim(), aggregate.getItemId());
                    byKey.put(composite, hit);
                }
                hit.markHitLowStock(totalSplit, this.toPackageQuantity(product, totalSplit));
            }
        }

        return new ArrayList<>(byKey.values());
    }

    /**
     * 仅低于最低警戒或全局包装阈值；不含高于最高（订货专用）。
     *
     * @param product                           目录展示
     * @param totalSplit                        全院拆零合计
     * @param globalMaxPackageQuantityExclusive 全局包装上限（不含）
     * @return true 命中低储
     */
    private boolean matchesBelowMinOnly(
        ProductDefinitionDisplayDto product,
        BigDecimal totalSplit,
        BigDecimal globalMaxPackageQuantityExclusive) {
        BigDecimal catalogMin = null;
        if (product != null) {
            catalogMin = product.getItemMinQuantity();
        }
        // 已维护最低 → 按库存单位换算比较
        if (catalogMin != null) {
            BigDecimal stockRatio = null;
            if (product != null) {
                stockRatio = product.getStockUnitRatio();
            }
            // 无换算比无法落到拆零口径，跳过以免误报
            if (stockRatio == null || stockRatio.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
            BigDecimal minSplit = catalogMin.multiply(stockRatio);
            return totalSplit.compareTo(minSplit) < 0;
        }
        // 未维护最低 → 回落全局包装阈值（订货：只要最低缺失即回落，不依赖最高是否维护）
        if (globalMaxPackageQuantityExclusive == null
            || globalMaxPackageQuantityExclusive.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal packageRatio = null;
        if (product != null) {
            packageRatio = product.getPackageUnitRatio();
        }
        // 无包装换算比无法用全局阈值，跳过
        if (packageRatio == null || packageRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal thresholdSplit = globalMaxPackageQuantityExclusive.multiply(packageRatio);
        return totalSplit.compareTo(thresholdSplit) < 0;
    }

    /**
     * 拆零合计换算为包装量（展示用）；无包装换算比时返回 null。
     *
     * @param product    目录
     * @param totalSplit 全院拆零
     * @return 包装量或 null
     */
    private BigDecimal toPackageQuantity(ProductDefinitionDisplayDto product, BigDecimal totalSplit) {
        // 缺目录或合计无法展示包装量
        if (product == null || totalSplit == null) {
            return null;
        }
        BigDecimal packageRatio = product.getPackageUnitRatio();
        // 无有效包装换算比时不展示，避免除零
        if (packageRatio == null || packageRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalSplit.divide(packageRatio, 4, RoundingMode.HALF_UP);
    }

    /**
     * 订货预警命中中间行（供 AppService Asm）。
     */
    @Getter
    public static final class PurchaseWarningHit {

        /** 物品表名。 */
        private final String itemTable;

        /** 物品定义主键。 */
        private final Long itemId;

        /** 是否命中最晚效期批近效。 */
        private boolean hitExpire;

        /** 是否命中低储。 */
        private boolean hitLowStock;

        /** 最晚效期批的效期日。 */
        private LocalDate latestExpirationDate;

        /** 距今天数（可负）。 */
        private Integer remainingDays;

        /** 全院拆零合计。 */
        private BigDecimal hospitalWideSplitQty;

        /** 全院合计换算包装量。 */
        private BigDecimal hospitalWidePackageQty;

        /**
         * 构造空白命中壳，后续由效期/低储路径补标记。
         *
         * @param itemTable 物品表
         * @param itemId    定义主键
         */
        public PurchaseWarningHit(String itemTable, Long itemId) {
            this.itemTable = itemTable;
            this.itemId = itemId;
        }

        /**
         * 标记命中效期筛选。
         */
        void markHitExpire() {
            this.hitExpire = true;
        }

        /**
         * 回填最晚效期日与剩余天数。
         *
         * @param latestExpiration 最晚效期日
         * @param today            业务当日
         */
        void applyLatestExpiration(LocalDate latestExpiration, LocalDate today) {
            this.latestExpirationDate = latestExpiration;
            this.remainingDays = (int) ChronoUnit.DAYS.between(today, latestExpiration);
        }

        /**
         * 标记命中低储并写入全院量展示。
         *
         * @param totalSplit             全院拆零合计
         * @param hospitalWidePackageQty 换算包装量，可空
         */
        void markHitLowStock(BigDecimal totalSplit, BigDecimal hospitalWidePackageQty) {
            this.hitLowStock = true;
            this.hospitalWideSplitQty = totalSplit;
            this.hospitalWidePackageQty = hospitalWidePackageQty;
        }
    }
}
