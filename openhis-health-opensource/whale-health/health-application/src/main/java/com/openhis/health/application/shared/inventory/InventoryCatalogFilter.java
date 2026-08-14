/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inventory;

import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.DocInventoryItemSnapshotPageQuery;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
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
 * 库存目录筛选预解析：按药品/耗材定义 QueryService 预解析 {@code item_id}，避免 inventory 领域 Criteria 跨包 JOIN（B.6.4）。
 *
 * <p>落点 {@code application.shared.inventory}：库存明细、单据明细、批号选品等多 Feature 共用；
 * 仅依赖定义聚合 QS，禁止 Feature Request/Response。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryCatalogFilter {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /**
     * 将类型/医保等级/基药筛选解析为定义主键列表并写入 {@link WarehouseInventoryDetailPageQuery}。
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
        boolean hasBasicFlag = query.getBasicFlag() != null;
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
        // 基药仅作用于药品定义：与类目/医保等级已解析主键求交；无前置条件时直接写入
        if (hasBasicFlag) {
            List<Long> medByBasic =
                this.medicationDefinitionQueryService.listIdsByBasicFlag(query.getBasicFlag());
            if (hasMedicationCategory || hasChrgitmLv) {
                query.setResolvedMedicationDefinitionIds(
                    this.intersectIds(query.getResolvedMedicationDefinitionIds(), medByBasic));
            } else {
                query.setResolvedMedicationDefinitionIds(medByBasic);
            }
        }
    }

    /**
     * 备份静表：按基药标识预解析药品定义主键并写入 {@link DocInventoryItemSnapshotPageQuery}。
     *
     * <p>静表无 {@code basic_flag} 列，须经药典 QueryService 预解析后按 {@code item_id} 收窄（B.6.4）。</p>
     *
     * @param query 备份筛选入参（原地写入 resolvedMedicationDefinitionIds）
     */
    public void enrichSnapshotBasicFlagDefinitionIds(DocInventoryItemSnapshotPageQuery query) {
        if (query == null) {
            return;
        }
        if (query.getBasicFlag() == null) {
            return;
        }
        query.setResolvedMedicationDefinitionIds(
            this.medicationDefinitionQueryService.listIdsByBasicFlag(query.getBasicFlag()));
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
     * 库房/药房/科室库存选品：按关键字预解析药品/耗材定义主键写入 {@link InventoryItemPageQuery}。
     *
     * <p>库存行仅冗余通用名与助记码；商品名/编码须经定义 QueryService 预解析后按 {@code item_id} OR（B.6.4）。
     * 已指定 {@code itemTable} 时只解析对应定义表，避免多余全表扫。</p>
     *
     * @param query 库存项分页入参（原地写入 resolved*ForKeyword；无关键字则跳过）
     */
    public void enrichInventoryItemPageKeywordDefinitionIds(InventoryItemPageQuery query) {
        if (query == null) {
            return;
        }
        if (StringUtils.isEmpty(query.getKeyword())) {
            return;
        }
        String itemTable = null;
        if (StringUtils.isNotEmpty(query.getItemTable())) {
            itemTable = query.getItemTable().trim();
        }
        // 未指定表或药品表：解析药品定义（含商品名）
        boolean resolveMedication = itemTable == null
            || TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable);
        // 未指定表或耗材表：解析耗材定义（含商品名）
        boolean resolveDevice = itemTable == null
            || TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable);
        if (resolveMedication) {
            query.setResolvedMedicationDefinitionIdsForKeyword(
                this.medicationDefinitionQueryService.listIdsByInventoryDetailKeyword(query.getKeyword()));
        }
        if (resolveDevice) {
            query.setResolvedDeviceDefinitionIdsForKeyword(
                this.deviceDefinitionQueryService.listIdsByInventoryDetailKeyword(query.getKeyword()));
        }
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
