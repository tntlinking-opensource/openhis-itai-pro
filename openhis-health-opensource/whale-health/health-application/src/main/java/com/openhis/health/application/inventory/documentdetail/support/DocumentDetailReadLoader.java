/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.support;

import com.openhis.health.application.inventory.documentdetail.dto.DocumentDetailRowDto;
import com.openhis.health.application.inventory.documentdetail.mapper.InventoryDocumentDetailMapper;
import com.openhis.health.application.inventory.documentdetail.request.DocumentDetailFilterRequest;
import com.openhis.health.application.inventory.documentdetail.request.ExportDocumentDetailsRequest;
import com.openhis.health.application.inventory.documentdetail.request.PageDocumentDetailsRequest;
import com.openhis.health.application.inventory.documentdetail.request.SummarizeDocumentDetailsRequest;
import com.openhis.health.application.inventory.documentdetail.response.ExportDocumentDetailsResponse;
import com.openhis.health.application.inventory.documentdetail.response.LoadDocumentDetailFormInitResponse;
import com.openhis.health.application.inventory.documentdetail.response.PageDocumentDetailsResponse;
import com.openhis.health.application.inventory.documentdetail.response.SummarizeDocumentDetailsResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.share.constants.DocumentDetailExportConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 单据详情报表读侧编排（由请求 {@code domainScope} 区分三域）。
 *
 * <p>职责边界：租户 Gate、筛选装配、{@code pageWarehouseDocumentDetail}、批量 enrich、操作单位换算与导出拉全量。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentDetailReadLoader {

    /** 供应明细行分页读侧。 */
    private final SupplyRequestQueryService supplyRequestQueryService;
    /** 库位 init 下拉与行 enrich。 */
    private final LocationQueryService locationQueryService;
    /** 各域 SupplyType 白名单与默认类型。 */
    private final DocumentDetailTypeWhitelistHelper typeWhitelistSupport;
    /** Request → {@code WarehouseDocumentDetailPageQuery} 单一真源。 */
    private final DocumentDetailQueryAssembler queryAssemblySupport;
    /** 头摊平与主数据展示名。 */
    private final DocumentDetailEnricher enrichmentSupport;
    /** 拆零量 → 操作单位数量（BR-INV-DDC-010/011）。 */
    private final DocumentDetailQuantityConversionHelper quantityConversionSupport;
    /** Response/导出行映射。 */
    private final InventoryDocumentDetailMapper documentDetailMapper;
    /** 分页窗口 Gate（pageNo/pageSize ≥ 1）。 */
    private final PageWindowGate pageWindowGate;

    /**
     * 页面初始化。
     *
     * @param scope 业务域
     * @return init 响应
     * @throws BusinessException {@code INV_DDC_201}
     */
    public LoadDocumentDetailFormInitResponse loadDocumentDetailFormInit(DocumentDetailDomainScope scope) {
        // Gate：租户上下文
        this.requireTenantId();
        LoadDocumentDetailFormInitResponse response = new LoadDocumentDetailFormInitResponse();
        try {
            // Asm：本域类型白名单与默认类型
            response.setTypeEnumOptions(this.typeWhitelistSupport.buildTypeEnumOptions(scope));
            response.setDefaultTypeEnum(this.typeWhitelistSupport.defaultTypeEnum(scope));
            // Pre：租户内活跃库位并集（不做管辖 Gate）
            List<OptionDto> locationOptions = this.buildActiveLocationOptions();
            response.setSourceLocationOptions(locationOptions);
            response.setPurposeLocationOptions(locationOptions);
            // Asm：默认审批自然月
            response.setDefaultApprovalTimeStart(this.queryAssemblySupport.defaultApprovalTimeStart());
            response.setDefaultApprovalTimeEnd(this.queryAssemblySupport.defaultApprovalTimeEnd());
            return response;
        } catch (RuntimeException ex) {
            log.warn("单据详情 init 失败 scope={}", scope, ex);
            throw new BusinessException(ErrorCodeConstants.INV_DDC_201, "页面加载失败", ex);
        }
    }

    /**
     * 明细分页。
     *
     * @param scope   业务域
     * @param request 分页请求
     * @return 分页响应
     * @throws BusinessException {@code INV_DDC_202}～{@code INV_DDC_204}、{@code INV_DDC_207}
     */
    public PageDocumentDetailsResponse pageDocumentDetails(
        DocumentDetailDomainScope scope,
        PageDocumentDetailsRequest request
    ) {
        // Gate：分页窗口须合法
        this.pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.INV_DDC_203, "查询失败，请稍后重试");
        // Gate：租户上下文
        Long tenantId = this.requireTenantId();
        DocumentDetailFilterRequest filter = this.documentDetailMapper.toFilter(request);
        // Gate：typeEnum 白名单与审批时间闭区间
        this.assertFilterGates(scope, filter);
        // Pre：装配领域 Query（含品名 enrich）
        WarehouseDocumentDetailPageQuery query = this.queryAssemblySupport.buildPageQuery(
            scope, filter, tenantId, request.getPageNo(), request.getPageSize());
        // Pre：品名无命中时短路空页，避免无意义全表扫
        if (this.queryAssemblySupport.shouldShortCircuitEmptyForProductKeyword(query)) {
            return this.documentDetailMapper.toPageDocumentDetailsResponse(
                new ArrayList<>(), 0L, request.getPageNo(), request.getPageSize());
        }
        try {
            // Pre：领域明细分页
            Page<WarehouseDocumentDetailQueryModel> page =
                this.supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            // Asm：enrich + 操作单位换算后返回
            List<DocumentDetailRowDto> rows = this.assembleRows(page.getContent());
            return this.documentDetailMapper.toPageDocumentDetailsResponse(
                rows, page.getTotalElements(), request.getPageNo(), request.getPageSize());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("单据详情分页失败 scope={}", scope, ex);
            throw new BusinessException(ErrorCodeConstants.INV_DDC_203, "查询失败，请稍后重试", ex);
        }
    }

    /**
     * 筛选全量合计。
     *
     * @param scope   业务域
     * @param request 筛选请求
     * @return 合计响应
     * @throws BusinessException {@code INV_DDC_204}、{@code INV_DDC_206}
     */
    public SummarizeDocumentDetailsResponse summarizeDocumentDetails(
        DocumentDetailDomainScope scope,
        SummarizeDocumentDetailsRequest request
    ) {
        // Gate：租户上下文
        Long tenantId = this.requireTenantId();
        DocumentDetailFilterRequest filter = request;
        if (filter == null) {
            filter = new DocumentDetailFilterRequest();
        }
        // Gate：typeEnum 白名单与审批时间
        this.assertFilterGates(scope, filter);
        try {
            // Pre：与导出共用全量拉取链（步长 500）
            List<DocumentDetailRowDto> allRows = this.loadAllMatchingRows(scope, filter, tenantId);
            BigDecimal totalPriceSum = BigDecimal.ZERO;
            BigDecimal itemQuantitySum = BigDecimal.ZERO;
            // Asm：全筛选行 SUM totalPrice 与展示 itemQuantity
            for (DocumentDetailRowDto row : allRows) {
                if (row == null) {
                    continue;
                }
                if (row.getTotalPrice() != null) {
                    totalPriceSum = totalPriceSum.add(row.getTotalPrice());
                }
                if (row.getItemQuantity() != null) {
                    itemQuantitySum = itemQuantitySum.add(row.getItemQuantity());
                }
            }
            return this.documentDetailMapper.toSummarizeDocumentDetailsResponse(
                totalPriceSum, itemQuantitySum, allRows.size());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("单据详情合计失败 scope={}", scope, ex);
            throw new BusinessException(ErrorCodeConstants.INV_DDC_206, "合计计算失败，请稍后重试", ex);
        }
    }

    /**
     * Excel 导出。
     *
     * @param scope   业务域
     * @param request 筛选请求
     * @return 导出响应
     * @throws BusinessException {@code INV_DDC_204}、{@code INV_DDC_205}
     */
    public ExportDocumentDetailsResponse exportDocumentDetails(
        DocumentDetailDomainScope scope,
        ExportDocumentDetailsRequest request
    ) {
        // Gate：租户上下文
        Long tenantId = this.requireTenantId();
        DocumentDetailFilterRequest filter = request;
        if (filter == null) {
            filter = new DocumentDetailFilterRequest();
        }
        // Gate：typeEnum 白名单与审批时间
        this.assertFilterGates(scope, filter);
        try {
            // Pre：全量拉取（与合计同链）
            List<DocumentDetailRowDto> allRows = this.loadAllMatchingRows(scope, filter, tenantId);
            // Gate：超行上限直接失败（产品定：不 truncated）
            if (allRows.size() > DocumentDetailExportConstants.EXPORT_MAX_ROWS) {
                throw new BusinessException(ErrorCodeConstants.INV_DDC_205, "导出失败，请稍后重试");
            }
            // Asm：导出行映射 + Tabular Excel
            byte[] bytes = this.documentDetailMapper.buildExcelBytes(
                this.documentDetailMapper.toExportRows(allRows));
            String fileName = this.documentDetailMapper.buildExportFileName(scope);
            return this.documentDetailMapper.toExportDocumentDetailsResponse(fileName, bytes);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("单据详情导出失败 scope={}", scope, ex);
            throw new BusinessException(ErrorCodeConstants.INV_DDC_205, "导出失败，请稍后重试", ex);
        }
    }

    /**
     * 筛选 Gate：类型白名单与审批时间窗。
     *
     * @param scope  业务域
     * @param filter 筛选
     */
    private void assertFilterGates(DocumentDetailDomainScope scope, DocumentDetailFilterRequest filter) {
        // Gate：请求 domainScope 与已解析 scope 一致（防参数漂移）
        if (StringUtils.isNotEmpty(filter.getDomainScope())) {
            DocumentDetailDomainScope requestScope =
                DocumentDetailDomainScope.requireFromCode(filter.getDomainScope());
            if (requestScope != scope) {
                throw new BusinessException(ErrorCodeConstants.INV_DDC_202, "业务域 domainScope 无效");
            }
        }
        // Gate：typeEnum 落在本域白名单
        String typeEnum = this.typeWhitelistSupport.resolveTypeEnumOrDefault(scope, filter.getTypeEnum());
        this.typeWhitelistSupport.assertTypeInDomain(scope, typeEnum);
        filter.setTypeEnum(typeEnum);
        // Gate：审批时间闭区间 start ≤ end
        LocalDateTime[] window = this.queryAssemblySupport.resolveApprovalTimeWindow(
            filter.getApprovalTimeStart(), filter.getApprovalTimeEnd());
        this.queryAssemblySupport.assertApprovalTimeWindowValid(window[0], window[1]);
        filter.setApprovalTimeStart(window[0]);
        filter.setApprovalTimeEnd(window[1]);
    }

    /**
     * 循环分页拉取全量命中行（合计/导出共用）。
     *
     * @param scope    业务域
     * @param filter   筛选
     * @param tenantId 租户
     * @return 全量展示行
     */
    private List<DocumentDetailRowDto> loadAllMatchingRows(
        DocumentDetailDomainScope scope,
        DocumentDetailFilterRequest filter,
        Long tenantId
    ) {
        WarehouseDocumentDetailPageQuery probe = this.queryAssemblySupport.buildPageQuery(
            scope, filter, tenantId, 1, DocumentDetailExportConstants.EXPORT_PAGE_SIZE);
        if (this.queryAssemblySupport.shouldShortCircuitEmptyForProductKeyword(probe)) {
            return new ArrayList<>();
        }
        List<DocumentDetailRowDto> allRows = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            // Pre：按 EXPORT_PAGE_SIZE 分批拉取，避免一次性过大结果集
            WarehouseDocumentDetailPageQuery query = this.queryAssemblySupport.buildPageQuery(
                scope, filter, tenantId, pageNo, DocumentDetailExportConstants.EXPORT_PAGE_SIZE);
            Page<WarehouseDocumentDetailQueryModel> page =
                this.supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            List<DocumentDetailRowDto> batch = this.assembleRows(page.getContent());
            for (DocumentDetailRowDto row : batch) {
                allRows.add(row);
            }
            if (allRows.size() > DocumentDetailExportConstants.EXPORT_MAX_ROWS) {
                // 超出导出上限，停止继续拉取
                break;
            }
            if (page.getContent() == null || page.getContent().isEmpty()) {
                break;
            }
            if (pageNo >= page.getTotalPages()) {
                break;
            }
            pageNo++;
        }
        return allRows;
    }

    /**
     * enrich + 换算装配展示行。
     *
     * @param models 读模型
     * @return 展示行
     */
    private List<DocumentDetailRowDto> assembleRows(List<WarehouseDocumentDetailQueryModel> models) {
        // Pre：批量 enrich 上下文
        DocumentDetailEnricher.DocumentDetailEnrichmentContext context =
            this.enrichmentSupport.buildContext(models);
        List<DocumentDetailRowDto> rows = this.enrichmentSupport.toRows(models, context);
        // Asm：操作单位数量换算（失败抛 INV_DDC_204）
        this.quantityConversionSupport.applyDisplayQuantities(rows, context.getProductByKey());
        return rows;
    }

    /**
     * 租户内活跃库位并集下拉。
     *
     * @return 选项列表
     */
    private List<OptionDto> buildActiveLocationOptions() {
        LinkedHashMap<Long, Location> locationById = new LinkedHashMap<>();
        for (Location warehouse : this.locationQueryService.listAllActiveWarehouses()) {
            if (warehouse != null && warehouse.getId() != null) {
                locationById.put(warehouse.getId(), warehouse);
            }
        }
        for (Location pharmacy : this.locationQueryService.listAllActivePharmacies()) {
            if (pharmacy != null && pharmacy.getId() != null) {
                locationById.put(pharmacy.getId(), pharmacy);
            }
        }
        for (Location dept : this.locationQueryService.listAllActiveDepartmentPharmacies()) {
            if (dept != null && dept.getId() != null) {
                locationById.put(dept.getId(), dept);
            }
        }
        List<OptionDto> options = new ArrayList<>();
        for (Location location : locationById.values()) {
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(location.getId()));
            option.setLabel(location.getName());
            option.setBusNo(location.getFormEnum());
            options.add(option);
        }
        return options;
    }

    /**
     * 要求当前租户 id。
     *
     * @return 租户 id
     * @throws BusinessException {@code INV_DDC_201}
     */
    private Long requireTenantId() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCodeConstants.INV_DDC_201, "页面加载失败");
        }
        return tenantId;
    }
}
