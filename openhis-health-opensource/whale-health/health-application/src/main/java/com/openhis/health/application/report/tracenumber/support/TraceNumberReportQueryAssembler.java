/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.tracenumber.support;

import com.openhis.health.application.report.tracenumber.request.ExportTraceNumberReportRequest;
import com.openhis.health.application.report.tracenumber.request.PageTraceNumberReportRequest;
import com.openhis.health.domain.custom.tracenumber.queries.TraceNumberReportPageQuery;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TraceNumberConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 追溯码报表筛选装配：Request → {@link TraceNumberReportPageQuery} 单一真源（TRC-RPT-SDD-R-002）。
 */
@Component
@RequiredArgsConstructor
public class TraceNumberReportQueryAssembler {

    /** 药品关键字预解析。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /**
     * Gate：{@code queryScope=LOCATION} 时 {@code locationId} 必填（TRC-RPT-SDD-R-003）。
     *
     * @param queryScope 查询范围
     * @param locationId 库位主键
     * @throws BusinessException {@link ErrorCodeConstants#TRC_RPT_202}
     */
    public void assertLocationRequiredWhenLocationScope(String queryScope, Long locationId) {
        if (!TraceNumberConstants.REPORT_QUERY_SCOPE_LOCATION.equals(trimToNull(queryScope))) {
            return;
        }
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_202, "请选择库房");
        }
    }

    /**
     * Pre：分页请求 → 领域分页 Query（含药品关键字预解析）。
     *
     * @param request 分页请求
     * @return 领域 Query；药品关键字无命中时 {@code medicationItemIds} 为空列表表示短路空页
     */
    public TraceNumberReportPageQuery buildPageQuery(PageTraceNumberReportRequest request) {
        TraceNumberReportPageQuery query = new TraceNumberReportPageQuery();
        if (request == null) {
            return query;
        }
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        this.applyCommonFilters(query, request.getQueryScope(), request.getLocationId(),
            request.getInHospitalStatusEnum(), request.getLocationStockStatusEnum(),
            request.getTraceNoKeyword(), request.getMedicationKeyword(), request.getLotNumberKeyword(),
            request.getFlowTimeStart(), request.getFlowTimeEnd());
        return query;
    }

    /**
     * Pre：导出请求 → 领域 Query（无分页键；筛选与 IF-02 同构）。
     *
     * @param request 导出请求
     * @return 领域 Query
     */
    public TraceNumberReportPageQuery buildPageQuery(ExportTraceNumberReportRequest request) {
        TraceNumberReportPageQuery query = new TraceNumberReportPageQuery();
        if (request == null) {
            return query;
        }
        this.applyCommonFilters(query, request.getQueryScope(), request.getLocationId(),
            request.getInHospitalStatusEnum(), request.getLocationStockStatusEnum(),
            request.getTraceNoKeyword(), request.getMedicationKeyword(), request.getLotNumberKeyword(),
            request.getFlowTimeStart(), request.getFlowTimeEnd());
        return query;
    }

    /**
     * Pre：药品关键字预解析后是否应短路为空结果。
     *
     * @param query 已 enrich 的 Query
     * @return {@code true} 表示关键字无命中、应返回空页
     */
    public boolean shouldShortCircuitEmptyForMedicationKeyword(TraceNumberReportPageQuery query, String medicationKeyword) {
        if (StringUtils.isEmpty(medicationKeyword)) {
            return false;
        }
        List<Long> medicationItemIds = query.getMedicationItemIds();
        return medicationItemIds == null || medicationItemIds.isEmpty();
    }

    /**
     * 复制 Query 并写入分页键（导出循环用）。
     *
     * @param source   源 Query
     * @param pageNo   页码
     * @param pageSize 页大小
     * @return 新 Query 实例
     */
    public TraceNumberReportPageQuery copyQueryWithPaging(
        TraceNumberReportPageQuery source,
        int pageNo,
        int pageSize) {
        TraceNumberReportPageQuery query = new TraceNumberReportPageQuery();
        if (source == null) {
            query.setPageNo(Integer.valueOf(pageNo));
            query.setPageSize(Integer.valueOf(pageSize));
            return query;
        }
        query.setQueryScope(source.getQueryScope());
        query.setLocationId(source.getLocationId());
        query.setInHospitalStatusEnum(source.getInHospitalStatusEnum());
        query.setLocationStockStatusEnum(source.getLocationStockStatusEnum());
        query.setTraceNoKeyword(source.getTraceNoKeyword());
        query.setMedicationItemIds(source.getMedicationItemIds());
        query.setLotNumberKeyword(source.getLotNumberKeyword());
        query.setFlowTimeStart(source.getFlowTimeStart());
        query.setFlowTimeEnd(source.getFlowTimeEnd());
        query.setPageNo(Integer.valueOf(pageNo));
        query.setPageSize(Integer.valueOf(pageSize));
        return query;
    }

    /**
     * 追溯码报表公共筛选：范围、库位、在院/库存状态、关键字与时间窗。
     *
     * @param query                 待填充 query
     * @param queryScope            查询范围
     * @param locationId            库位 id
     * @param inHospitalStatusEnum  在院状态
     * @param locationStockStatusEnum 库存状态
     * @param traceNoKeyword        追溯码关键字
     * @param medicationKeyword     药品关键字
     * @param lotNumberKeyword      批号关键字
     * @param flowTimeStart         流水起始
     * @param flowTimeEnd           流水截止
     */
    private void applyCommonFilters(
        TraceNumberReportPageQuery query,
        String queryScope,
        Long locationId,
        String inHospitalStatusEnum,
        String locationStockStatusEnum,
        String traceNoKeyword,
        String medicationKeyword,
        String lotNumberKeyword,
        java.time.LocalDateTime flowTimeStart,
        java.time.LocalDateTime flowTimeEnd) {
        query.setQueryScope(trimToNull(queryScope));
        query.setLocationId(locationId);
        query.setInHospitalStatusEnum(trimToNull(inHospitalStatusEnum));
        query.setLocationStockStatusEnum(trimToNull(locationStockStatusEnum));
        query.setTraceNoKeyword(trimToNull(traceNoKeyword));
        query.setLotNumberKeyword(trimToNull(lotNumberKeyword));
        query.setFlowTimeStart(flowTimeStart);
        query.setFlowTimeEnd(flowTimeEnd);
        String normalizedMedicationKeyword = trimToNull(medicationKeyword);
        if (normalizedMedicationKeyword == null) {
            query.setMedicationItemIds(null);
            return;
        }
        List<Long> medicationItemIds =
            this.medicationDefinitionQueryService.listIdsByInventoryDetailKeyword(normalizedMedicationKeyword);
        if (medicationItemIds == null) {
            query.setMedicationItemIds(Collections.emptyList());
            return;
        }
        query.setMedicationItemIds(medicationItemIds);
    }
}
