/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.product;

import com.zxplat.domain.custom.servicelocationtemplate.entity.ServiceLocationTemplate;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.devicedispense.queries.model.DeviceDispenseOrderCatalogOccupancyModel;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 临床目录当前页药耗库存批量预取（IF-DOR-11 / IF-IP-DOR-11）。
 *
 * <p>将「取药科室模板一次加载 + 按表批量库存/预占」收敛为单次预取，避免装配器按行 N+1 打库。</p>
 */
@Component
@RequiredArgsConstructor
public class ClinicalCatalogInventoryPageBatchLoader {

    /** 取药科室模板库位解析（支持机构模板一次加载后的内存 match）。 */
    private final ClinicalCatalogDispenseLocationResolver clinicalCatalogDispenseLocationResolver;

    /** 库存可用量批量读。 */
    private final InventoryItemQueryService inventoryItemQueryService;

    /** 药品发放预占批量读。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 耗材发放预占批量读。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /**
     * 对当前页产品定义批量预取药耗候选库位、默认发药点、可用库存与发放预占。
     *
     * <p>诊疗行忽略；机构为空时返回空批次（装配器按无库存展示）。</p>
     *
     * @param content            当前页产品读模型
     * @param organizationId     开立/接诊机构
     * @param pharmacyLocationId 可选指定药房（收窄候选）
     * @return 按产品复合键索引的预取结果
     */
    public ClinicalCatalogInventoryPageBatch preload(
        List<ProductDefinitionDisplayDto> content,
        Long organizationId,
        Long pharmacyLocationId
    ) {
        ClinicalCatalogInventoryPageBatch batch = new ClinicalCatalogInventoryPageBatch();
        if (content == null || content.isEmpty() || organizationId == null) {
            return batch;
        }
        // Pre：机构模板一次加载，后续候选/默认库位均在内存 calcScore，避免 N×findAllByOrganizationId
        List<ServiceLocationTemplate> organizationTemplates =
            clinicalCatalogDispenseLocationResolver.loadOrganizationTemplateRows(organizationId);

        List<ProductDefinitionDisplayDto> medicationRows = new ArrayList<>();
        List<ProductDefinitionDisplayDto> deviceRows = new ArrayList<>();
        for (ProductDefinitionDisplayDto productRow : content) {
            if (productRow == null || productRow.getProductDefinitionId() == null) {
                continue;
            }
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productRow.getProductTableName())) {
                medicationRows.add(productRow);
                continue;
            }
            if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
                deviceRows.add(productRow);
            }
        }
        this.fillLocationAndInventory(batch, medicationRows, organizationTemplates, pharmacyLocationId);
        this.fillLocationAndInventory(batch, deviceRows, organizationTemplates, pharmacyLocationId);
        return batch;
    }

    /**
     * 对同一物理表（药或耗）批量解析库位并一次拉取库存/预占，再按产品键回填批次。
     *
     * @param batch                  批次结果
     * @param productRows            同表产品行
     * @param organizationTemplates  机构模板快照
     * @param pharmacyLocationId     可选指定药房
     */
    private void fillLocationAndInventory(
        ClinicalCatalogInventoryPageBatch batch,
        List<ProductDefinitionDisplayDto> productRows,
        List<ServiceLocationTemplate> organizationTemplates,
        Long pharmacyLocationId
    ) {
        if (productRows == null || productRows.isEmpty()) {
            return;
        }
        String productTableName = productRows.get(0).getProductTableName();
        LinkedHashSet<Long> unionCandidateLocs = new LinkedHashSet<>();
        LinkedHashSet<Long> productDefinitionIds = new LinkedHashSet<>();
        for (ProductDefinitionDisplayDto productRow : productRows) {
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                productRow.getProductTableName(), productRow.getProductDefinitionId());
            List<Long> candidateLocs = clinicalCatalogDispenseLocationResolver.listApplicableCandidateLocationIdsFromLoaded(
                organizationTemplates,
                productRow.getProductTableName(),
                clinicalCatalogDispenseLocationResolver.resolveDistributionCategoryCode(productRow),
                productRow.getProductDefinitionId(),
                productRow.getMethodCode(),
                productRow.getTypeCode(),
                pharmacyLocationId
            );
            batch.putCandidateLocationIds(productKey, candidateLocs);
            // 先落模板序首；拉完库存后再按 BR-SLT-005 有库存下探覆盖
            if (candidateLocs != null && !candidateLocs.isEmpty()) {
                batch.putDefaultDispenseLocationId(productKey, candidateLocs.get(0));
            }
            unionCandidateLocs.addAll(candidateLocs);
            productDefinitionIds.add(productRow.getProductDefinitionId());
        }
        if (unionCandidateLocs.isEmpty() || productDefinitionIds.isEmpty()) {
            for (ProductDefinitionDisplayDto productRow : productRows) {
                String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                    productRow.getProductTableName(), productRow.getProductDefinitionId());
                batch.putPhysicalRows(productKey, List.of());
                batch.putOccupancy(productKey, Map.of());
            }
            return;
        }
        List<Long> candidateLocList = new ArrayList<>(unionCandidateLocs);
        List<Long> productIdList = new ArrayList<>(productDefinitionIds);
        // Cmd：同表一次批量拉可用库存（多品 × 多库位）
        List<InventoryCatalogAvailabilityRowModel> physicalRows =
            inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                productTableName, productIdList, candidateLocList);
        // 本页该表无任何可用库存行时，跳过发放预占；默认库位保持候选序首
        if (physicalRows == null || physicalRows.isEmpty()) {
            for (ProductDefinitionDisplayDto productRow : productRows) {
                String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                    productRow.getProductTableName(), productRow.getProductDefinitionId());
                batch.putPhysicalRows(productKey, List.of());
                batch.putOccupancy(productKey, Map.of());
            }
            return;
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTableName)) {
            List<MedicationDispenseOrderCatalogOccupancyModel> occupancyRows =
                medicationDispenseQueryService.listMedicationDispensesForOrderCatalogOccupancy(
                    candidateLocList, productIdList);
            for (ProductDefinitionDisplayDto productRow : productRows) {
                this.putMedicationPartition(batch, productRow, physicalRows, occupancyRows);
                // Asm：药/耗同口径 — 粒度序内按 priority 有库存下探写 defaultDispenseLocationId
                this.overlayDefaultDispenseLocationByStock(batch, productRow, physicalRows);
            }
            return;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTableName)) {
            List<DeviceDispenseOrderCatalogOccupancyModel> occupancyRows =
                deviceDispenseQueryService.listDeviceDispensesForOrderCatalogOccupancy(
                    candidateLocList, productIdList);
            for (ProductDefinitionDisplayDto productRow : productRows) {
                this.putDevicePartition(batch, productRow, physicalRows, occupancyRows);
                // Asm：耗材与药品同一套 BR-SLT-005 下探
                this.overlayDefaultDispenseLocationByStock(batch, productRow, physicalRows);
            }
        }
    }

    /**
     * 用候选序 + 正库存覆盖目录默认发药/发材点（{@code BR-SLT-005}：1 先试，无货再试下一档）。
     *
     * @param batch        批次
     * @param productRow   当前产品行
     * @param physicalRows 同表批量可用库存
     */
    private void overlayDefaultDispenseLocationByStock(
        ClinicalCatalogInventoryPageBatch batch,
        ProductDefinitionDisplayDto productRow,
        List<InventoryCatalogAvailabilityRowModel> physicalRows
    ) {
        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
            productRow.getProductTableName(), productRow.getProductDefinitionId());
        List<Long> candidateLocs = batch.getCandidateLocationIds(productKey);
        Long picked = clinicalCatalogDispenseLocationResolver.pickFirstLocationWithPositiveStock(
            candidateLocs, productRow.getProductDefinitionId(), physicalRows);
        // 候选空时保持未写入；有命中则覆盖序首占位
        if (picked != null) {
            batch.putDefaultDispenseLocationId(productKey, picked);
        }
    }

    /**
     * 将批量库存/药品预占按单品候选库位切片写入批次。
     *
     * @param batch          批次
     * @param productRow     当前药品行
     * @param physicalRows   同表批量库存
     * @param occupancyRows  同表批量预占
     */
    private void putMedicationPartition(
        ClinicalCatalogInventoryPageBatch batch,
        ProductDefinitionDisplayDto productRow,
        List<InventoryCatalogAvailabilityRowModel> physicalRows,
        List<MedicationDispenseOrderCatalogOccupancyModel> occupancyRows
    ) {
        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
            productRow.getProductTableName(), productRow.getProductDefinitionId());
        Long productDefinitionId = productRow.getProductDefinitionId();
        List<Long> candidateLocs = batch.getCandidateLocationIds(productKey);
        List<InventoryCatalogAvailabilityRowModel> rowsForProduct = new ArrayList<>();
        for (InventoryCatalogAvailabilityRowModel inventoryRow : physicalRows) {
            if (inventoryRow == null) {
                continue;
            }
            if (!productDefinitionId.equals(inventoryRow.getItemId())) {
                continue;
            }
            if (!candidateLocs.contains(inventoryRow.getLocationId())) {
                continue;
            }
            rowsForProduct.add(inventoryRow);
        }
        batch.putPhysicalRows(productKey, rowsForProduct);
        List<MedicationDispenseOrderCatalogOccupancyModel> occForProduct = new ArrayList<>();
        for (MedicationDispenseOrderCatalogOccupancyModel occupancyRow : occupancyRows) {
            if (occupancyRow != null && productDefinitionId.equals(occupancyRow.getMedicationId())) {
                occForProduct.add(occupancyRow);
            }
        }
        Map<String, BigDecimal> occupancy =
            DoctorStationAdviceInventoryUnitQuantityHelper.mergeMedicationOccupancyInSplitUnits(
                occForProduct, productRow, productRow.getProductTableName());
        batch.putOccupancy(productKey, occupancy);
    }

    /**
     * 将批量库存/耗材预占按单品候选库位切片写入批次。
     *
     * @param batch          批次
     * @param productRow     当前耗材行
     * @param physicalRows   同表批量库存
     * @param occupancyRows  同表批量预占
     */
    private void putDevicePartition(
        ClinicalCatalogInventoryPageBatch batch,
        ProductDefinitionDisplayDto productRow,
        List<InventoryCatalogAvailabilityRowModel> physicalRows,
        List<DeviceDispenseOrderCatalogOccupancyModel> occupancyRows
    ) {
        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
            productRow.getProductTableName(), productRow.getProductDefinitionId());
        Long productDefinitionId = productRow.getProductDefinitionId();
        List<Long> candidateLocs = batch.getCandidateLocationIds(productKey);
        List<InventoryCatalogAvailabilityRowModel> rowsForProduct = new ArrayList<>();
        for (InventoryCatalogAvailabilityRowModel inventoryRow : physicalRows) {
            if (inventoryRow == null) {
                continue;
            }
            if (!productDefinitionId.equals(inventoryRow.getItemId())) {
                continue;
            }
            if (!candidateLocs.contains(inventoryRow.getLocationId())) {
                continue;
            }
            rowsForProduct.add(inventoryRow);
        }
        batch.putPhysicalRows(productKey, rowsForProduct);
        List<DeviceDispenseOrderCatalogOccupancyModel> occForProduct = new ArrayList<>();
        for (DeviceDispenseOrderCatalogOccupancyModel occupancyRow : occupancyRows) {
            if (occupancyRow != null && productDefinitionId.equals(occupancyRow.getDeviceDefId())) {
                occForProduct.add(occupancyRow);
            }
        }
        Map<String, BigDecimal> occupancy =
            DoctorStationAdviceInventoryUnitQuantityHelper.mergeDeviceOccupancyInSplitUnits(
                occForProduct, productRow, productRow.getProductTableName());
        batch.putOccupancy(productKey, occupancy);
    }
}
