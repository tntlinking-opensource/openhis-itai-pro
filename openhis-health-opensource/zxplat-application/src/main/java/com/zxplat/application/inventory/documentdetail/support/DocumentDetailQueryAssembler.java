/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.documentdetail.support;

import com.zxplat.application.inventory.documentdetail.request.DocumentDetailFilterRequest;
import com.zxplat.application.shared.inventory.InventoryCatalogFilter;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 单据详情筛选条件装配：审批时间窗、品名关键字与 {@link WarehouseDocumentDetailPageQuery} 单一真源（INV-DDC-SDD-R-002）。
 */
@Component
@RequiredArgsConstructor
public class DocumentDetailQueryAssembler {

    /** 品名关键字 → resolved 定义 id（B.6.4 跨聚合预解析）。 */
    private final InventoryCatalogFilter inventoryCatalogFilter;
    /** 各域 typeEnum 白名单（未传类型时 IN 本域全部）。 */
    private final DocumentDetailTypeWhitelistHelper typeWhitelistSupport;

    /**
     * 校验审批时间闭区间合法（start ≤ end）。
     *
     * @param start 区间起点
     * @param end   区间终点
     * @throws BusinessException {@code INV_DDC_207} 区间非法
     */
    public void assertApprovalTimeWindowValid(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return;
        }
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCodeConstants.INV_DDC_207, "审批时间范围无效");
        }
    }

    /**
     * 解析审批时间窗：均未传时默认当前自然月闭区间。
     *
     * @param start 请求起点，可空
     * @param end   请求终点，可空
     * @return 长度为 2 的数组：[start, end]
     */
    public LocalDateTime[] resolveApprovalTimeWindow(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null) {
            return new LocalDateTime[] {start, end};
        }
        if (start != null) {
            return new LocalDateTime[] {start, end};
        }
        if (end != null) {
            return new LocalDateTime[] {start, end};
        }
        YearMonth current = YearMonth.now();
        LocalDateTime monthStart = current.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = current.atEndOfMonth().atTime(LocalTime.of(23, 59, 59));
        return new LocalDateTime[] {monthStart, monthEnd};
    }

    /**
     * 当前自然月默认审批时间起（init 回填）。
     *
     * @return 月初 00:00:00
     */
    public LocalDateTime defaultApprovalTimeStart() {
        YearMonth current = YearMonth.now();
        return current.atDay(1).atStartOfDay();
    }

    /**
     * 当前自然月默认审批时间止（init 回填）。
     *
     * @return 月末 23:59:59
     */
    public LocalDateTime defaultApprovalTimeEnd() {
        YearMonth current = YearMonth.now();
        return current.atEndOfMonth().atTime(LocalTime.of(23, 59, 59));
    }

    /**
     * 品名关键字预解析后是否应短路为空结果（双 resolved 列表均为空且 keyword 非空）。
     *
     * @param query 已 enrich 的查询入参
     * @return {@code true} 表示无命中、应返回空页
     */
    public boolean shouldShortCircuitEmptyForProductKeyword(WarehouseDocumentDetailPageQuery query) {
        if (query == null || StringUtils.isEmpty(query.getProductKeyword())) {
            return false;
        }
        List<Long> medIds = query.getResolvedMedicationDefinitionIdsForKeyword();
        List<Long> devIds = query.getResolvedDeviceDefinitionIdsForKeyword();
        boolean medEmpty = medIds == null || medIds.isEmpty();
        boolean devEmpty = devIds == null || devIds.isEmpty();
        return medEmpty && devEmpty;
    }

    /**
     * 将筛选请求装配为领域分页 Query（批号 LIKE、品名 resolved；状态/时间窗见下）。
     *
     * <p><strong>BR-INV-DDC-003</strong>：非期初固定 {@code AGREE} + {@code approval_time} 窗；
     * 期初 {@code *_OPENING}（须显式传 {@code typeEnum}）为 {@code AGREE}+{@code APPROVAL}，
     * 时间窗走 {@code apply_time}（审核中尚无审批时刻，供人工核对导出）。其它类型不受影响。</p>
     *
     * @param scope    业务域
     * @param request  筛选入参
     * @param tenantId 租户主键
     * @param pageNo   页码（1-based）；合计/导出全量拉取时可传 {@code null}
     * @param pageSize 页大小；合计/导出步长
     * @return 领域 Query（已 enrich 品名关键字）
     */
    public WarehouseDocumentDetailPageQuery buildPageQuery(
        DocumentDetailDomainScope scope,
        DocumentDetailFilterRequest request,
        Long tenantId,
        Integer pageNo,
        Integer pageSize
    ) {
        if (request == null) {
            request = new DocumentDetailFilterRequest();
        }
        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        if (pageNo != null) {
            query.setPageNo(pageNo);
        }
        if (pageSize != null) {
            query.setPageSize(pageSize);
        }
        // Asm：已传 typeEnum → 单类型等值；未传 → 本域白名单 IN（查全部，不强制域默认单类型）
        String typeEnum = this.typeWhitelistSupport.normalizeOptionalTypeEnum(request.getTypeEnum());
        if (StringUtils.isNotEmpty(typeEnum)) {
            query.setTypeEnum(typeEnum);
        } else {
            query.setTypeEnums(this.typeWhitelistSupport.whitelistTypeCodes(scope));
        }
        LocalDateTime[] window = this.resolveApprovalTimeWindow(
            request.getApprovalTimeStart(), request.getApprovalTimeEnd());
        // BR-INV-DDC-003：期初含审核中（人工核对）；非期初仅 AGREE + 审批时间
        if (SupplyType.isOpeningTypeCode(typeEnum)) {
            query.setStatusEnums(List.of(
                SupplyStatus.AGREE.getCode(),
                SupplyStatus.APPROVAL.getCode()));
            query.setApplyTimeStart(window[0]);
            query.setApplyTimeEnd(window[1]);
        } else {
            query.setStatusEnum(SupplyStatus.AGREE.getCode());
            query.setApprovalTimeStart(window[0]);
            query.setApprovalTimeEnd(window[1]);
        }
        query.setTenantId(tenantId);
        query.setSourceLocationId(request.getSourceLocationId());
        query.setPurposeLocationId(request.getPurposeLocationId());
        // Asm：供应商等值筛；空则该维不过滤
        query.setSupplierId(request.getSupplierId());
        query.setItemTable(this.normalizeOptionalItemTable(request.getItemTable()));
        query.setBusNo(StringUtils.trimToNull(request.getBusNo()));
        query.setLotNumberKeyword(StringUtils.trimToNull(request.getLotNumberKeyword()));
        query.setProductKeyword(StringUtils.trimToNull(request.getProductKeyword()));
        this.inventoryCatalogFilter.enrichDocumentDetailKeywordDefinitionIds(query);
        return query;
    }

    /**
     * 归一化可选项目分类：仅允许药品/耗材定义表；空则不限。
     *
     * @param itemTable 请求 itemTable
     * @return 归一化表名；空入参返回 null
     * @throws BusinessException {@code INV_DDC_202} 非法表名
     */
    private String normalizeOptionalItemTable(String itemTable) {
        String trimmed = StringUtils.trimToNull(itemTable);
        if (trimmed == null) {
            return null;
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(trimmed)
            || TableNameConstants.ADM_DEVICE_DEFINITION.equals(trimmed)) {
            return trimmed;
        }
        throw new BusinessException(ErrorCodeConstants.INV_DDC_202, "项目分类无效");
    }
}
