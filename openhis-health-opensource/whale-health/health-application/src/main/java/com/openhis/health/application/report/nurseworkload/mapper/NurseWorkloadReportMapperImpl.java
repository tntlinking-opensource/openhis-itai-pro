/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.mapper;

import com.openhis.health.application.report.nurseworkload.dto.NurseConsumableDetailExportRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseConsumableDetailRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseExecutionDetailExportRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseExecutionDetailRowDto;
import com.openhis.health.application.report.nurseworkload.dto.NurseExecutionSummaryRowDto;
import com.openhis.health.application.report.nurseworkload.request.ExportNurseWorkloadDetailRequest;
import com.openhis.health.application.report.nurseworkload.request.NurseWorkloadQueryRequest;
import com.openhis.health.application.report.nurseworkload.request.PageNurseConsumableWorkloadDetailRequest;
import com.openhis.health.application.report.nurseworkload.request.PageNurseExecutionWorkloadDetailRequest;
import com.openhis.health.domain.clinical.procedure.queries.NurseWorkloadPageQuery;
import com.openhis.health.domain.clinical.procedure.queries.model.NurseExecutionCategorySummaryQueryModel;
import com.openhis.health.domain.clinical.procedure.queries.model.NurseExecutionWorkloadDetailQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.NurseWorkloadConstants;
import com.openhis.health.domain.share.constants.NurseWorkloadExportConstants;
import com.openhis.health.domain.workflow.devicedispense.queries.model.NurseConsumableWorkloadDetailQueryModel;
import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link NurseWorkloadReportMapper} 实现：逐字段搬运，不含业务推断。
 */
@Component
public class NurseWorkloadReportMapperImpl implements NurseWorkloadReportMapper {

    /**
     * 汇总查询 Request → 领域读查询。
     *
     * @param request 调用方保证非空
     * @return 领域读查询
     */
    @Override
    public NurseWorkloadPageQuery toNurseWorkloadPageQuery(NurseWorkloadQueryRequest request) {
        NurseWorkloadPageQuery query = new NurseWorkloadPageQuery();
        query.setEncounterScope(request.getEncounterScope());
        query.setDateFrom(request.getDateFrom());
        query.setDateTo(request.getDateTo());
        query.setPractitionerId(request.getPractitionerId());
        // 开立科室仅认显式 requestingOrgId；organizationId 由 AppService 解析为工作科室上的护士/医技集合
        query.setRequestingOrgId(request.getRequestingOrgId());
        return query;
    }

    /**
     * 诊疗明细分页 Request → 领域读查询。
     *
     * @param request 调用方保证非空
     * @return 含分页键的领域读查询
     */
    @Override
    public NurseWorkloadPageQuery toNurseWorkloadPageQuery(PageNurseExecutionWorkloadDetailRequest request) {
        NurseWorkloadPageQuery query = new NurseWorkloadPageQuery();
        query.setEncounterScope(request.getEncounterScope());
        query.setDateFrom(request.getDateFrom());
        query.setDateTo(request.getDateTo());
        query.setPractitionerId(request.getPractitionerId());
        query.setRequestingOrgId(request.getRequestingOrgId());
        query.setPageNo(this.resolvePageNo(request.getPageNo()));
        query.setPageSize(this.resolvePageSize(request.getPageSize()));
        return query;
    }

    /**
     * 耗材明细分页 Request → 领域读查询。
     *
     * @param request 调用方保证非空
     * @return 含分页键的领域读查询
     */
    @Override
    public NurseWorkloadPageQuery toNurseWorkloadPageQuery(PageNurseConsumableWorkloadDetailRequest request) {
        NurseWorkloadPageQuery query = new NurseWorkloadPageQuery();
        query.setEncounterScope(request.getEncounterScope());
        query.setDateFrom(request.getDateFrom());
        query.setDateTo(request.getDateTo());
        query.setPractitionerId(request.getPractitionerId());
        query.setRequestingOrgId(request.getRequestingOrgId());
        query.setPageNo(this.resolvePageNo(request.getPageNo()));
        query.setPageSize(this.resolvePageSize(request.getPageSize()));
        return query;
    }

    /**
     * 导出 Request → 领域读查询（不含分页键）。
     *
     * @param request 调用方保证非空
     * @return 领域读查询
     */
    @Override
    public NurseWorkloadPageQuery toNurseWorkloadPageQuery(ExportNurseWorkloadDetailRequest request) {
        NurseWorkloadPageQuery query = new NurseWorkloadPageQuery();
        query.setEncounterScope(request.getEncounterScope());
        query.setDateFrom(request.getDateFrom());
        query.setDateTo(request.getDateTo());
        query.setPractitionerId(request.getPractitionerId());
        query.setRequestingOrgId(request.getRequestingOrgId());
        return query;
    }

    /**
     * 诊疗汇总 QueryModel → 汇总行 DTO。
     *
     * @param model           领域汇总行；可为 null
     * @param categoryDisplay 类目展示名
     * @return DTO；model 为 null 时返回 null
     */
    @Override
    public NurseExecutionSummaryRowDto toSummaryRowDto(
        NurseExecutionCategorySummaryQueryModel model,
        String categoryDisplay) {
        if (model == null) {
            return null;
        }
        NurseExecutionSummaryRowDto dto = new NurseExecutionSummaryRowDto();
        dto.setCategoryEnum(model.getCategoryEnum());
        dto.setCategoryDisplay(categoryDisplay);
        dto.setExecutionCount(model.getExecutionCount());
        return dto;
    }

    /**
     * 诊疗明细 QueryModel → 明细行 DTO（展示字段由 AssemblySupport enrich）。
     *
     * @param model 领域明细行；可为 null
     * @return DTO；model 为 null 时返回 null
     */
    @Override
    public NurseExecutionDetailRowDto toDetailRowDto(NurseExecutionWorkloadDetailQueryModel model) {
        if (model == null) {
            return null;
        }
        NurseExecutionDetailRowDto dto = new NurseExecutionDetailRowDto();
        dto.setProcedureId(model.getProcedureId());
        dto.setServiceRequestId(model.getServiceRequestId());
        dto.setCategoryEnum(model.getCategoryEnum());
        dto.setPractitionerId(model.getPractitionerId());
        dto.setRecordedTime(model.getRecordedTime());
        dto.setEncounterId(model.getEncounterId());
        dto.setPatientId(model.getPatientId());
        return dto;
    }

    /**
     * 耗材明细 QueryModel → 明细行 DTO（展示字段由 AssemblySupport enrich）。
     *
     * @param model 领域明细行；可为 null
     * @return DTO；model 为 null 时返回 null
     */
    @Override
    public NurseConsumableDetailRowDto toDetailRowDto(NurseConsumableWorkloadDetailQueryModel model) {
        if (model == null) {
            return null;
        }
        NurseConsumableDetailRowDto dto = new NurseConsumableDetailRowDto();
        dto.setDispenseId(model.getDispenseId());
        dto.setDeviceDefId(model.getDeviceDefId());
        dto.setHvcmFlag(model.getHvcmFlag());
        dto.setPractitionerId(model.getPractitionerId());
        dto.setBusinessTime(model.getBusinessTime());
        dto.setQuantity(model.getQuantity());
        dto.setUnitCode(model.getUnitCode());
        dto.setEncounterId(model.getEncounterId());
        dto.setPatientId(model.getPatientId());
        return dto;
    }

    /**
     * 诊疗明细 DTO → 导出行 DTO。
     *
     * @param row 已 enrich 的明细行；可为 null
     * @return 导出行；row 为 null 时返回 null
     */
    @Override
    public NurseExecutionDetailExportRowDto toExecutionExportRowDto(NurseExecutionDetailRowDto row) {
        if (row == null) {
            return null;
        }
        NurseExecutionDetailExportRowDto exportRow = new NurseExecutionDetailExportRowDto();
        exportRow.setItemName(row.getItemName());
        exportRow.setCategoryDisplay(row.getCategoryDisplay());
        exportRow.setGenerateSourceDisplay(row.getGenerateSourceDisplay());
        exportRow.setPractitionerName(row.getPractitionerName());
        exportRow.setRecordedTimeText(this.formatDateTime(row.getRecordedTime()));
        exportRow.setPatientName(row.getPatientName());
        exportRow.setEncounterBusNo(row.getEncounterBusNo());
        return exportRow;
    }

    /**
     * 耗材明细 DTO → 导出行 DTO。
     *
     * @param row 已 enrich 的明细行；可为 null
     * @return 导出行；row 为 null 时返回 null
     */
    @Override
    public NurseConsumableDetailExportRowDto toConsumableExportRowDto(NurseConsumableDetailRowDto row) {
        if (row == null) {
            return null;
        }
        NurseConsumableDetailExportRowDto exportRow = new NurseConsumableDetailExportRowDto();
        exportRow.setDeviceName(row.getDeviceName());
        exportRow.setSpecText(row.getSpecText());
        exportRow.setHvcmDisplay(row.getHvcmDisplay());
        exportRow.setGenerateSourceDisplay(row.getGenerateSourceDisplay());
        exportRow.setPractitionerName(row.getPractitionerName());
        exportRow.setBusinessTimeText(this.formatDateTime(row.getBusinessTime()));
        exportRow.setQuantity(row.getQuantity());
        exportRow.setUnitCodeLabel(row.getUnitCodeLabel());
        exportRow.setPatientName(row.getPatientName());
        exportRow.setEncounterBusNo(row.getEncounterBusNo());
        return exportRow;
    }

    /**
     * 诊疗执行明细导出行 → Excel 字节（可按前端列清单裁剪）。
     *
     * @param rows    导出行列表；调用方保证非 null
     * @param columns 前端自选列；null/空则全部 {@code @Excel} 列
     * @return xlsx 二进制
     */
    @Override
    public byte[] buildExecutionExcelBytes(
        List<NurseExecutionDetailExportRowDto> rows,
        List<ExcelExportColumnDto> columns) {
        return AnnotatedExcelExporter.exportToXlsxBytes(
            NurseWorkloadExportConstants.EXECUTION_SHEET_NAME,
            rows,
            NurseExecutionDetailExportRowDto.class,
            columns);
    }

    /**
     * 耗材使用明细导出行 → Excel 字节（可按前端列清单裁剪）。
     *
     * @param rows    导出行列表；调用方保证非 null
     * @param columns 前端自选列；null/空则全部 {@code @Excel} 列
     * @return xlsx 二进制
     */
    @Override
    public byte[] buildConsumableExcelBytes(
        List<NurseConsumableDetailExportRowDto> rows,
        List<ExcelExportColumnDto> columns) {
        return AnnotatedExcelExporter.exportToXlsxBytes(
            NurseWorkloadExportConstants.CONSUMABLE_SHEET_NAME,
            rows,
            NurseConsumableDetailExportRowDto.class,
            columns);
    }

    /**
     * 解析页码（缺省 1）。
     *
     * @param pageNo 请求页码
     * @return 有效页码
     */
    private int resolvePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            // 未传或非法页码回落第 1 页（pageNo 从 1 开始）
            return 1;
        }
        return pageNo;
    }

    /**
     * 解析页大小（缺省 20，上限 1000）。
     *
     * @param pageSize 请求页大小
     * @return 有效页大小
     */
    private int resolvePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            // 缺省 20 条/页（NurseWorkloadConstants.DEFAULT_PAGE_SIZE）
            return NurseWorkloadConstants.DEFAULT_PAGE_SIZE;
        }
        if (pageSize > NurseWorkloadConstants.MAX_PAGE_SIZE) {
            // IF-RPT-NWL-03 上限 1000
            return NurseWorkloadConstants.MAX_PAGE_SIZE;
        }
        return pageSize;
    }

    /**
     * 格式化时间为导出展示串。
     *
     * @param dateTime 时间；可为 null
     * @return 格式化文本；null 时返回空串
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(GeneralSharedConstants.DATE_TIME_FORMATTER);
    }
}
