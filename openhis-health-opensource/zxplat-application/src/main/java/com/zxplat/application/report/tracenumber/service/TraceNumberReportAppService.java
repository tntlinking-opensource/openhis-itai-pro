/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.tracenumber.service;

import com.zxplat.application.report.tracenumber.dto.TraceNumberRecordRowDto;
import com.zxplat.application.report.tracenumber.dto.TraceNumberReportExportRowDto;
import com.zxplat.application.report.tracenumber.dto.TraceNumberReportRowDto;
import com.zxplat.application.report.tracenumber.mapper.TraceNumberReportMapper;
import com.zxplat.application.report.tracenumber.request.ExportTraceNumberReportRequest;
import com.zxplat.application.report.tracenumber.request.GetTraceNumberOverviewRequest;
import com.zxplat.application.report.tracenumber.request.ListTraceRecordsRequest;
import com.zxplat.application.report.tracenumber.request.LoadTraceNumberReportFormInitRequest;
import com.zxplat.application.report.tracenumber.request.PageTraceNumberReportRequest;
import com.zxplat.application.report.tracenumber.response.ExportTraceNumberReportResponse;
import com.zxplat.application.report.tracenumber.response.GetTraceNumberOverviewResponse;
import com.zxplat.application.report.tracenumber.response.ListTraceRecordsResponse;
import com.zxplat.application.report.tracenumber.response.LoadTraceNumberReportFormInitResponse;
import com.zxplat.application.report.tracenumber.response.PageTraceNumberReportResponse;
import com.zxplat.application.report.tracenumber.support.TraceNumberReportEnrichmentEnricher;
import com.zxplat.application.report.tracenumber.support.TraceNumberReportPatientEnricher;
import com.zxplat.application.report.tracenumber.support.TraceNumberReportQueryAssembler;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.custom.tracenumber.queries.TraceNumberReportPageQuery;
import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberQueryModel;
import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberRecordQueryModel;
import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberReportRowQueryModel;
import com.zxplat.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.InventoryWarehouseDetailExportConstants;
import com.zxplat.domain.share.constants.TraceNumberConstants;
import com.zxplat.domain.share.constants.TraceNumberReportExportConstants;
import com.zxplat.domain.share.enums.InHospitalStatus;
import com.zxplat.domain.share.enums.LocationStockStatus;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.AnnotatedExcelExporter;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 追溯码查询报表只读编排（AS-CUS-TRC-002 · IF-TRC-RPT-01～05）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link TraceNumberQueryService}：主/子表读真源</li>
 *   <li>{@link TraceNumberReportQueryAssembler}：筛选单一真源</li>
 *   <li>{@link TraceNumberReportEnrichmentEnricher}：药品/枚举 Label enrich</li>
 *   <li>{@link TraceNumberReportPatientEnricher}：明细 Tab 患者 enrich</li>
 * </ul>
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/traceNumberReport/{methodName}。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TraceNumberReportAppService {

    /** 分页窗口 Gate。 */
    private final PageWindowGate pageWindowGate;

    /** 追溯码读侧。 */
    private final TraceNumberQueryService traceNumberQueryService;

    /** 库位读侧。 */
    private final LocationQueryService locationQueryService;

    /** 药品读侧。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 枚举 Option 加载。 */
    private final CodeSystemQueryService codeSystemQueryService;

    /** 筛选装配。 */
    private final TraceNumberReportQueryAssembler traceNumberReportQueryAssembler;

    /** 展示 enrich。 */
    private final TraceNumberReportEnrichmentEnricher traceNumberReportEnrichmentEnricher;

    /** 患者 enrich。 */
    private final TraceNumberReportPatientEnricher traceNumberReportPatientEnricher;

    /** DTO 映射。 */
    private final TraceNumberReportMapper traceNumberReportMapper;

    /**
     * 初始化筛选项（IF-TRC-RPT-01）。
     *
     * @param request 空壳请求
     * @return 下拉选项
     * @throws BusinessException {@link ErrorCodeConstants#TRC_RPT_201}
     */
    public LoadTraceNumberReportFormInitResponse loadTraceNumberReportFormInit(LoadTraceNumberReportFormInitRequest request) {
        // Gate：租户须有效
        if (TenantUtils.getCurrentTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_201, "筛选项加载失败");
        }
        LoadTraceNumberReportFormInitResponse response = new LoadTraceNumberReportFormInitResponse();
        try {
            // Asm：查询范围、在院态、本库态枚举
            response.setQueryScopeOptions(this.buildQueryScopeOptions());
            response.setInHospitalStatusOptions(this.codeSystemQueryService.getOptionListByEnum(InHospitalStatus.class));
            response.setLocationStockStatusOptions(this.codeSystemQueryService.getOptionListByEnum(LocationStockStatus.class));
            // Pre：药库+药房+科室药房并集
            response.setLocationOptions(this.buildLocationOptions());
        } catch (RuntimeException ex) {
            log.warn("追溯码报表初始化失败", ex);
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_201, "筛选项加载失败");
        }
        return response;
    }

    /**
     * 追溯码列表分页（IF-TRC-RPT-02）。
     *
     * @param request 组合筛选 + 分页
     * @return 分页列表
     * @throws BusinessException 参数非法或查询失败
     */
    public PageTraceNumberReportResponse pageTraceNumberReport(PageTraceNumberReportRequest request) {
        // Gate：租户 + 分页窗口
        if (TenantUtils.getCurrentTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_201, "列表加载失败");
        }
        pageWindowGate.requirePositivePageWindow(request, ErrorCodeConstants.TRC_RPT_203, "列表加载失败");
        traceNumberReportQueryAssembler.assertLocationRequiredWhenLocationScope(
            request.getQueryScope(), request.getLocationId());
        try {
            // Pre：筛选 → 领域 Query
            TraceNumberReportPageQuery query = traceNumberReportQueryAssembler.buildPageQuery(request);
            if (traceNumberReportQueryAssembler.shouldShortCircuitEmptyForMedicationKeyword(
                query, request.getMedicationKeyword())) {
                PageTraceNumberReportResponse empty = traceNumberReportMapper.toPageResponse(null, List.of());
                this.applyDeepLinkFields(empty, request);
                return empty;
            }
            Page<TraceNumberReportRowQueryModel> page = traceNumberQueryService.pageTraceNumberReport(query);
            List<TraceNumberReportRowDto> rows = traceNumberReportMapper.toRowDtoList(page.getContent());
            // Pre：药品 + 当前所在库房 enrich
            Map<Long, MedicationDefinition> medicationById = this.loadMedicationByIdForRows(rows);
            Map<Long, String> locationNameById = this.loadLocationNameByIdForReportRows(rows);
            // Pre：指定库房时把所选库房名并入映射，保证「当前所在库房」必有值
            this.putSelectedLocationName(locationNameById, request.getQueryScope(), request.getLocationId());
            traceNumberReportEnrichmentEnricher.enrichListRows(
                rows, medicationById, locationNameById, request.getQueryScope());
            this.fillSelectedLocationNameWhenMissing(rows, request.getQueryScope(), request.getLocationId(), locationNameById);
            // Asm：分页壳 + deep link 回显
            PageTraceNumberReportResponse response = traceNumberReportMapper.toPageResponse(page, rows);
            this.applyDeepLinkFields(response, request);
            return response;
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException ex) {
            log.warn("追溯码报表列表查询失败", ex);
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_203, "列表加载失败");
        }
    }

    /**
     * 单码概览（IF-TRC-RPT-03）。
     *
     * @param request 追溯码
     * @return 主档概览
     * @throws BusinessException 码不存在时 {@link ErrorCodeConstants#TRC_RPT_204}
     */
    public GetTraceNumberOverviewResponse getTraceNumberOverview(GetTraceNumberOverviewRequest request) {
        // Gate：租户 + traceNo 非空
        if (TenantUtils.getCurrentTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_101, "请提供追溯码");
        }
        String traceNoRaw = null;
        if (request != null) {
            traceNoRaw = request.getTraceNo();
        }
        String traceNo = trimToNull(traceNoRaw);
        if (traceNo == null) {
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_101, "请提供追溯码");
        }
        try {
            // Pre：读主档
            TraceNumberQueryModel model = traceNumberQueryService.getTraceNumberByTraceNo(traceNo);
            MedicationDefinition medication = null;
            if (model.getItemId() != null) {
                List<MedicationDefinition> definitions =
                    medicationDefinitionQueryService.findAllByIds(List.of(model.getItemId()));
                if (!definitions.isEmpty()) {
                    medication = definitions.get(0);
                }
            }
            // Asm：映射概览
            return traceNumberReportMapper.toOverviewResponse(model, medication);
        } catch (BusinessException businessException) {
            if (ErrorCodeConstants.TRACE_NUMBER_NOT_FOUND.equals(businessException.getErrorCode())) {
                throw new BusinessException(ErrorCodeConstants.TRC_RPT_204, "未找到该追溯码");
            }
            throw businessException;
        }
    }

    /**
     * 单码流转明细（IF-TRC-RPT-04）。
     *
     * @param request 追溯码
     * @return 时间序流水
     * @throws BusinessException 码不存在时
     */
    public ListTraceRecordsResponse listTraceRecords(ListTraceRecordsRequest request) {
        // Gate：租户 + traceNo 非空
        if (TenantUtils.getCurrentTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_101, "请提供追溯码");
        }
        String traceNoRaw = null;
        if (request != null) {
            traceNoRaw = request.getTraceNo();
        }
        String traceNo = trimToNull(traceNoRaw);
        if (traceNo == null) {
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_101, "请提供追溯码");
        }
        try {
            // Pre：主档存在性 Gate
            traceNumberQueryService.getTraceNumberByTraceNo(traceNo);
            List<TraceNumberRecordQueryModel> models = traceNumberQueryService.listRecordsByTraceNo(traceNo);
            List<TraceNumberRecordRowDto> rows =
                traceNumberReportMapper.toRecordRowDtoList(models);
            // Pre：库位名 + 患者 enrich
            Map<Long, String> locationNameById = this.loadLocationNameByIdForRecords(rows);
            traceNumberReportPatientEnricher.enrichPatientNames(rows);
            traceNumberReportEnrichmentEnricher.enrichFlowLabels(rows, locationNameById);
            // Asm：组装响应
            ListTraceRecordsResponse response = new ListTraceRecordsResponse();
            response.setTraceNo(traceNo);
            response.setRecords(rows);
            return response;
        } catch (BusinessException businessException) {
            if (ErrorCodeConstants.TRACE_NUMBER_NOT_FOUND.equals(businessException.getErrorCode())) {
                throw new BusinessException(ErrorCodeConstants.TRC_RPT_204, "未找到该追溯码");
            }
            throw businessException;
        }
    }

    /**
     * 导出 Excel（IF-TRC-RPT-05）。
     *
     * @param request 与列表同筛选
     * @return Excel 字节
     * @throws BusinessException 导出失败时
     */
    public ExportTraceNumberReportResponse exportTraceNumberReport(ExportTraceNumberReportRequest request) {
        // Gate：租户 + 库房范围
        if (TenantUtils.getCurrentTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_205, "导出失败，请稍后重试");
        }
        traceNumberReportQueryAssembler.assertLocationRequiredWhenLocationScope(
            request.getQueryScope(), request.getLocationId());
        try {
            List<TraceNumberReportRowDto> allRows = this.fetchAllReportRows(request);
            List<TraceNumberReportExportRowDto> exportRows = traceNumberReportMapper.toExportRowDtoList(allRows);
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                TraceNumberReportExportConstants.SHEET_NAME,
                exportRows,
                TraceNumberReportExportRowDto.class,
                request.getExportColumns());
            ExportTraceNumberReportResponse response = new ExportTraceNumberReportResponse();
            String fileName = TraceNumberReportExportConstants.FILE_NAME_PREFIX + "_"
                + GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER.format(LocalDateTime.now())
                + ".xlsx";
            response.setFileName(fileName);
            response.setContentType(InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE);
            response.setBody(body);
            response.setExportedRowCount(exportRows.size());
            boolean truncated = exportRows.size() >= TraceNumberReportExportConstants.EXPORT_MAX_ROWS;
            response.setTruncated(truncated);
            return response;
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (RuntimeException ex) {
            log.warn("追溯码报表导出失败", ex);
            throw new BusinessException(ErrorCodeConstants.TRC_RPT_205, "导出失败，请稍后重试");
        }
    }

    /**
     * 分页循环拉取全量列表行（导出用）。
     *
     * @param request 导出筛选
     * @return 全量行（上限 {@link TraceNumberReportExportConstants#EXPORT_MAX_ROWS}）
     */
    private List<TraceNumberReportRowDto> fetchAllReportRows(ExportTraceNumberReportRequest request) {
        TraceNumberReportPageQuery baseQuery = traceNumberReportQueryAssembler.buildPageQuery(request);
        if (traceNumberReportQueryAssembler.shouldShortCircuitEmptyForMedicationKeyword(
            baseQuery, request.getMedicationKeyword())) {
            return List.of();
        }
        List<TraceNumberReportRowDto> allRows = new ArrayList<>();
        int pageNo = 1;
        boolean truncated = false;
        while (allRows.size() < TraceNumberReportExportConstants.EXPORT_MAX_ROWS) {
            TraceNumberReportPageQuery pageQuery = traceNumberReportQueryAssembler.copyQueryWithPaging(
                baseQuery, pageNo, TraceNumberReportExportConstants.EXPORT_PAGE_SIZE);
            Page<TraceNumberReportRowQueryModel> page = traceNumberQueryService.pageTraceNumberReport(pageQuery);
            List<TraceNumberReportRowDto> rows = traceNumberReportMapper.toRowDtoList(page.getContent());
            Map<Long, MedicationDefinition> medicationById = this.loadMedicationByIdForRows(rows);
            Map<Long, String> locationNameById = this.loadLocationNameByIdForReportRows(rows);
            this.putSelectedLocationName(locationNameById, request.getQueryScope(), request.getLocationId());
            traceNumberReportEnrichmentEnricher.enrichListRows(
                rows, medicationById, locationNameById, request.getQueryScope());
            this.fillSelectedLocationNameWhenMissing(
                rows, request.getQueryScope(), request.getLocationId(), locationNameById);
            for (TraceNumberReportRowDto row : rows) {
                if (allRows.size() >= TraceNumberReportExportConstants.EXPORT_MAX_ROWS) {
                    truncated = true;
                    break;
                }
                allRows.add(row);
            }
            if (truncated || page.getContent().isEmpty()
                || page.getContent().size() < TraceNumberReportExportConstants.EXPORT_PAGE_SIZE) {
                break;
            }
            pageNo = pageNo + 1;
        }
        if (truncated) {
            log.info("追溯码报表导出触达上限 {}", TraceNumberReportExportConstants.EXPORT_MAX_ROWS);
        }
        return allRows;
    }

    /**
     * 批量加载列表行关联药品。
     *
     * @param rows 列表 DTO
     * @return id → 药品定义
     */
    private Map<Long, MedicationDefinition> loadMedicationByIdForRows(List<TraceNumberReportRowDto> rows) {
        Set<Long> medicationIds = new HashSet<>();
        if (rows != null) {
            for (TraceNumberReportRowDto row : rows) {
                if (row != null && row.getMedicationId() != null) {
                    medicationIds.add(row.getMedicationId());
                }
            }
        }
        if (medicationIds.isEmpty()) {
            return Map.of();
        }
        List<MedicationDefinition> definitions = medicationDefinitionQueryService.findAllByIds(medicationIds);
        return traceNumberReportEnrichmentEnricher.indexMedicationsById(definitions);
    }

    /**
     * 指定库房查询时，把所选库房名写入映射，供列表「当前所在库房」展示。
     *
     * @param locationNameById 库位 id → 名称
     * @param queryScope       查询范围
     * @param locationId       所选库房
     */
    private void putSelectedLocationName(
        Map<Long, String> locationNameById,
        String queryScope,
        Long locationId) {
        if (!TraceNumberConstants.REPORT_QUERY_SCOPE_LOCATION.equals(trimToNull(queryScope))) {
            return;
        }
        if (locationId == null || locationNameById == null) {
            return;
        }
        if (locationNameById.containsKey(locationId)) {
            return;
        }
        String name = locationQueryService.findNameById(locationId);
        if (trimToNull(name) != null) {
            locationNameById.put(locationId, name);
        }
    }

    /**
     * 指定库房命中行若未解析出当前所在库房名，回填为所选库房名（能查出即在本库）。
     *
     * @param rows             列表行
     * @param queryScope       查询范围
     * @param locationId       所选库房
     * @param locationNameById 库位名映射
     */
    private void fillSelectedLocationNameWhenMissing(
        List<TraceNumberReportRowDto> rows,
        String queryScope,
        Long locationId,
        Map<Long, String> locationNameById) {
        if (!TraceNumberConstants.REPORT_QUERY_SCOPE_LOCATION.equals(trimToNull(queryScope))) {
            return;
        }
        if (rows == null || locationId == null || locationNameById == null) {
            return;
        }
        String selectedName = locationNameById.get(locationId);
        if (trimToNull(selectedName) == null) {
            return;
        }
        for (TraceNumberReportRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (trimToNull(row.getCurrentLocationNames()) != null) {
                continue;
            }
            row.setCurrentLocationNames(selectedName);
        }
    }

    /**
     * 批量加载列表行「当前所在库房」名称。
     *
     * @param rows 列表 DTO
     * @return 库位 id → 名称
     */
    private Map<Long, String> loadLocationNameByIdForReportRows(List<TraceNumberReportRowDto> rows) {
        Set<Long> locationIds = new HashSet<>();
        if (rows != null) {
            for (TraceNumberReportRowDto row : rows) {
                if (row == null || row.getCurrentLocationIds() == null) {
                    continue;
                }
                for (Long locationId : row.getCurrentLocationIds()) {
                    if (locationId != null) {
                        locationIds.add(locationId);
                    }
                }
            }
        }
        if (locationIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(locationQueryService.mapNamesByIds(locationIds));
    }

    /**
     * 批量加载流水行库位名。
     *
     * @param rows 流水 DTO
     * @return 库位 id → 名称
     */
    private Map<Long, String> loadLocationNameByIdForRecords(
        List<TraceNumberRecordRowDto> rows) {
        Map<Long, String> locationNameById = new LinkedHashMap<>();
        if (rows == null) {
            return locationNameById;
        }
        for (TraceNumberRecordRowDto row : rows) {
            if (row == null || row.getLocationId() == null) {
                continue;
            }
            if (locationNameById.containsKey(row.getLocationId())) {
                continue;
            }
            locationQueryService.findOptionalById(row.getLocationId()).ifPresent(location -> {
                locationNameById.put(location.getId(), location.getName());
            });
        }
        return locationNameById;
    }

    /**
     * deep link 字段回显。
     *
     * @param response 分页响应
     * @param request  分页请求
     */
    private void applyDeepLinkFields(PageTraceNumberReportResponse response, PageTraceNumberReportRequest request) {
        if (response == null || request == null) {
            return;
        }
        response.setPrefillTraceNo(trimToNull(request.getTraceNo()));
        response.setOpenDetail(request.getOpenDetail());
    }

    /**
     * 构建查询范围 Option。
     *
     * @return HOSPITAL / LOCATION
     */
    private List<OptionDto> buildQueryScopeOptions() {
        List<OptionDto> options = new ArrayList<>();
        OptionDto hospital = new OptionDto();
        hospital.setValue(TraceNumberConstants.REPORT_QUERY_SCOPE_HOSPITAL);
        hospital.setLabel("全院");
        options.add(hospital);
        OptionDto location = new OptionDto();
        location.setValue(TraceNumberConstants.REPORT_QUERY_SCOPE_LOCATION);
        location.setLabel("指定库房");
        options.add(location);
        return options;
    }

    /**
     * 药库+药房+科室药房并集库位 Option（TRC-RPT-SDD-R-004）。
     *
     * @return 库位下拉
     */
    private List<OptionDto> buildLocationOptions() {
        Map<Long, Location> locationById = new LinkedHashMap<>();
        this.mergeLocations(locationById, locationQueryService.listAllActiveWarehouses());
        this.mergeLocations(locationById, locationQueryService.listAllActivePharmacies());
        this.mergeLocations(locationById, locationQueryService.listAllActiveDepartmentPharmacies());
        List<OptionDto> options = new ArrayList<>();
        for (Location location : locationById.values()) {
            if (location == null || location.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(location.getId()));
            option.setLabel(location.getName());
            options.add(option);
        }
        return options;
    }

    /**
     * 合并库位列表到 id 索引（去重保序）。
     *
     * @param locationById 目标索引
     * @param locations    源列表
     */
    private void mergeLocations(Map<Long, Location> locationById, List<Location> locations) {
        if (locations == null) {
            return;
        }
        for (Location location : locations) {
            if (location != null && location.getId() != null) {
                locationById.putIfAbsent(location.getId(), location);
            }
        }
    }
}
