/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.mapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageDetailRowDto;
import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageExportRowDto;
import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageSummaryRowDto;
import com.openhis.health.application.report.consumableusage.outpatient.response.ExportOutpatientConsumableUsageReportResponse;
import com.openhis.health.application.report.consumableusage.outpatient.response.LoadOutpatientConsumableUsageReportPageInitResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.share.constants.ConsumableUsageReportConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;

/**
 * 门诊耗材使用报表映射实现（AS-RPT-005）。
 */
@Component
public class OutpatientConsumableUsageReportMapperImpl implements OutpatientConsumableUsageReportMapper {

    /**
     * 装配页初始化响应。
     *
     * @param organizations           科室列表；调用方保证非 null
     * @param locations               库位列表；调用方保证非 null
     * @param summaryDimensionOptions 汇总维选项
     * @param sourceCodeOptions       来源选项
     * @param feeNatureOptions        费用性质（bus_no）
     * @param categoryOptions         耗材类型
     * @param practitionerOptions     执行人
     * @param recordTypeOptions       记录类型
     * @param defaultDateFrom         默认日起
     * @param defaultDateTo           默认日止
     * @return 初始化响应
     */
    @Override
    public LoadOutpatientConsumableUsageReportPageInitResponse toLoadPageInitResponse(
        List<Organization> organizations,
        List<Location> locations,
        List<OptionDto> summaryDimensionOptions,
        List<OptionDto> sourceCodeOptions,
        List<OptionDto> feeNatureOptions,
        List<OptionDto> categoryOptions,
        List<OptionDto> practitionerOptions,
        List<OptionDto> recordTypeOptions,
        String defaultDateFrom,
        String defaultDateTo) {
        LoadOutpatientConsumableUsageReportPageInitResponse response =
            new LoadOutpatientConsumableUsageReportPageInitResponse();
        response.setDefaultDateFrom(defaultDateFrom);
        response.setDefaultDateTo(defaultDateTo);
        response.setSummaryDimensionOptions(summaryDimensionOptions);
        response.setSourceCodeOptions(sourceCodeOptions);
        response.setOrganizationOptions(this.toOrganizationOptions(organizations));
        response.setLocationOptions(this.toLocationOptions(locations));
        response.setFeeNatureOptions(feeNatureOptions);
        response.setCategoryOptions(categoryOptions);
        response.setPractitionerOptions(practitionerOptions);
        response.setRecordTypeOptions(recordTypeOptions);
        return response;
    }

    /**
     * 构建记录类型下拉：ALL / DISPENSE / REFUND。
     *
     * @return 选项
     */
    @Override
    public List<OptionDto> buildRecordTypeOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(this.option(
            ConsumableUsageReportConstants.RECORD_TYPE_ALL,
            ConsumableUsageReportConstants.RECORD_TYPE_ALL_LABEL));
        options.add(this.option(
            ConsumableUsageReportConstants.RECORD_TYPE_DISPENSE,
            ConsumableUsageReportConstants.RECORD_TYPE_DISPENSE_LABEL));
        options.add(this.option(
            ConsumableUsageReportConstants.RECORD_TYPE_REFUND,
            ConsumableUsageReportConstants.RECORD_TYPE_REFUND_LABEL));
        return options;
    }

    /**
     * 构造 OptionDto。
     *
     * @param value 值
     * @param label 展示
     * @return 选项
     */
    private OptionDto option(String value, String label) {
        OptionDto option = new OptionDto();
        option.setValue(value);
        option.setLabel(label);
        return option;
    }

    /**
     * 明细行 → 导出行。
     *
     * @param row 明细行；null 时返回 null
     * @return 导出行
     */
    @Override
    public OutpatientConsumableUsageExportRowDto toExportRowFromDetail(OutpatientConsumableUsageDetailRowDto row) {
        if (row == null) {
            return null;
        }
        OutpatientConsumableUsageExportRowDto exportRow = new OutpatientConsumableUsageExportRowDto();
        exportRow.setSourceText(row.getSourceCode_dictText());
        if (row.getBusinessTime() != null) {
            exportRow.setBusinessTimeText(row.getBusinessTime().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        exportRow.setPatientName(row.getPatientName());
        exportRow.setEncounterBusNo(row.getEncounterBusNo());
        exportRow.setFeeNatureDisplay(row.getFeeNatureDisplay());
        exportRow.setMedTypeDisplay(row.getMedTypeDisplay());
        exportRow.setOrganizationName(row.getOrganizationName());
        exportRow.setLocationName(row.getLocationName());
        exportRow.setDeviceName(row.getDeviceName());
        exportRow.setSpecText(row.getSpecText());
        exportRow.setQuantity(row.getQuantity());
        exportRow.setUnitText(row.getUnitCode_dictText());
        exportRow.setAmount(row.getAmount());
        exportRow.setPurchaseUnitPrice(row.getPurchaseUnitPrice());
        exportRow.setPurchaseAmount(row.getPurchaseAmount());
        exportRow.setSaleUnitPrice(row.getSaleUnitPrice());
        exportRow.setSaleAmount(row.getSaleAmount());
        exportRow.setLotNumber(row.getLotNumber());
        exportRow.setStatusOrTypeText(row.getStatusOrTypeEnum_enumText());
        exportRow.setPerformerName(row.getPerformerName());
        return exportRow;
    }

    /**
     * 汇总行 → 导出行。
     *
     * @param row 汇总行；null 时返回 null
     * @return 导出行
     */
    @Override
    public OutpatientConsumableUsageExportRowDto toExportRowFromSummary(OutpatientConsumableUsageSummaryRowDto row) {
        if (row == null) {
            return null;
        }
        OutpatientConsumableUsageExportRowDto exportRow = new OutpatientConsumableUsageExportRowDto();
        exportRow.setPatientName(row.getPatientName());
        exportRow.setOrganizationName(row.getOrganizationName());
        exportRow.setLocationName(row.getLocationName());
        exportRow.setDeviceName(row.getDeviceName());
        // CATEGORY 等无分列主键时用统一维度名回填品名列，避免汇总导出空维度
        if (StringUtils.isEmpty(exportRow.getDeviceName()) && StringUtils.isNotEmpty(row.getDimensionName())) {
            exportRow.setDeviceName(row.getDimensionName());
        }
        exportRow.setQuantity(row.getNetQuantity());
        exportRow.setAmount(row.getSaleNetAmount());
        exportRow.setPurchaseAmount(row.getPurchaseNetAmount());
        exportRow.setSaleAmount(row.getSaleNetAmount());
        return exportRow;
    }

    /**
     * 装配导出响应。
     *
     * @param body             xlsx 字节
     * @param exportedRowCount 行数
     * @param truncated        是否截断
     * @return 导出响应
     */
    @Override
    public ExportOutpatientConsumableUsageReportResponse toExportResponse(
        byte[] body,
        int exportedRowCount,
        boolean truncated) {
        ExportOutpatientConsumableUsageReportResponse response = new ExportOutpatientConsumableUsageReportResponse();
        response.setFileName(ConsumableUsageReportConstants.OUTPATIENT_EXPORT_DEFAULT_FILENAME);
        response.setContentType(ConsumableUsageReportConstants.XLSX_CONTENT_TYPE);
        response.setBody(body);
        response.setExportedRowCount(exportedRowCount);
        response.setTruncated(truncated);
        return response;
    }

    /**
     * 组织 → OptionDto。
     *
     * @param organizations 组织列表
     * @return 下拉选项
     */
    private List<OptionDto> toOrganizationOptions(List<Organization> organizations) {
        List<OptionDto> options = new ArrayList<>();
        if (organizations == null) {
            return options;
        }
        for (Organization organization : organizations) {
            // Asm：跳过无主键组织，避免 Option value 空串
            if (organization == null || organization.getId() == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(organization.getId()));
            option.setLabel(organization.getName());
            options.add(option);
        }
        return options;
    }

    /**
     * 库位 → OptionDto。
     *
     * @param locations 库位列表
     * @return 下拉选项
     */
    private List<OptionDto> toLocationOptions(List<Location> locations) {
        List<OptionDto> options = new ArrayList<>();
        if (locations == null) {
            return options;
        }
        for (Location location : locations) {
            // Asm：跳过无主键库位
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
}
