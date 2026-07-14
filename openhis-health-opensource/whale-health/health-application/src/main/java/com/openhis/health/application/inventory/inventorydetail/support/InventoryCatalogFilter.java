/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.support;

import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 库存明细筛选：按药品/耗材定义 QueryService 预解析 {@code item_id}，避免 inventory 领域 Criteria 跨包 JOIN（B.6.1）。
 */
@Component
@RequiredArgsConstructor
public class InventoryCatalogFilter {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /**
     * 将类型/医保等级筛选解析为定义主键列表并写入 {@link WarehouseInventoryDetailPageQuery}。
     *
     * @param query 药库库存明细筛选入参（原地写入 resolved* 字段）
     */
    public void enrichResolvedDefinitionIds(WarehouseInventoryDetailPageQuery query) {
        if (query == null) {
            return;
        }
        boolean hasMedicationCategory = query.getMedicationCategoryCodes() != null
            && !query.getMedicationCategoryCodes().isEmpty();
        boolean hasDeviceCategory = query.getDeviceCategoryCodes() != null
            && !query.getDeviceCategoryCodes().isEmpty();
        boolean hasChrgitmLv = query.getChrgitmLvList() != null && !query.getChrgitmLvList().isEmpty();
        if (hasMedicationCategory) {
            query.setResolvedMedicationDefinitionIds(
                this.medicationDefinitionQueryService.listIdsByCategoryCodesIn(query.getMedicationCategoryCodes()));
        }
        if (hasDeviceCategory) {
            query.setResolvedDeviceDefinitionIds(
                this.deviceDefinitionQueryService.listIdsByCategoryCodesIn(query.getDeviceCategoryCodes()));
        }
        if (hasChrgitmLv) {
            List<Long> medByLv = this.medicationDefinitionQueryService.listIdsByChrgitmLvIn(query.getChrgitmLvList());
            List<Long> devByLv = this.deviceDefinitionQueryService.listIdsByChrgitmLvIn(query.getChrgitmLvList());
            if (hasMedicationCategory) {
                query.setResolvedMedicationDefinitionIds(
                    this.intersectIds(query.getResolvedMedicationDefinitionIds(), medByLv));
            } else {
                query.setResolvedMedicationDefinitionIds(medByLv);
            }
            if (hasDeviceCategory) {
                query.setResolvedDeviceDefinitionIds(
                    this.intersectIds(query.getResolvedDeviceDefinitionIds(), devByLv));
            } else {
                query.setResolvedDeviceDefinitionIds(devByLv);
            }
        }
    }

    /**
     * 将关键字解析为药品/耗材定义主键列表并写入 {@link WarehouseInventoryDetailPageQuery}（与 openhis 编码/商品名检索对齐）。
     *
     * @param query 药库库存明细筛选入参（原地写入 resolved*ForKeyword 字段）
     */
    public void enrichKeywordDefinitionIds(WarehouseInventoryDetailPageQuery query) {
        if (query == null) {
            return;
        }
        if (StringUtils.isEmpty(query.getKeyword())) {
            return;
        }
        query.setResolvedMedicationDefinitionIdsForKeyword(
            this.medicationDefinitionQueryService.listIdsByInventoryDetailKeyword(query.getKeyword()));
        query.setResolvedDeviceDefinitionIdsForKeyword(
            this.deviceDefinitionQueryService.listIdsByInventoryDetailKeyword(query.getKeyword()));
    }

    /**
     * 将品名/编码关键字解析为药品/耗材定义主键并写入 {@link WarehouseDocumentDetailPageQuery}（与库存明细检索口径对齐）。
     *
     * @param query 单据明细分页筛选入参（原地写入 resolved*ForKeyword 字段）
     */
    public void enrichDocumentDetailKeywordDefinitionIds(WarehouseDocumentDetailPageQuery query) {
        if (query == null) {
            return;
        }
        if (StringUtils.isEmpty(query.getProductKeyword())) {
            return;
        }
        query.setResolvedMedicationDefinitionIdsForKeyword(
            this.medicationDefinitionQueryService.listIdsByInventoryDetailKeyword(query.getProductKeyword()));
        query.setResolvedDeviceDefinitionIdsForKeyword(
            this.deviceDefinitionQueryService.listIdsByInventoryDetailKeyword(query.getProductKeyword()));
    }

    /**
     * 药品/耗材定义主键列表求交（医保等级与类目筛选叠加时使用）。
     *
     * @param left  已按类目收窄的 id 列表，可为 null
     * @param right 按医保等级解析的 id 列表，可为 null
     * @return 交集；任一侧为空时返回空列表（避免误返回「全量」）
     */
    private List<Long> intersectIds(List<Long> left, List<Long> right) {
        if (left == null || left.isEmpty()) {
            if (right == null || right.isEmpty()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(right);
        }
        if (right == null || right.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> rightSet = new HashSet<>(right);
        List<Long> intersection = new ArrayList<>();
        for (Long id : left) {
            if (id != null && rightSet.contains(id)) {
                intersection.add(id);
            }
        }
        return intersection;
    }
}
