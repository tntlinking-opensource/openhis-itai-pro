/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.outpatient.mapper;

import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseDetailRowDto;
import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseExportRowDto;
import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseSummaryRowDto;
import com.openhis.health.application.report.medicationdispense.outpatient.response.ExportOutpatientMedicationDispenseReportResponse;
import com.openhis.health.application.report.medicationdispense.outpatient.response.LoadOutpatientMedicationDispenseReportPageInitResponse;
import com.openhis.health.application.report.medicationdispense.outpatient.response.PageOutpatientMedicationDispenseReportDetailResponse;
import com.openhis.health.application.report.medicationdispense.outpatient.response.SummarizeOutpatientMedicationDispenseReportResponse;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.share.constants.MedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.OutpatientMedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordExportConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link OutpatientMedicationDispenseReportMapper} 实现。
 */
@Component
public class OutpatientMedicationDispenseReportMapperImpl implements OutpatientMedicationDispenseReportMapper {

    /**
     * 装配页初始化响应。
     *
     * @param pharmacies               启用药房
     * @param categoryOptions          药品类型
     * @param practitionerOptions      发药人
     * @param recordTypeOptions        记录类型
     * @param summaryDimensionOptions  汇总维度
     * @param sourceOptions            来源
     * @param feeNatureOptions         费用性质（bus_no）
     * @param defaultFrom              默认日起
     * @param defaultTo                默认日止
     * @return 初始化响应
     */
    @Override
    public LoadOutpatientMedicationDispenseReportPageInitResponse toLoadPageInitResponse(
        List<Location> pharmacies,
        List<OptionDto> categoryOptions,
        List<OptionDto> practitionerOptions,
        List<OptionDto> recordTypeOptions,
        List<OptionDto> summaryDimensionOptions,
        List<OptionDto> sourceOptions,
        List<OptionDto> feeNatureOptions,
        String defaultFrom,
        String defaultTo) {
        LoadOutpatientMedicationDispenseReportPageInitResponse response =
            new LoadOutpatientMedicationDispenseReportPageInitResponse();
        response.setDefaultDateFrom(defaultFrom);
        response.setDefaultDateTo(defaultTo);
        response.setPharmacyOptions(this.toPharmacyOptions(pharmacies));
        response.setCategoryOptions(categoryOptions);
        response.setPractitionerOptions(practitionerOptions);
        response.setRecordTypeOptions(recordTypeOptions);
        response.setSummaryDimensionOptions(summaryDimensionOptions);
        response.setSourceOptions(sourceOptions);
        response.setFeeNatureOptions(feeNatureOptions);
        return response;
    }

    /**
     * 构建记录类型下拉。
     *
     * @return 选项
     */
    @Override
    public List<OptionDto> buildRecordTypeOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.option(
            PharmacyDispenseRecordConstants.RECORD_TYPE_ALL,
            PharmacyDispenseRecordConstants.RECORD_TYPE_ALL_LABEL));
        options.add(this.option(
            PharmacyDispenseRecordConstants.RECORD_TYPE_DISPENSE,
            PharmacyDispenseRecordConstants.RECORD_TYPE_DISPENSE_LABEL));
        options.add(this.option(
            PharmacyDispenseRecordConstants.RECORD_TYPE_REFUND,
            PharmacyDispenseRecordConstants.RECORD_TYPE_REFUND_LABEL));
        return options;
    }

    /**
     * 构建汇总维度下拉。
     *
     * @return 选项
     */
    @Override
    public List<OptionDto> buildSummaryDimensionOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.option(
            MedicationDispenseReportConstants.SUMMARY_DIMENSION_PHARMACY,
            MedicationDispenseReportConstants.SUMMARY_DIMENSION_PHARMACY_LABEL));
        options.add(this.option(
            MedicationDispenseReportConstants.SUMMARY_DIMENSION_ITEM,
            MedicationDispenseReportConstants.SUMMARY_DIMENSION_ITEM_LABEL));
        options.add(this.option(
            MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY,
            MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY_LABEL));
        options.add(this.option(
            MedicationDispenseReportConstants.SUMMARY_DIMENSION_DEPARTMENT,
            MedicationDispenseReportConstants.SUMMARY_DIMENSION_DEPARTMENT_LABEL));
        return options;
    }

    /**
     * 构建来源下拉。
     *
     * @return 选项
     */
    @Override
    public List<OptionDto> buildSourceOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.option(
            MedicationDispenseReportConstants.SOURCE_ALL,
            MedicationDispenseReportConstants.SOURCE_ALL_LABEL));
        options.add(this.option(
            MedicationDispenseReportConstants.SOURCE_PATIENT_DISPENSE,
            MedicationDispenseReportConstants.SOURCE_PATIENT_DISPENSE_LABEL));
        options.add(this.option(
            MedicationDispenseReportConstants.SOURCE_DEPT_ISSUE,
            MedicationDispenseReportConstants.SOURCE_DEPT_ISSUE_LABEL));
        return options;
    }

    /**
     * 药房列表 → OptionDto。
     *
     * @param pharmacies 药房
     * @return 下拉
     */
    @Override
    public List<OptionDto> toPharmacyOptions(List<Location> pharmacies) {
        List<OptionDto> options = new ArrayList<>();
        if (pharmacies == null) {
            return options;
        }
        for (Location pharmacy : pharmacies) {
            if (pharmacy == null || pharmacy.getId() == null) {
                continue;
            }
            options.add(this.option(String.valueOf(pharmacy.getId()), pharmacy.getName()));
        }
        return options;
    }

    /**
     * 装配汇总响应。
     *
     * @param rows          汇总行
     * @param totalQuantity 净数量合计
     * @param totalAmount   净金额合计
     * @return 汇总响应
     */
    @Override
    public SummarizeOutpatientMedicationDispenseReportResponse toSummarizeResponse(
        List<OutpatientMedicationDispenseSummaryRowDto> rows,
        BigDecimal totalQuantity,
        BigDecimal purchaseTotalAmount,
        BigDecimal saleTotalAmount) {
        SummarizeOutpatientMedicationDispenseReportResponse response =
            new SummarizeOutpatientMedicationDispenseReportResponse();
        response.setRows(rows);
        response.setTotalQuantity(totalQuantity);
        response.setPurchaseTotalAmount(purchaseTotalAmount);
        response.setSaleTotalAmount(saleTotalAmount);
        response.setTotalAmount(saleTotalAmount);
        return response;
    }

    /**
     * 装配明细分页响应。
     *
     * @param rows      当前页行
     * @param totalCount 总条数
     * @param pageNo    页码
     * @param pageSize  页大小
     * @return 分页响应
     */
    @Override
    public PageOutpatientMedicationDispenseReportDetailResponse toPageDetailResponse(
        List<OutpatientMedicationDispenseDetailRowDto> rows,
        long totalCount,
        int pageNo,
        int pageSize) {
        return PagedResultBuilder.build(
            new PageOutpatientMedicationDispenseReportDetailResponse(),
            rows,
            totalCount,
            pageNo,
            pageSize);
    }

    /**
     * 明细行 → 导出行。
     *
     * @param row 明细；null 返回 null
     * @return 导出行
     */
    @Override
    public OutpatientMedicationDispenseExportRowDto toExportRowFromDetail(
        OutpatientMedicationDispenseDetailRowDto row) {
        if (row == null) {
            return null;
        }
        OutpatientMedicationDispenseExportRowDto exportRow = new OutpatientMedicationDispenseExportRowDto();
        exportRow.setSourceText(row.getSourceText());
        exportRow.setRecordTypeText(row.getRecordTypeText());
        exportRow.setBusinessTimeOrDimension(row.getBusinessTime());
        exportRow.setPharmacyLocationName(row.getPharmacyLocationName());
        if (row.getPatientName() != null) {
            exportRow.setPatientOrDepartmentName(row.getPatientName());
        } else {
            exportRow.setPatientOrDepartmentName(row.getDepartmentName());
        }
        exportRow.setEncounterNo(row.getEncounterBusNo());
        exportRow.setFeeNatureDisplay(row.getFeeNatureDisplay());
        exportRow.setMedTypeDisplay(row.getMedTypeDisplay());
        exportRow.setMedicationCode(row.getMedicationCode());
        exportRow.setMedicationName(row.getMedicationName());
        exportRow.setMedicationSpec(row.getMedicationSpec());
        exportRow.setQuantity(row.getQuantity());
        exportRow.setUnitText(row.getUnitText());
        exportRow.setUnitPrice(row.getUnitPrice());
        exportRow.setTotalAmount(row.getTotalAmount());
        exportRow.setPurchaseUnitPrice(row.getPurchaseUnitPrice());
        exportRow.setPurchaseAmount(row.getPurchaseAmount());
        exportRow.setSaleUnitPrice(row.getSaleUnitPrice());
        exportRow.setSaleAmount(row.getSaleAmount());
        exportRow.setLotNumber(row.getLotNumber());
        exportRow.setBusNo(row.getBusNo());
        return exportRow;
    }

    /**
     * 汇总行 → 导出行。
     *
     * @param row 汇总；null 返回 null
     * @return 导出行
     */
    @Override
    public OutpatientMedicationDispenseExportRowDto toExportRowFromSummary(
        OutpatientMedicationDispenseSummaryRowDto row) {
        if (row == null) {
            return null;
        }
        OutpatientMedicationDispenseExportRowDto exportRow = new OutpatientMedicationDispenseExportRowDto();
        exportRow.setBusinessTimeOrDimension(row.getDimensionName());
        exportRow.setQuantity(row.getNetQuantity());
        exportRow.setTotalAmount(row.getSaleNetAmount());
        exportRow.setPurchaseAmount(row.getPurchaseNetAmount());
        exportRow.setSaleAmount(row.getSaleNetAmount());
        exportRow.setMedicationName(row.getDimensionName());
        return exportRow;
    }

    /**
     * 装配导出响应。
     *
     * @param body      xlsx
     * @param rowCount  行数
     * @param truncated 是否截断
     * @return 导出响应
     */
    @Override
    public ExportOutpatientMedicationDispenseReportResponse toExportResponse(
        byte[] body,
        int rowCount,
        boolean truncated) {
        ExportOutpatientMedicationDispenseReportResponse response =
            new ExportOutpatientMedicationDispenseReportResponse();
        response.setFileName(OutpatientMedicationDispenseReportConstants.EXPORT_DEFAULT_FILENAME);
        response.setContentType(PharmacyDispenseRecordExportConstants.XLSX_CONTENT_TYPE);
        response.setBody(body);
        response.setExportedRowCount(Integer.valueOf(rowCount));
        response.setTruncated(Boolean.valueOf(truncated));
        return response;
    }

    /**
     * 构造下拉选项。
     *
     * @param value 值
     * @param label 文案
     * @return OptionDto
     */
    private OptionDto option(String value, String label) {
        OptionDto option = new OptionDto();
        option.setValue(value);
        option.setLabel(label);
        return option;
    }
}
