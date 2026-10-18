/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.inventory;

import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.document.inventoryitemsnapshot.queries.DocInventoryItemSnapshotPageQuery;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.zxplat.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
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
     * 将类型/医保等级/重点药类型筛选解析为定义主键列表并写入 {@link WarehouseInventoryDetailPageQuery}。
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
        boolean hasKeyDrugType = StringUtils.isNotEmpty(query.getKeyDrugType());
        boolean hasMedicineProperty = StringUtils.isNotEmpty(query.getMedicinePropertyCode());
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
        // 重点药类型 / 药品性质仅作用于药品定义：与类目/医保等级已解析主键求交；两框同时选时彼此 AND
        if (hasKeyDrugType || hasMedicineProperty) {
            List<Long> medByKeyFilter = this.resolveKeyDrugAndMedicinePropertyIds(
                query.getKeyDrugType(),
                query.getMedicinePropertyCode());
            if (hasMedicationCategory || hasChrgitmLv) {
                query.setResolvedMedicationDefinitionIds(
                    this.intersectIds(query.getResolvedMedicationDefinitionIds(), medByKeyFilter));
            } else {
                query.setResolvedMedicationDefinitionIds(medByKeyFilter);
            }
        }
    }

    /**
     * 备份静表：按重点药类型/药品性质预解析药品定义主键并写入 {@link DocInventoryItemSnapshotPageQuery}。
     *
     * <p>静表无药典标识列，须经药典 QueryService 预解析后按 {@code item_id} 收窄（B.6.4）。</p>
     *
     * @param query 备份筛选入参（原地写入 resolvedMedicationDefinitionIds）
     */
    public void enrichSnapshotKeyDrugTypeDefinitionIds(DocInventoryItemSnapshotPageQuery query) {
        if (query == null) {
            return;
        }
        if (StringUtils.isEmpty(query.getKeyDrugType())
            && StringUtils.isEmpty(query.getMedicinePropertyCode())) {
            return;
        }
        query.setResolvedMedicationDefinitionIds(
            this.resolveKeyDrugAndMedicinePropertyIds(
                query.getKeyDrugType(),
                query.getMedicinePropertyCode()));
    }

    /**
     * 按重点药类型与药品性质预解析药典主键；两框均非空时求交（AND）。
     *
     * @param keyDrugTypeCode         重点药类型 code（四旗）；空则不按四旗收窄
     * @param medicinePropertyCode    药品性质 code（麻/精）；空则不按性质收窄
     * @return 命中主键；无命中返回空列表
     */
    private List<Long> resolveKeyDrugAndMedicinePropertyIds(
        String keyDrugTypeCode,
        String medicinePropertyCode) {
        boolean hasKeyDrug = StringUtils.isNotEmpty(keyDrugTypeCode);
        boolean hasProperty = StringUtils.isNotEmpty(medicinePropertyCode);
        // 仅重点药
        if (hasKeyDrug && !hasProperty) {
            return this.medicationDefinitionQueryService.listIdsByKeyDrugType(keyDrugTypeCode);
        }
        // 仅药品性质
        if (!hasKeyDrug && hasProperty) {
            return this.medicationDefinitionQueryService.listIdsByKeyDrugType(medicinePropertyCode);
        }
        // 两框同时选：AND 求交
        List<Long> byKey =
            this.medicationDefinitionQueryService.listIdsByKeyDrugType(keyDrugTypeCode);
        List<Long> byProperty =
            this.medicationDefinitionQueryService.listIdsByKeyDrugType(medicinePropertyCode);
        return this.intersectIds(byKey, byProperty);
    }

    /**
     * 将关键字解析为药品/耗材定义主键列表并写入 {@link WarehouseInventoryDetailPageQuery}（与历史 编码/商品名检索对齐）。
     *
     * <p>已指定 {@code itemTable} 时只解析对应定义表，避免多余全表扫。</p>
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
        String itemTable = null;
        // 已指定项目类型时只扫对应定义表
        if (StringUtils.isNotEmpty(query.getItemTable())) {
            itemTable = query.getItemTable().trim();
        }
        // 未指定表或药品表：解析药品定义（含商品名/编码）
        boolean resolveMedication = itemTable == null
            || TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable);
        // 未指定表或耗材表：解析耗材定义（含商品名/编码）
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
        String itemTable = null;
        if (StringUtils.isNotEmpty(query.getItemTable())) {
            itemTable = query.getItemTable().trim();
        }
        // 已指定项目分类时只解析对应定义表，与库存选品口径一致
        boolean resolveMedication = itemTable == null
            || TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable);
        boolean resolveDevice = itemTable == null
            || TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable);
        if (resolveMedication) {
            query.setResolvedMedicationDefinitionIdsForKeyword(
                this.medicationDefinitionQueryService.listIdsByInventoryDetailKeyword(query.getProductKeyword()));
        }
        if (resolveDevice) {
            query.setResolvedDeviceDefinitionIdsForKeyword(
                this.deviceDefinitionQueryService.listIdsByInventoryDetailKeyword(query.getProductKeyword()));
        }
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
