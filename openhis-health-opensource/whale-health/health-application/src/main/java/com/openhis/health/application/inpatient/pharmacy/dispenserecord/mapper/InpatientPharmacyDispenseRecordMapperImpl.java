/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispenserecord.mapper;

import com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto.InpatientPharmacyDispenseRecordExportRowDto;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto.InpatientPharmacyDispenseRecordRowDto;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.request.ExportInpatientPharmacyDispenseRecordsRequest;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.request.PageInpatientPharmacyDispenseRecordsRequest;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.response.ExportInpatientPharmacyDispenseRecordsResponse;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.response.LoadInpatientPharmacyDispenseRecordPageInitResponse;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.response.PageInpatientPharmacyDispenseRecordsResponse;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.mapper.OutpatientPharmacyDispenseRecordMapper;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordExportConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto.InpatientPharmacyDispenseRecordQueryRowDto;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link InpatientPharmacyDispenseRecordMapper} 实现。
 */
@Component
@RequiredArgsConstructor
public class InpatientPharmacyDispenseRecordMapperImpl implements InpatientPharmacyDispenseRecordMapper {

    private final OutpatientPharmacyDispenseRecordMapper outpatientPharmacyDispenseRecordMapper;

    /**
     * 分页查询请求 → 发放域记录查询入参。
     *
     * @param request 分页查询请求；调用方保证非空
     * @return 领域查询入参
     */
    @Override
    public MedicationDispenseRecordPageQuery toMedicationDispenseRecordPageQuery(
        PageInpatientPharmacyDispenseRecordsRequest request) {
        MedicationDispenseRecordPageQuery query = new MedicationDispenseRecordPageQuery();
        PageQueryBinder.bind(request, query);
        this.applyFilterFields(query, request.getDispenseDateFrom(), request.getDispenseDateTo(),
            request.getRecordTypeCode(), request.getPharmacyLocationId(), request.getDispenserPractitionerId());
        return query;
    }

    /**
     * 导出请求 → 发放域记录查询入参（含导出行上限页大小）。
     *
     * @param request 导出请求；调用方保证非空
     * @return 领域查询入参
     */
    @Override
    public MedicationDispenseRecordPageQuery toMedicationDispenseRecordPageQuery(
        ExportInpatientPharmacyDispenseRecordsRequest request) {
        MedicationDispenseRecordPageQuery query = new MedicationDispenseRecordPageQuery();
        query.setPageNo(1);
        query.setPageSize(PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS);
        this.applyFilterFields(query, request.getDispenseDateFrom(), request.getDispenseDateTo(),
            request.getRecordTypeCode(), request.getPharmacyLocationId(), request.getDispenserPractitionerId());
        return query;
    }

    /**
     * 装配页初始化响应。
     *
     * @param pharmacies         启用药房列表
     * @param categoryOptions    项目类型下拉
     * @param dispenserOptions   发药人下拉
     * @param recordTypeOptions  记录类型下拉
     * @param defaultFrom        默认发药日起
     * @param defaultTo          默认发药日止
     * @return 初始化响应
     */
    @Override
    public LoadInpatientPharmacyDispenseRecordPageInitResponse toLoadPageInitResponse(
        List<Location> pharmacies,
        List<OptionDto> categoryOptions,
        List<OptionDto> dispenserOptions,
        List<OptionDto> recordTypeOptions,
        String defaultFrom,
        String defaultTo) {
        LoadInpatientPharmacyDispenseRecordPageInitResponse response =
            new LoadInpatientPharmacyDispenseRecordPageInitResponse();
        response.setDefaultDispenseDateFrom(defaultFrom);
        response.setDefaultDispenseDateTo(defaultTo);
        response.setPharmacyOptions(this.toPharmacyOptions(pharmacies));
        response.setMedicationCategoryOptions(categoryOptions);
        response.setDispenserOptions(dispenserOptions);
        response.setRecordTypeOptions(recordTypeOptions);
        return response;
    }

    /**
     * 构建记录类型固定下拉。
     *
     * @return 全部/发药/退药选项
     */
    @Override
    public List<OptionDto> buildRecordTypeOptions() {
        return this.outpatientPharmacyDispenseRecordMapper.buildRecordTypeOptions();
    }

    /**
     * 药房实体列表 → OptionDto。
     *
     * @param pharmacies 药房列表
     * @return 下拉选项
     */
    @Override
    public List<OptionDto> toPharmacyOptions(List<Location> pharmacies) {
        return this.outpatientPharmacyDispenseRecordMapper.toPharmacyOptions(pharmacies);
    }

    /**
     * 装配分页响应。
     *
     * @param page 领域分页结果
     * @param rows 平铺行
     * @return 分页响应
     */
    @Override
    public PageInpatientPharmacyDispenseRecordsResponse toPageResponse(
        Page<MedicationDispenseSlipQueryModel> page,
        List<InpatientPharmacyDispenseRecordRowDto> rows) {
        return PagedResultBuilder.build(new PageInpatientPharmacyDispenseRecordsResponse(), rows, page);
    }

    /**
     * 列表行 → 导出行。
     *
     * @param row 列表行；{@code null} 时返回 {@code null}
     * @return 导出行 DTO
     */
    @Override
    public InpatientPharmacyDispenseRecordExportRowDto toExportRowDto(InpatientPharmacyDispenseRecordRowDto row) {
        if (row == null) {
            return null;
        }
        InpatientPharmacyDispenseRecordExportRowDto exportRow = new InpatientPharmacyDispenseRecordExportRowDto();
        exportRow.setRecordTypeText(row.getRecordTypeText());
        exportRow.setDispenseTime(row.getDispenseTime());
        exportRow.setPharmacyLocationName(row.getPharmacyLocationName());
        exportRow.setDispenserName(row.getDispenserName());
        exportRow.setPatientName(row.getPatientName());
        exportRow.setInpatientNo(row.getInpatientNo());
        exportRow.setWardName(row.getWardName());
        exportRow.setBedName(row.getBedName());
        exportRow.setPrescriptionNo(row.getPrescriptionNo());
        exportRow.setMedicationCode(row.getMedicationCode());
        exportRow.setMedicationName(row.getMedicationName());
        exportRow.setMedicationSpec(row.getMedicationSpec());
        exportRow.setQuantity(row.getQuantity());
        exportRow.setUnitText(row.getUnitText());
        exportRow.setUnitPrice(row.getUnitPrice());
        exportRow.setTotalAmount(row.getTotalAmount());
        exportRow.setLotNumber(row.getLotNumber());
        exportRow.setTraceNo(row.getTraceNo());
        exportRow.setOrderingDepartmentName(row.getOrderingDepartmentName());
        exportRow.setUsageText(row.getUsageText());
        exportRow.setSummaryNo(row.getSummaryNo());
        return exportRow;
    }

    /**
     * querysupport 行 → 应用列表行。
     *
     * @param source querysupport 行；{@code null} 时返回 {@code null}
     * @return 应用列表行 DTO
     */
    @Override
    public InpatientPharmacyDispenseRecordRowDto toRowDto(InpatientPharmacyDispenseRecordQueryRowDto source) {
        if (source == null) {
            return null;
        }
        InpatientPharmacyDispenseRecordRowDto row = new InpatientPharmacyDispenseRecordRowDto();
        row.setDispenseId(source.getDispenseId());
        row.setRecordTypeCode(source.getRecordTypeCode());
        row.setRecordTypeText(source.getRecordTypeText());
        row.setDispenseTime(source.getDispenseTime());
        row.setPharmacyLocationId(source.getPharmacyLocationId());
        row.setPharmacyLocationName(source.getPharmacyLocationName());
        row.setDispenserPractitionerId(source.getDispenserPractitionerId());
        row.setDispenserName(source.getDispenserName());
        row.setPatientId(source.getPatientId());
        row.setPatientName(source.getPatientName());
        row.setEncounterId(source.getEncounterId());
        row.setInpatientNo(source.getInpatientNo());
        row.setWardName(source.getWardName());
        row.setBedName(source.getBedName());
        row.setPrescriptionNo(source.getPrescriptionNo());
        row.setMedicationId(source.getMedicationId());
        row.setMedicationCode(source.getMedicationCode());
        row.setMedicationName(source.getMedicationName());
        row.setMedicationSpec(source.getMedicationSpec());
        row.setQuantity(source.getQuantity());
        row.setUnitCode(source.getUnitCode());
        row.setUnitText(source.getUnitText());
        row.setUnitPrice(source.getUnitPrice());
        row.setTotalAmount(source.getTotalAmount());
        row.setLotNumber(source.getLotNumber());
        row.setTraceNo(source.getTraceNo());
        row.setOrderingDepartmentId(source.getOrderingDepartmentId());
        row.setOrderingDepartmentName(source.getOrderingDepartmentName());
        row.setUsageText(source.getUsageText());
        row.setManufacturerText(source.getManufacturerText());
        row.setSummaryNo(source.getSummaryNo());
        row.setMedReqId(source.getMedReqId());
        return row;
    }

    /**
     * 装配导出响应。
     *
     * @param body       xlsx 字节
     * @param rowCount   导出行数
     * @param truncated  是否截断
     * @return 导出响应
     */
    @Override
    public ExportInpatientPharmacyDispenseRecordsResponse toExportResponse(
        byte[] body,
        int rowCount,
        boolean truncated) {
        ExportInpatientPharmacyDispenseRecordsResponse response =
            new ExportInpatientPharmacyDispenseRecordsResponse();
        response.setFileName(PharmacyDispenseRecordExportConstants.INPATIENT_EXPORT_DEFAULT_FILENAME);
        response.setContentType(PharmacyDispenseRecordExportConstants.XLSX_CONTENT_TYPE);
        response.setBody(body);
        response.setExportedRowCount(Integer.valueOf(rowCount));
        response.setTruncated(Boolean.valueOf(truncated));
        return response;
    }

    /**
     * 住院发药记录分页：解析展示日期、记录类型与库房/发药人筛选。
     *
     * @param query                    待填充 query
     * @param dispenseDateFrom         起始 yyyy-MM-dd
     * @param dispenseDateTo           截止 yyyy-MM-dd
     * @param recordTypeCode           记录类型码
     * @param pharmacyLocationId       药房 id
     * @param dispenserPractitionerId  发药人 id
     */
    private void applyFilterFields(
        MedicationDispenseRecordPageQuery query,
        String dispenseDateFrom,
        String dispenseDateTo,
        String recordTypeCode,
        Long pharmacyLocationId,
        Long dispenserPractitionerId) {
        LocalDate from = LocalDate.parse(dispenseDateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDate to = LocalDate.parse(dispenseDateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        query.setStartDate(from.atStartOfDay());
        query.setEndDate(to.atTime(LocalTime.of(23, 59, 59)));
        String normalizedRecordType = trimToNull(recordTypeCode);
        if (normalizedRecordType == null) {
            normalizedRecordType = PharmacyDispenseRecordConstants.RECORD_TYPE_ALL;
        }
        query.setRecordTypeCode(normalizedRecordType);
        query.setLocationId(pharmacyLocationId);
        query.setPractitionerId(dispenserPractitionerId);
    }
}
