/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.support;

import com.openhis.health.application.basic.devicecatalog.request.CreateDeviceCatalogRequest;
import com.openhis.health.domain.custom.supplier.command.RegisterSupplierCommand;
import com.openhis.health.domain.custom.supplier.service.SupplierDomainService;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.share.constants.DeviceCatalogImportConstants;
import com.openhis.health.domain.share.enums.SupplierType;
import com.openhis.health.domain.share.events.administration.devicedefinition.DeviceCatalogImportedEvent;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 耗材目录导入写段：事务内逐行调用创建回调并发布批次导入事件。
 */
@Service
@RequiredArgsConstructor
public class DeviceCatalogImportWriteSupport {

    private final IDomainEventPublisher domainEventPublisher;
    private final DeviceCatalogBusNoSequenceSync busNoSequenceSync;
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
     * 事务内逐行导入：映射为 {@link CreateDeviceCatalogRequest} 后委托 {@link ImportRowCreator}。
     *
     * @param batchNo          批次号
     * @param operatorId       操作人主键
     * @param orgId            机构主键（异步场景直接绑定，避免会话丢失）
     * @param rows             已通过校验的导入行
     * @param creator          单行创建回调
     * @param progressCallback 可选进度回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void importRows(
        String batchNo,
        Long operatorId,
        Long orgId,
        List<DeviceCatalogImportRow> rows,
        ImportRowCreator creator,
        ProgressCallback progressCallback
    ) {
        // Gate：写段入参
        if (rows == null || rows.isEmpty() || creator == null) {
            return;
        }
        // Pre：独立事务对齐 DD 序号，避免「器材业务编码已存在」或落库撞号
        busNoSequenceSync.syncFromExistingDefinitions();
        // Pre：本批厂家名 → id 缓存，避免同文件同厂重复建档
        Map<String, Long> manufacturerIdByName = new HashMap<>();
        int total = rows.size();
        int processed = 0;
        // Cmd：逐行建档+定价（由 creator 封装）
        for (DeviceCatalogImportRow row : rows) {
            CreateDeviceCatalogRequest request =
                this.toCreateRequest(row, orgId, operatorId, manufacturerIdByName);
            creator.create(request);
            processed = processed + 1;
            if (progressCallback != null) {
                progressCallback.onProgress(processed, total);
            }
        }
        // Cmd 续：发布批次导入事件
        DeviceCatalogImportedEvent event = new DeviceCatalogImportedEvent(
            batchNo, rows.size(), 0, operatorId);
        domainEventPublisher.publish(event);
    }

    /**
     * 兼容旧签名：无进度回调、无显式 orgId。
     *
     * @param batchNo    批次号
     * @param operatorId 操作人主键
     * @param rows       已通过校验的导入行
     * @param creator    单行创建回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void importRows(
        String batchNo,
        Long operatorId,
        List<DeviceCatalogImportRow> rows,
        ImportRowCreator creator
    ) {
        this.importRows(batchNo, operatorId, null, rows, creator, null);
    }

    /**
     * 导入行 → 新建耗材目录请求（ACTIVE 由领域创建路径保证；busNo/拼音五笔由领域采番）。
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
    private CreateDeviceCatalogRequest toCreateRequest(
        DeviceCatalogImportRow row,
        Long orgId,
        Long operatorId,
        Map<String, Long> manufacturerIdByName
    ) {
        CreateDeviceCatalogRequest request = new CreateDeviceCatalogRequest();
        request.setName(row.getName());
        request.setMerchandiseName(row.getMerchandiseName());
        request.setCategoryCode(row.getCategoryCode());
        request.setTypeCode(row.getTypeCode());
        request.setManufacturerText(row.getManufacturerText());
        if (orgId != null) {
            request.setOrgId(orgId);
        }
        // Pre：厂家文案 → manufacturerId（命中复用；未命中自动建 type=生产厂家）
        this.resolveOrCreateManufacturerIdFromImportText(request, operatorId, manufacturerIdByName);
        request.setItemTypeCode(row.getItemTypeCode());
        request.setTotalVolume(row.getTotalVolume());
        request.setApprovalNumber(row.getApprovalNumber());
        request.setYbNo(row.getYbNo());
        request.setChrgitmLv(row.getChrgitmLv());
        request.setMrSettlementTypeCode(row.getMrSettlementTypeCode());
        request.setYbMatchFlag(row.getYbMatchFlag());
        request.setYbType(row.getYbTypeCode());
        request.setPurchaseUnitCode(row.getPurchaseUnitCode());
        request.setSplitUnitCode(row.getSplitUnitCode());
        request.setPurchaseUnitRatio(row.getPurchaseUnitRatio());
        request.setPackageUnitCode(row.getPackageUnitCode());
        request.setPackageUnitRatio(row.getPackageUnitRatio());
        request.setStockUnitCode(row.getStockUnitCode());
        request.setStockUnitRatio(row.getStockUnitRatio());
        request.setPurchasePrice(row.getPurchasePrice());
        request.setRetailPrice(row.getRetailPrice());
        request.setMaximumRetailPrice(row.getMaximumRetailPrice());
        request.setItemMinQuantity(row.getItemMinQuantity());
        request.setItemMaxQuantity(row.getItemMaxQuantity());
        request.setModelNumber(row.getModelNumber());
        request.setAllergenFlag(row.getAllergenFlag());
        request.setYbFlag(row.getYbFlag());
        request.setHvcmFlag(row.getHvcmFlag());
        request.setChargeableFlag(row.getChargeableFlag());
        request.setDescription(this.mergeDescription(row.getDescriptionText(), row.getLegacyCode()));
        request.setSupplyId(null);
        return request;
    }

    /**
     * 导入行厂家文案对齐生产厂家主数据：精确名命中则复用；未命中则注册 type=生产厂家。
     *
     * @param request              已写入 {@code manufacturerText}/{@code orgId} 的创建请求
     * @param operatorId           操作人
     * @param manufacturerIdByName 本批缓存（trim 名 → id）
     */
    private void resolveOrCreateManufacturerIdFromImportText(
        CreateDeviceCatalogRequest request,
        Long operatorId,
        Map<String, Long> manufacturerIdByName
    ) {
        if (request == null) {
            return;
        }
        if (request.getManufacturerId() != null) {
            return;
        }
        String manufacturerText = request.getManufacturerText();
        if (StringUtils.isEmpty(manufacturerText)) {
            return;
        }
        String trimmedName = manufacturerText.trim();
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
        command.setNote("耗材目录导入自动建档");
        Long newId = supplierDomainService.registerSupplier(command);
        request.setManufacturerId(newId);
        if (manufacturerIdByName != null) {
            manufacturerIdByName.put(trimmedName, newId);
        }
    }

    /**
     * 合并说明与院内旧编码至 description。
     *
     * @param descriptionText 说明
     * @param legacyCode      院内旧编码
     * @return 合并后文本；皆空则 null
     */
    private String mergeDescription(String descriptionText, String legacyCode) {
        boolean hasDescription = StringUtils.isNotEmpty(descriptionText);
        boolean hasLegacy = StringUtils.isNotEmpty(legacyCode);
        if (!hasDescription && !hasLegacy) {
            return null;
        }
        if (hasDescription && !hasLegacy) {
            return descriptionText.trim();
        }
        String legacyLine = DeviceCatalogImportConstants.LEGACY_CODE_DEFINITION_PREFIX + legacyCode.trim();
        if (!hasDescription) {
            return legacyLine;
        }
        return descriptionText.trim() + "\n" + legacyLine;
    }

    /**
     * 导入单行创建回调。
     */
    @FunctionalInterface
    public interface ImportRowCreator {

        /**
         * 创建单条耗材目录（含定价）。
         *
         * @param request 新建请求
         */
        void create(CreateDeviceCatalogRequest request);
    }
}
