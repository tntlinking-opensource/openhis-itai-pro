/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 医生站/划价目录当前页药耗库存批量预取结果（候选库位、默认发药点、可用库存行、发放预占）。
 *
 * <p>键均为 {@link com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil#buildCompositeKey} 复合键。</p>
 */
public final class ClinicalCatalogInventoryPageBatch {

    /** 产品复合键 → 模板适用候选库位（已按请求 pharmacyLocationId 收窄）。 */
    private final Map<String, List<Long>> candidateLocationIdsByProductKey = new HashMap<>();

    /** 产品复合键 → 默认发药/发材库位。 */
    private final Map<String, Long> defaultDispenseLocationIdByProductKey = new HashMap<>();

    /** 产品复合键 → 候选库位上可用库存行（未扣预占）。 */
    private final Map<String, List<InventoryCatalogAvailabilityRowModel>> physicalRowsByProductKey = new HashMap<>();

    /** 产品复合键 → 拆零单位预占映射（tripleCompositeKey → 数量）。 */
    private final Map<String, Map<String, BigDecimal>> occupancyByProductKey = new HashMap<>();

    /**
     * 记录某产品的候选库位。
     *
     * @param productKey    产品复合键
     * @param locationIds   候选库位；null 视为空
     */
    public void putCandidateLocationIds(String productKey, List<Long> locationIds) {
        if (productKey == null) {
            return;
        }
        if (locationIds == null || locationIds.isEmpty()) {
            this.candidateLocationIdsByProductKey.put(productKey, List.of());
            return;
        }
        this.candidateLocationIdsByProductKey.put(productKey, List.copyOf(locationIds));
    }

    /**
     * 记录某产品的默认发药库位。
     *
     * @param productKey 产品复合键
     * @param locationId 默认库位；可空
     */
    public void putDefaultDispenseLocationId(String productKey, Long locationId) {
        if (productKey == null || locationId == null) {
            return;
        }
        this.defaultDispenseLocationIdByProductKey.put(productKey, locationId);
    }

    /**
     * 记录某产品的物理可用库存行。
     *
     * @param productKey 产品复合键
     * @param rows       库存行；null 视为空
     */
    public void putPhysicalRows(String productKey, List<InventoryCatalogAvailabilityRowModel> rows) {
        if (productKey == null) {
            return;
        }
        if (rows == null || rows.isEmpty()) {
            this.physicalRowsByProductKey.put(productKey, List.of());
            return;
        }
        this.physicalRowsByProductKey.put(productKey, List.copyOf(rows));
    }

    /**
     * 记录某产品的发放预占（拆零单位）。
     *
     * @param productKey 产品复合键
     * @param occupancy  预占映射；null 视为空
     */
    public void putOccupancy(String productKey, Map<String, BigDecimal> occupancy) {
        if (productKey == null) {
            return;
        }
        if (occupancy == null || occupancy.isEmpty()) {
            this.occupancyByProductKey.put(productKey, Map.of());
            return;
        }
        this.occupancyByProductKey.put(productKey, Map.copyOf(occupancy));
    }

    /**
     * 取产品候选库位。
     *
     * @param productKey 产品复合键
     * @return 候选库位；未预取时为空列表
     */
    public List<Long> getCandidateLocationIds(String productKey) {
        return this.candidateLocationIdsByProductKey.getOrDefault(productKey, List.of());
    }

    /**
     * 取产品默认发药库位。
     *
     * @param productKey 产品复合键
     * @return 默认库位；未命中为 null
     */
    public Long getDefaultDispenseLocationId(String productKey) {
        return this.defaultDispenseLocationIdByProductKey.get(productKey);
    }

    /**
     * 取产品物理可用库存行。
     *
     * @param productKey 产品复合键
     * @return 库存行；未预取时为空列表
     */
    public List<InventoryCatalogAvailabilityRowModel> getPhysicalRows(String productKey) {
        return this.physicalRowsByProductKey.getOrDefault(productKey, List.of());
    }

    /**
     * 取产品发放预占映射。
     *
     * @param productKey 产品复合键
     * @return 预占映射；未预取时为空 Map
     */
    public Map<String, BigDecimal> getOccupancy(String productKey) {
        Map<String, BigDecimal> occupancy = this.occupancyByProductKey.get(productKey);
        if (occupancy == null) {
            return Collections.emptyMap();
        }
        return occupancy;
    }
}
