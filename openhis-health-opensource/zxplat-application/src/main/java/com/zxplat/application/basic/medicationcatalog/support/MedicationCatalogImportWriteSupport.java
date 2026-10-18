/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.medicationcatalog.support;

import com.zxplat.application.basic.medicationcatalog.request.CreateMedicationCatalogRequest;
import com.zxplat.application.basic.supplier.support.SupplierBusNoSequenceSync;
import com.zxplat.domain.custom.supplier.command.RegisterSupplierCommand;
import com.zxplat.domain.custom.supplier.service.SupplierDomainService;
import com.zxplat.domain.custom.supplier.service.SupplierQueryService;
import com.zxplat.domain.share.constants.MedicationCatalogImportConstants;
import com.zxplat.domain.share.enums.SupplierType;
import com.zxplat.domain.share.events.medication.medicationdefinition.MedicationCatalogImportedEvent;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 药品目录导入写段：事务内逐行调用创建回调并发布批次导入事件。
 */
@Service
@RequiredArgsConstructor
public class MedicationCatalogImportWriteSupport {

    private final IDomainEventPublisher domainEventPublisher;
    private final MedicationCatalogBusNoSequenceSync busNoSequenceSync;
    /** 自动建厂家前对齐 SP 序号，避免迁入历史 bus_no 撞号。 */
    private final SupplierBusNoSequenceSync supplierBusNoSequenceSync;
    /** 按 Excel 厂家文案精确匹配生产厂家主数据（type=1）。 */
    private final SupplierQueryService supplierQueryService;
    /** 未命中厂家文案时注册 type=生产厂家主数据。 */
    private final SupplierDomainService supplierDomainService;

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
        // Pre：独立事务对齐 SP 序号，避免导入自动建厂家时「生成供应商编号失败」
        supplierBusNoSequenceSync.syncFromExistingSuppliers();
        // Pre：本批厂家名 → id 缓存，避免同文件同厂重复建档
        Map<String, Long> manufacturerIdByName = new HashMap<>();
        int total = rows.size();
        int processed = 0;
        int created = 0;
        // Cmd：逐行建档+定价（由 creator 封装）；文件内或库内同键则跳过，不覆盖已有目录
        for (MedicationCatalogImportRow row : rows) {
            if (row != null && row.isSkipExisting()) {
                processed = processed + 1;
                if (progressCallback != null) {
                    progressCallback.onProgress(processed, total);
                }
                continue;
            }
            CreateMedicationCatalogRequest request =
                this.toCreateRequest(row, orgId, operatorId, manufacturerIdByName);
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
     *
     * <p>Excel 厂家文案精确匹配 {@code cus_supplier}（type=生产厂家）；未命中则注册新厂家后再落
     * {@code manufacturerId}。</p>
     *
     * @param row                   导入行
     * @param orgId                 机构主键
     * @param operatorId            操作人
     * @param manufacturerIdByName  本批厂家名缓存（就地写入）
     * @return 创建请求
     */
    private CreateMedicationCatalogRequest toCreateRequest(
        MedicationCatalogImportRow row,
        Long orgId,
        Long operatorId,
        Map<String, Long> manufacturerIdByName
    ) {
        CreateMedicationCatalogRequest request = new CreateMedicationCatalogRequest();
        request.setName(row.getName());
        request.setMerchandiseName(row.getMerchandiseName());
        request.setCategoryCode(row.getCategoryCode());
        request.setManufacturerText(row.getManufacturerText());
        if (orgId != null) {
            request.setOrgId(orgId);
        }
        // Pre：厂家文案 → manufacturerId（命中复用；未命中自动建 type=生产厂家）
        this.resolveOrCreateManufacturerIdFromImportText(request, operatorId, manufacturerIdByName);
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
        request.setNationalMonitorFlag(row.getNationalMonitorFlag());
        request.setCentralPurchaseFlag(row.getCentralPurchaseFlag());
        request.setDefinition(this.mergeDefinition(row.getDefinitionText(), row.getLegacyCode()));
        request.setSupplyId(null);
        return request;
    }

    /**
     * 导入行厂家文案对齐生产厂家主数据：精确名命中则复用；未命中则注册 type=生产厂家。
     *
     * @param request              已写入 {@code manufacturerText}/{@code orgId} 的创建请求
     * @param operatorId           操作人（写入供应商注册事件）
     * @param manufacturerIdByName 本批缓存（trim 名 → id）
     */
    private void resolveOrCreateManufacturerIdFromImportText(
        CreateMedicationCatalogRequest request,
        Long operatorId,
        Map<String, Long> manufacturerIdByName
    ) {
        if (request == null) {
            return;
        }
        // 已有主键则不再用文案覆盖
        if (request.getManufacturerId() != null) {
            return;
        }
        String manufacturerText = request.getManufacturerText();
        if (StringUtils.isEmpty(manufacturerText)) {
            return;
        }
        String trimmedName = manufacturerText.trim();
        // 本批已解析过同名厂家：直接复用，避免同文件重复建档
        if (manufacturerIdByName != null && manufacturerIdByName.containsKey(trimmedName)) {
            request.setManufacturerId(manufacturerIdByName.get(trimmedName));
            return;
        }
        Optional<Long> matchedId = supplierQueryService.findManufacturerIdByExactName(trimmedName);
        if (matchedId.isPresent()) {
            Long id = matchedId.get();
            request.setManufacturerId(id);
            if (manufacturerIdByName != null) {
                manufacturerIdByName.put(trimmedName, id);
            }
            return;
        }
        // 未命中：注册生产厂家主数据（信用代码可空；名称原样）
        Long orgId = request.getOrgId();
        if (orgId == null || orgId == 0L) {
            return;
        }
        RegisterSupplierCommand command = new RegisterSupplierCommand();
        command.setName(trimmedName);
        command.setTypeEnum(SupplierType.MANUFACTURER.getCode());
        command.setCreditCode("");
        command.setOrgId(orgId);
        command.setOperatorId(operatorId);
        command.setNote("药典导入自动建档");
        Long newId = supplierDomainService.registerSupplier(command);
        request.setManufacturerId(newId);
        if (manufacturerIdByName != null) {
            manufacturerIdByName.put(trimmedName, newId);
        }
    }

    /**
     * 合并药品说明与院内旧编码至 definition。
     *
     * @param definitionText 药品说明
     * @param legacyCode     院内旧编码
     * @return 合并后文本；皆空则 null
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
