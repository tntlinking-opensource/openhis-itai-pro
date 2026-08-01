/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.support;

import com.openhis.health.application.basic.medicationcatalog.request.CreateMedicationCatalogRequest;
import com.openhis.health.domain.share.constants.MedicationCatalogImportConstants;
import com.openhis.health.domain.share.events.medication.medicationdefinition.MedicationCatalogImportedEvent;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 药品目录导入写段：事务内逐行调用创建回调并发布批次导入事件。
 */
@Service
@RequiredArgsConstructor
public class MedicationCatalogImportWriteSupport {

    private final IDomainEventPublisher domainEventPublisher;
    private final MedicationCatalogBusNoSequenceSync busNoSequenceSync;

    /**
     * 写段进度回调。
     */
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * @param processed 已写入条数
         * @param total     总条数
         */
        void onProgress(int processed, int total);
    }

    /**
     * 事务内逐行导入：映射为 {@link CreateMedicationCatalogRequest} 后委托 {@link ImportRowCreator}。
     * 已标记 {@link MedicationCatalogImportRow#isSkipExisting()} 的行跳过创建。
     *
     * @param batchNo          批次号
     * @param operatorId       操作人主键
     * @param orgId            机构主键（异步场景直接绑定，避免会话丢失）
     * @param rows             已通过校验的导入行
     * @param creator          单行创建回调
     * @param progressCallback 可选进度回调
     * @return 实际新建条数（不含跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public int importRows(
        String batchNo,
        Long operatorId,
        Long orgId,
        List<MedicationCatalogImportRow> rows,
        ImportRowCreator creator,
        ProgressCallback progressCallback
    ) {
        // Gate：写段入参
        if (rows == null || rows.isEmpty() || creator == null) {
            return 0;
        }
        // Pre：独立事务对齐 MD 序号，避免「药品业务编码冲突」
        busNoSequenceSync.syncFromExistingDefinitions();
        int total = rows.size();
        int processed = 0;
        int created = 0;
        // Cmd：逐行建档+定价（由 creator 封装）；库内已存在则跳过
        for (MedicationCatalogImportRow row : rows) {
            if (row != null && row.isSkipExisting()) {
                processed = processed + 1;
                if (progressCallback != null) {
                    progressCallback.onProgress(processed, total);
                }
                continue;
            }
            CreateMedicationCatalogRequest request = this.toCreateRequest(row, orgId);
            creator.create(request);
            created = created + 1;
            processed = processed + 1;
            if (progressCallback != null) {
                progressCallback.onProgress(processed, total);
            }
        }
        // Cmd 续：发布批次导入事件（数量为实际新建）
        MedicationCatalogImportedEvent event = new MedicationCatalogImportedEvent(
            batchNo, created, 0, operatorId);
        domainEventPublisher.publish(event);
        return created;
    }

    /**
     * 兼容旧签名：无进度回调、无显式 orgId。
     *
     * @return 实际新建条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int importRows(
        String batchNo,
        Long operatorId,
        List<MedicationCatalogImportRow> rows,
        ImportRowCreator creator
    ) {
        return this.importRows(batchNo, operatorId, null, rows, creator, null);
    }

    /**
     * 导入行 → 新建药品目录请求（ACTIVE 由领域创建路径保证；busNo/拼音五笔由领域采番）。
     */
    private CreateMedicationCatalogRequest toCreateRequest(MedicationCatalogImportRow row, Long orgId) {
        CreateMedicationCatalogRequest request = new CreateMedicationCatalogRequest();
        request.setName(row.getName());
        request.setMerchandiseName(row.getMerchandiseName());
        request.setCategoryCode(row.getCategoryCode());
        request.setManufacturerText(row.getManufacturerText());
        request.setTypeCode(row.getTypeCode());
        request.setApprovalNumber(row.getApprovalNumber());
        request.setNationalDrugCode(row.getNationalDrugCode());
        request.setYbNo(row.getYbNo());
        request.setChrgitmLv(row.getChrgitmLv());
        request.setMrSettlementTypeCode(row.getMrSettlementTypeCode());
        request.setYbMatchFlag(row.getYbMatchFlag());
        request.setYbType(row.getYbTypeCode());
        request.setPharmacologyCategoryCode(row.getPharmacologyCategoryCode());
        request.setTotalVolume(row.getTotalVolume());
        request.setDoseUnitCode(row.getDoseUnitCode());
        request.setDoseUnitRatio(row.getDoseUnitRatio());
        request.setDoseFormCode(row.getDoseFormCode());
        request.setSkinTestFlag(row.getSkinTestFlag());
        request.setInjectFlag(row.getInjectFlag());
        request.setUsageLimit(row.getUsageLimit());
        request.setMethodCode(row.getMethodCode());
        request.setRateCode(row.getRateCode());
        request.setDose(row.getDose());
        request.setMinUnit(row.getMinUnit());
        request.setMaxUnit(row.getMaxUnit());
        request.setDosageInstruction(row.getDosageInstructionCode());
        request.setRestrictedScope(row.getRestrictedScope());
        request.setDddCode(row.getDddCode());
        request.setDddUnitCode(row.getDddUnitCode());
        request.setPurchaseUnitCode(row.getPurchaseUnitCode());
        request.setSplitUnitCode(row.getSplitUnitCode());
        request.setPurchaseUnitRatio(row.getPurchaseUnitRatio());
        request.setPackageUnitCode(row.getPackageUnitCode());
        request.setPackageUnitRatio(row.getPackageUnitRatio());
        request.setTraceUnitCode(row.getTraceUnitCode());
        request.setTraceUnitRatio(row.getTraceUnitRatio());
        request.setStockUnitCode(row.getStockUnitCode());
        request.setStockUnitRatio(row.getStockUnitRatio());
        request.setPurchasePrice(row.getPurchasePrice());
        request.setRetailPrice(row.getRetailPrice());
        request.setMaximumRetailPrice(row.getMaximumRetailPrice());
        request.setPartAttributeEnum(row.getPartAttributeEnum());
        request.setThoPartAttributeEnum(row.getThoPartAttributeEnum());
        request.setItemMinQuantity(row.getItemMinQuantity());
        request.setItemMaxQuantity(row.getItemMaxQuantity());
        request.setDrug69Code(row.getDrug69Code());
        request.setBasicFlag(row.getBasicFlag());
        request.setChildrenFlag(row.getChildrenFlag());
        request.setSelfFlag(row.getSelfFlag());
        request.setRestrictedFlag(row.getRestrictedFlag());
        request.setAntibioticFlag(row.getAntibioticFlag());
        request.setRxFlag(row.getRxFlag());
        request.setDefinition(this.mergeDefinition(row.getDefinitionText(), row.getLegacyCode()));
        if (orgId != null) {
            request.setOrgId(orgId);
        }
        request.setSupplyId(null);
        return request;
    }

    /**
     * 合并药品说明与院内旧编码至 definition。
     */
    private String mergeDefinition(String definitionText, String legacyCode) {
        boolean hasDefinition = StringUtils.isNotEmpty(definitionText);
        boolean hasLegacy = StringUtils.isNotEmpty(legacyCode);
        if (!hasDefinition && !hasLegacy) {
            return null;
        }
        if (hasDefinition && !hasLegacy) {
            return definitionText.trim();
        }
        String legacyLine = MedicationCatalogImportConstants.LEGACY_CODE_DEFINITION_PREFIX + legacyCode.trim();
        if (!hasDefinition) {
            return legacyLine;
        }
        return definitionText.trim() + "\n" + legacyLine;
    }

    /**
     * 导入单行创建回调（由 AppService 传入 {@code this::createOneForImport} 等实现）。
     */
    @FunctionalInterface
    public interface ImportRowCreator {

        /**
         * 创建单条药品目录（含定价）。
         *
         * @param request 新建请求
         */
        void create(CreateMedicationCatalogRequest request);
    }
}
