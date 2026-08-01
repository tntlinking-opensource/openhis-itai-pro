/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.support;

import com.openhis.health.application.basic.servicecatalog.request.CreateServiceCatalogRequest;
import com.openhis.health.domain.share.constants.ServiceCatalogImportConstants;
import com.openhis.health.domain.share.events.workflow.activitydefinition.ServiceCatalogImportedEvent;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 诊疗目录导入写段：事务内逐行调用创建回调并发布批次导入事件。
 */
@Service
@RequiredArgsConstructor
public class ServiceCatalogImportWriteSupport {

    private final IDomainEventPublisher domainEventPublisher;
    private final ServiceCatalogBusNoSequenceSync busNoSequenceSync;

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
     * 事务内逐行导入：映射为 {@link CreateServiceCatalogRequest} 后委托 {@link ImportRowCreator}。
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
        List<ServiceCatalogImportRow> rows,
        ImportRowCreator creator,
        ProgressCallback progressCallback
    ) {
        // Gate：写段入参
        if (rows == null || rows.isEmpty() || creator == null) {
            return;
        }
        // Pre：独立事务对齐 AD 序号，避免「诊疗业务编码已存在」或落库撞号
        busNoSequenceSync.syncFromExistingDefinitions();
        int total = rows.size();
        int processed = 0;
        // Cmd：逐行建档+定价（由 creator 封装）
        for (ServiceCatalogImportRow row : rows) {
            CreateServiceCatalogRequest request = this.toCreateRequest(row, orgId);
            creator.create(request);
            processed = processed + 1;
            if (progressCallback != null) {
                progressCallback.onProgress(processed, total);
            }
        }
        // Cmd 续：发布批次导入事件
        ServiceCatalogImportedEvent event = new ServiceCatalogImportedEvent(
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
        List<ServiceCatalogImportRow> rows,
        ImportRowCreator creator
    ) {
        this.importRows(batchNo, operatorId, null, rows, creator, null);
    }

    /**
     * 导入行 → 新建诊疗目录请求（ACTIVE 由领域创建路径保证；busNo/拼音五笔由领域采番）。
     */
    private CreateServiceCatalogRequest toCreateRequest(ServiceCatalogImportRow row, Long orgId) {
        CreateServiceCatalogRequest request = new CreateServiceCatalogRequest();
        request.setName(row.getName());
        request.setCategoryCode(row.getCategoryCode());
        request.setTypeEnum(row.getTypeEnum());
        request.setPackageUnitCode(row.getPackageUnitCode());
        request.setItemTypeCode(row.getItemTypeCode());
        request.setPurchasePrice(row.getPurchasePrice());
        request.setRetailPrice(row.getRetailPrice());
        request.setMaximumRetailPrice(row.getMaximumRetailPrice());
        request.setYbFlag(row.getYbFlag());
        request.setYbNo(row.getYbNo());
        request.setYbType(row.getYbTypeCode());
        request.setYbMatchFlag(row.getYbMatchFlag());
        request.setChrgitmLv(row.getChrgitmLv());
        request.setMrSettlementTypeCode(row.getMrSettlementTypeCode());
        request.setDescriptionText(this.mergeDescription(row.getDescriptionText(), row.getLegacyCode()));
        if (orgId != null) {
            request.setOrgId(orgId);
        }
        return request;
    }

    /**
     * 合并说明与院内旧编码至 descriptionText。
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
        String legacyLine = ServiceCatalogImportConstants.LEGACY_CODE_DEFINITION_PREFIX + legacyCode.trim();
        if (!hasDescription) {
            return legacyLine;
        }
        return descriptionText.trim() + "\n" + legacyLine;
    }

    /**
     * 导入单行创建回调（由 AppService 传入 {@code this::createOneForImport} 等实现）。
     */
    @FunctionalInterface
    public interface ImportRowCreator {

        /**
         * 创建单条诊疗目录（含定价）。
         *
         * @param request 新建请求
         */
        void create(CreateServiceCatalogRequest request);
    }
}
