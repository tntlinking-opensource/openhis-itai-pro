/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.tracenumber.mapper;

import com.zxplat.application.report.tracenumber.dto.TraceNumberRecordRowDto;
import com.zxplat.application.report.tracenumber.dto.TraceNumberReportExportRowDto;
import com.zxplat.application.report.tracenumber.dto.TraceNumberReportRowDto;
import com.zxplat.application.report.tracenumber.response.GetTraceNumberOverviewResponse;
import com.zxplat.application.report.tracenumber.response.PageTraceNumberReportResponse;
import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberQueryModel;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberRecordQueryModel;
import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberReportRowQueryModel;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.share.enums.InHospitalStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link TraceNumberReportMapper} 实现。
 */
@Component
public class TraceNumberReportMapperImpl implements TraceNumberReportMapper {

    /**
     * 分页 QueryModel → 列表 Response 壳。
     *
     * @param page 领域分页
     * @param rows 已 enrich 的列表 DTO
     * @return 分页响应
     */
    @Override
    public PageTraceNumberReportResponse toPageResponse(Page<TraceNumberReportRowQueryModel> page, List<TraceNumberReportRowDto> rows) {
        PageTraceNumberReportResponse response = new PageTraceNumberReportResponse();
        List<TraceNumberReportRowDto> safeRows = rows;
        if (safeRows == null) {
            safeRows = new ArrayList<>();
        }
        return PagedResultBuilder.build(response, safeRows, page);
    }

    /**
     * 报表行 QueryModel → 列表 DTO。
     *
     * @param model 领域行；null 时返回 null
     * @return 列表 DTO
     */
    @Override
    public TraceNumberReportRowDto toRowDto(TraceNumberReportRowQueryModel model) {
        if (model == null) {
            return null;
        }
        TraceNumberReportRowDto dto = new TraceNumberReportRowDto();
        dto.setTraceNo(model.getTraceNo());
        dto.setMedicationId(model.getItemId());
        dto.setLotNumber(model.getLotNumber());
        dto.setInHospitalStatusEnum(model.getInHospitalStatusEnum());
        dto.setRemainingSplitQuantity(model.getRemainingSplitQuantity());
        dto.setTraceUnitCode(model.getTraceUnitCode());
        dto.setLocationStockStatusEnum(model.getLocationStockStatusEnum());
        dto.setLocationNetSplitQuantity(model.getLocationNetSplitQuantity());
        dto.setCurrentLocationIds(model.getCurrentLocationIds());
        dto.setLastFlowAt(model.getLastFlowAt());
        return dto;
    }

    /**
     * 批量映射报表行。
     *
     * @param models 领域行列表
     * @return DTO 列表
     */
    @Override
    public List<TraceNumberReportRowDto> toRowDtoList(List<TraceNumberReportRowQueryModel> models) {
        List<TraceNumberReportRowDto> result = new ArrayList<>();
        if (models == null) {
            return result;
        }
        for (TraceNumberReportRowQueryModel model : models) {
            TraceNumberReportRowDto dto = this.toRowDto(model);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    /**
     * 主档 QueryModel + 药品 enrich → 概览 Response。
     *
     * @param model      主档
     * @param medication 药品定义；可 null
     * @return 概览响应
     */
    @Override
    public GetTraceNumberOverviewResponse toOverviewResponse(
        TraceNumberQueryModel model,
        MedicationDefinition medication
    ) {
        if (model == null) {
            return null;
        }
        GetTraceNumberOverviewResponse response = new GetTraceNumberOverviewResponse();
        response.setTraceNo(model.getTraceNo());
        response.setMedicationId(model.getItemId());
        response.setLotNumber(model.getLotNumber());
        response.setTraceUnitCode(model.getTraceUnitCode());
        response.setRemainingSplitQuantity(model.getRemainingSplitQuantity());
        response.setInHospitalStatusEnum(model.getInHospitalStatusEnum());
        InHospitalStatus inHospitalStatus = EnumUtil.fromCode(InHospitalStatus.class, model.getInHospitalStatusEnum());
        if (inHospitalStatus != null) {
            response.setInHospitalStatusLabel(inHospitalStatus.getDisplay());
        }
        if (medication != null) {
            response.setMedicationCode(medication.getBusNo());
            response.setMedicationName(medication.getName());
            response.setSpecificationText(medication.getTotalVolume());
        }
        return response;
    }

    /**
     * 流水 QueryModel → 明细 DTO。
     *
     * @param model 流水读模型；null 时返回 null
     * @return 明细 DTO
     */
    @Override
    public TraceNumberRecordRowDto toRecordRowDto(TraceNumberRecordQueryModel model) {
        if (model == null) {
            return null;
        }
        TraceNumberRecordRowDto dto = new TraceNumberRecordRowDto();
        dto.setId(model.getId());
        // Asm：领域 record* → 对客 flow*（与 AS-CUS-TRC-002 §3.5 对齐）
        dto.setFlowCategoryEnum(model.getRecordCategoryEnum());
        dto.setFlowDirectionEnum(model.getRecordDirectionEnum());
        dto.setLocationId(model.getLocationId());
        dto.setQuantity(model.getQuantity());
        dto.setUnitCode(model.getUnitCode());
        dto.setOperationType(model.getOperationType());
        dto.setBusNo(model.getBusNo());
        dto.setCreatedAt(model.getCreatedAt());
        return dto;
    }

    /**
     * 批量映射流水明细。
     *
     * @param models 流水读模型列表
     * @return 明细 DTO 列表
     */
    @Override
    public List<TraceNumberRecordRowDto> toRecordRowDtoList(List<TraceNumberRecordQueryModel> models) {
        List<TraceNumberRecordRowDto> result = new ArrayList<>();
        if (models == null) {
            return result;
        }
        for (TraceNumberRecordQueryModel model : models) {
            TraceNumberRecordRowDto dto = this.toRecordRowDto(model);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    /**
     * 列表 DTO → 导出行 DTO。
     *
     * @param row 列表行；null 时返回 null
     * @return 导出行
     */
    @Override
    public TraceNumberReportExportRowDto toExportRowDto(TraceNumberReportRowDto row) {
        if (row == null) {
            return null;
        }
        TraceNumberReportExportRowDto exportRow = new TraceNumberReportExportRowDto();
        exportRow.setTraceNo(row.getTraceNo());
        exportRow.setMedicationCode(row.getMedicationCode());
        exportRow.setMedicationName(row.getMedicationName());
        exportRow.setLotNumber(row.getLotNumber());
        exportRow.setInHospitalStatusLabel(row.getInHospitalStatusLabel());
        exportRow.setRemainingSplitQuantity(row.getRemainingSplitQuantity());
        exportRow.setTraceUnitCodeLabel(row.getTraceUnitCode());
        exportRow.setCurrentLocationNames(row.getCurrentLocationNames());
        if (row.getLastFlowAt() != null) {
            exportRow.setLastFlowAtLabel(
                GeneralSharedConstants.DATE_TIME_FORMATTER.format(row.getLastFlowAt()));
        }
        return exportRow;
    }

    /**
     * 批量映射导出行。
     *
     * @param rows 列表行
     * @return 导出行列表
     */
    @Override
    public List<TraceNumberReportExportRowDto> toExportRowDtoList(List<TraceNumberReportRowDto> rows) {
        List<TraceNumberReportExportRowDto> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (TraceNumberReportRowDto row : rows) {
            TraceNumberReportExportRowDto exportRow = this.toExportRowDto(row);
            if (exportRow != null) {
                result.add(exportRow);
            }
        }
        return result;
    }
}
