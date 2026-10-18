/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.approval.support;

import com.zxplat.application.finance.approval.dto.FinanceApprovalDocumentRowDto;
import com.zxplat.application.finance.approval.mapper.FinanceApprovalMapper;
import com.zxplat.application.finance.approval.request.PageFinanceApprovalDocumentRequest;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.zxplat.domain.custom.priceadjustment.queries.PriceAdjustmentDocumentListQuery;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentImpactSummaryQueryModel;
import com.zxplat.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.zxplat.domain.custom.supplier.service.SupplierQueryService;
import com.zxplat.domain.share.constants.FinanceApprovalConstants;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 财务审批统一列表跨源分页 Loader（B.6.4）。
 *
 * <p>分域调用供应/调价 QueryService 三件套；并集时在应用层按制单/申请时刻降序合并切片。
 * <strong>禁止</strong>在本类默认改写为进货或待审（BR-APR-006）。</p>
 */
@Component
@RequiredArgsConstructor
public class FinanceApprovalDocumentPageLoader {

    /** 供应单据列表读。 */
    private final SupplyRequestQueryService supplyRequestQueryService;

    /** 调价单据列表读。 */
    private final PriceAdjustmentQueryService priceAdjustmentQueryService;

    /** Request↔Query / 行 DTO。 */
    private final FinanceApprovalMapper financeApprovalMapper;

    /** 类型与状态映射。 */
    private final FinanceApprovalTypeSupport typeSupport;

    /** 金额列装配。 */
    private final FinanceApprovalAmountAssembler amountAssembler;

    /** 库位名。 */
    private final LocationQueryService locationQueryService;

    /** 供应商名。 */
    private final SupplierQueryService supplierQueryService;

    /** 人员名。 */
    private final PractitionerQueryService practitionerQueryService;

    /**
     * 分页查询财务审批单据行。
     *
     * @param request 筛选与分页；调用方已完成 type/itemTable 校验
     * @return 合并后的当前页行与总数
     */
    public PageLoadResult page(PageFinanceApprovalDocumentRequest request) {
        String typeEnum = null;
        if (StringUtils.isNotEmpty(request.getTypeEnum())) {
            typeEnum = request.getTypeEnum().trim();
        }
        // 仅调价：不查供应（SDD §3.3 分支）
        if (FinanceApprovalConstants.TYPE_PRICE_ADJUSTMENT.equalsIgnoreCase(typeEnum)) {
            return this.pagePriceOnly(request);
        }
        // 单一供应码：仅供应 QS，不查调价
        if (StringUtils.isNotEmpty(typeEnum)
            && !FinanceApprovalConstants.TYPE_ALL.equalsIgnoreCase(typeEnum)) {
            return this.pageSupplyOnly(request, typeEnum);
        }
        // 空或 ALL：供应财务族 ∪ 调价并集；禁止默认改写为进货/待审（BR-APR-006）
        return this.pageUnion(request);
    }

    /**
     * 仅供应单类型分页。
     *
     * @param request  请求
     * @param typeCode 供应码
     * @return 分页结果
     */
    private PageLoadResult pageSupplyOnly(PageFinanceApprovalDocumentRequest request, String typeCode) {
        SupplyRequestDocumentListQuery query = this.financeApprovalMapper.toSupplyDocumentListQuery(request);
        query.setTypeEnum(typeCode);
        long total = this.supplyRequestQueryService.countDistinctBusNo(query);
        List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(query);
        List<SupplyRequestModel> lines =
            this.supplyRequestQueryService.listByBusNosWithFilter(query, busNos);
        List<FinanceApprovalDocumentRowDto> rows = this.buildSupplyRows(busNos, lines);
        this.enrichDisplayNames(rows);
        return new PageLoadResult(rows, total);
    }

    /**
     * 仅调价分页。
     *
     * @param request 请求
     * @return 分页结果
     */
    private PageLoadResult pagePriceOnly(PageFinanceApprovalDocumentRequest request) {
        String mappedStatus = this.typeSupport.mapUnifiedStatusToPriceAdjustment(request.getStatusEnum());
        PriceAdjustmentDocumentListQuery query =
            this.financeApprovalMapper.toPriceAdjustmentDocumentListQuery(request, mappedStatus);
        // 调价无供应商/库房列过滤：入参有 supplierId/locationId 时结果为空（语义：条件不适用）
        if (request.getSupplierId() != null || request.getLocationId() != null) {
            return new PageLoadResult(List.of(), 0L);
        }
        long total = this.priceAdjustmentQueryService.countDistinctBusNo(query);
        List<String> busNos = this.priceAdjustmentQueryService.listDistinctBusNoPage(query);
        List<PriceAdjustment> lines =
            this.priceAdjustmentQueryService.listLinesByBusNosWithFilter(query, busNos);
        List<FinanceApprovalDocumentRowDto> rows = this.buildPriceRows(busNos, lines);
        this.enrichDisplayNames(rows);
        return new PageLoadResult(rows, total);
    }

    /**
     * 供应财务族 ∪ 调价并集分页：两侧各取前 {@code pageNo * pageSize} 条后按时刻降序合并切片。
     *
     * @param request 请求
     * @return 分页结果
     */
    private PageLoadResult pageUnion(PageFinanceApprovalDocumentRequest request) {
        int pageNo = 1;
        if (request.getPageNo() != null && request.getPageNo() > 0) {
            pageNo = request.getPageNo();
        }
        int pageSize = FinanceApprovalConstants.DEFAULT_PAGE_SIZE;
        if (request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        }
        int fetchSize = pageNo * pageSize;

        SupplyRequestDocumentListQuery supplyQuery = this.financeApprovalMapper.toSupplyDocumentListQuery(request);
        supplyQuery.setTypeEnum(null);
        supplyQuery.setTypeEnums(SupplyType.listFinanceApprovalTypeCodes());
        supplyQuery.setPageNo(1);
        supplyQuery.setPageSize(fetchSize);

        String mappedStatus = this.typeSupport.mapUnifiedStatusToPriceAdjustment(request.getStatusEnum());
        PriceAdjustmentDocumentListQuery priceQuery =
            this.financeApprovalMapper.toPriceAdjustmentDocumentListQuery(request, mappedStatus);
        priceQuery.setPageNo(1);
        priceQuery.setPageSize(fetchSize);

        long supplyTotal = this.supplyRequestQueryService.countDistinctBusNo(supplyQuery);
        long priceTotal = 0L;
        // 调价无供应商/库房列：并集带该条件时调价侧不计入，避免假命中
        boolean skipPrice = request.getSupplierId() != null || request.getLocationId() != null;
        if (!skipPrice) {
            priceTotal = this.priceAdjustmentQueryService.countDistinctBusNo(priceQuery);
        }
        long total = supplyTotal + priceTotal;

        List<String> supplyBusNos = this.supplyRequestQueryService.listDistinctBusNoPage(supplyQuery);
        List<SupplyRequestModel> supplyLines =
            this.supplyRequestQueryService.listByBusNosWithFilter(supplyQuery, supplyBusNos);
        List<FinanceApprovalDocumentRowDto> supplyRows = this.buildSupplyRows(supplyBusNos, supplyLines);

        List<FinanceApprovalDocumentRowDto> priceRows = new ArrayList<>();
        if (!skipPrice) {
            List<String> priceBusNos = this.priceAdjustmentQueryService.listDistinctBusNoPage(priceQuery);
            List<PriceAdjustment> priceLines =
                this.priceAdjustmentQueryService.listLinesByBusNosWithFilter(priceQuery, priceBusNos);
            priceRows = this.buildPriceRows(priceBusNos, priceLines);
        }

        List<MergeCandidate> candidates = new ArrayList<>();
        for (FinanceApprovalDocumentRowDto row : supplyRows) {
            candidates.add(new MergeCandidate(row, this.resolveSortInstant(row)));
        }
        for (FinanceApprovalDocumentRowDto row : priceRows) {
            candidates.add(new MergeCandidate(row, this.resolveSortInstant(row)));
        }
        candidates.sort(Comparator
            .comparing(MergeCandidate::sortInstant, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(c -> c.row.getBusNo(), Comparator.nullsLast(String::compareTo)));

        int from = (pageNo - 1) * pageSize;
        List<FinanceApprovalDocumentRowDto> pageRows = new ArrayList<>();
        for (int i = from; i < candidates.size() && pageRows.size() < pageSize; i++) {
            pageRows.add(candidates.get(i).row);
        }
        this.enrichDisplayNames(pageRows);
        return new PageLoadResult(pageRows, total);
    }

    /**
     * 按 busNo 顺序装配供应列表行并填金额。
     *
     * @param busNos 单号页序
     * @param lines  明细
     * @return 行列表
     */
    private List<FinanceApprovalDocumentRowDto> buildSupplyRows(
        List<String> busNos,
        List<SupplyRequestModel> lines) {
        List<FinanceApprovalDocumentRowDto> rows = new ArrayList<>();
        if (busNos == null || busNos.isEmpty()) {
            return rows;
        }
        LinkedHashMap<String, List<SupplyRequestModel>> byBusNo = new LinkedHashMap<>();
        if (lines != null) {
            for (SupplyRequestModel line : lines) {
                if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                    continue;
                }
                byBusNo.computeIfAbsent(line.getBusNo().trim(), k -> new ArrayList<>()).add(line);
            }
        }
        for (String busNoRaw : busNos) {
            if (StringUtils.isEmpty(busNoRaw)) {
                continue;
            }
            String busNo = busNoRaw.trim();
            List<SupplyRequestModel> group = byBusNo.get(busNo);
            if (group == null || group.isEmpty()) {
                continue;
            }
            SupplyRequestModel head = group.get(0);
            FinanceApprovalDocumentRowDto row = this.financeApprovalMapper.toRowDto(head);
            if (row == null) {
                continue;
            }
            // Pre：按类型填金额列（SDD fillAmounts）
            this.amountAssembler.fillAmounts(row, group);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 按 busNo 顺序装配调价列表行并填盈亏。
     *
     * @param busNos 单号页序
     * @param lines  主行
     * @return 行列表
     */
    private List<FinanceApprovalDocumentRowDto> buildPriceRows(List<String> busNos, List<PriceAdjustment> lines) {
        List<FinanceApprovalDocumentRowDto> rows = new ArrayList<>();
        if (busNos == null || busNos.isEmpty()) {
            return rows;
        }
        LinkedHashMap<String, PriceAdjustment> headByBusNo = new LinkedHashMap<>();
        if (lines != null) {
            for (PriceAdjustment line : lines) {
                if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                    continue;
                }
                headByBusNo.putIfAbsent(line.getBusNo().trim(), line);
            }
        }
        for (String busNoRaw : busNos) {
            if (StringUtils.isEmpty(busNoRaw)) {
                continue;
            }
            String busNo = busNoRaw.trim();
            PriceAdjustment head = headByBusNo.get(busNo);
            if (head == null) {
                continue;
            }
            FinanceApprovalDocumentRowDto row = this.financeApprovalMapper.toRowDto(head);
            if (row == null) {
                continue;
            }
            // Pre：调价盈亏分列汇总（进价 / 售价各自写入，禁止加总）
            PriceAdjustmentImpactSummaryQueryModel impact =
                this.priceAdjustmentQueryService.sumImpactByBusNo(busNo);
            this.amountAssembler.fillAmounts(row, impact);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 批量回填库房/供应商/人员展示名。
     *
     * @param rows 当前页行
     */
    private void enrichDisplayNames(List<FinanceApprovalDocumentRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> locationIds = new HashSet<>();
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> practitionerIds = new HashSet<>();
        for (FinanceApprovalDocumentRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getLocationId() != null) {
                locationIds.add(row.getLocationId());
            }
            if (row.getSupplierId() != null) {
                supplierIds.add(row.getSupplierId());
            }
            if (row.getApplicantId() != null) {
                practitionerIds.add(row.getApplicantId());
            }
            if (row.getApproverId() != null) {
                practitionerIds.add(row.getApproverId());
            }
        }
        Map<Long, String> locationNameById = new HashMap<>();
        for (Long locationId : locationIds) {
            Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
            if (locOpt.isPresent() && StringUtils.isNotEmpty(locOpt.get().getName())) {
                locationNameById.put(locationId, locOpt.get().getName().trim());
            }
        }
        Map<Long, String> supplierNameById = new HashMap<>();
        for (Long supplierId : supplierIds) {
            Optional<String> nameOpt = this.supplierQueryService.findSupplierNameIfPresent(supplierId);
            nameOpt.ifPresent(name -> supplierNameById.put(supplierId, name));
        }
        Map<Long, String> practitionerNameById = new HashMap<>();
        if (!practitionerIds.isEmpty()) {
            List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
            for (Practitioner pr : practitioners) {
                if (pr == null || pr.getId() == null) {
                    continue;
                }
                if (StringUtils.isNotEmpty(pr.getName())) {
                    practitionerNameById.put(pr.getId(), pr.getName());
                }
            }
        }
        for (FinanceApprovalDocumentRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getLocationId() != null) {
                row.setLocationName(locationNameById.get(row.getLocationId()));
            }
            if (row.getSupplierId() != null) {
                row.setSupplierName(supplierNameById.get(row.getSupplierId()));
            }
            if (row.getApplicantId() != null) {
                row.setApplicantName(practitionerNameById.get(row.getApplicantId()));
            }
            if (row.getApproverId() != null) {
                row.setApproverName(practitionerNameById.get(row.getApproverId()));
            }
        }
    }

    /**
     * 解析合并排序时刻：优先制单/提交展示串对应时刻；解析失败则 null。
     *
     * @param row 列表行
     * @return Instant 或 null
     */
    private Instant resolveSortInstant(FinanceApprovalDocumentRowDto row) {
        if (row == null) {
            return null;
        }
        String primary = row.getOccurrenceTime();
        if (StringUtils.isEmpty(primary)) {
            primary = row.getSubmitTime();
        }
        if (StringUtils.isEmpty(primary)) {
            primary = row.getApplyTime();
        }
        if (StringUtils.isEmpty(primary)) {
            return null;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(primary.trim(),
                com.zxplat.domain.share.constants.GeneralSharedConstants.DATE_TIME_FORMATTER);
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Loader 分页结果。
     *
     * @param rows       当前页行
     * @param totalCount 合并后总单数
     */
    public record PageLoadResult(List<FinanceApprovalDocumentRowDto> rows, long totalCount) {
    }

    /**
     * 并集合并候选项。
     *
     * @param row         列表行
     * @param sortInstant 排序时刻
     */
    private record MergeCandidate(FinanceApprovalDocumentRowDto row, Instant sortInstant) {
    }
}
