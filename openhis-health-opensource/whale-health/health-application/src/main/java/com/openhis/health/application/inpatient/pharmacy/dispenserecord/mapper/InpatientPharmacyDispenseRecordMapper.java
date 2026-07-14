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
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto.InpatientPharmacyDispenseRecordQueryRowDto;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 住院发退药记录 Request/Response 与领域 Query 映射（AS-IP-PHA-004）。
 *
 * <p>禁止承载业务流程判断；querysupport Row → 应用 DTO 在边界完成。</p>
 */
public interface InpatientPharmacyDispenseRecordMapper {

    /**
     * 分页请求 → 发放域记录查询入参。
     *
     * @param request 分页查询请求；调用方保证非空
     * @return 领域查询入参
     */
    MedicationDispenseRecordPageQuery toMedicationDispenseRecordPageQuery(
        PageInpatientPharmacyDispenseRecordsRequest request);

    /**
     * 导出请求 → 发放域记录查询入参（含导出行上限页大小）。
     *
     * @param request 导出请求；调用方保证非空
     * @return 领域查询入参
     */
    MedicationDispenseRecordPageQuery toMedicationDispenseRecordPageQuery(
        ExportInpatientPharmacyDispenseRecordsRequest request);

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
    LoadInpatientPharmacyDispenseRecordPageInitResponse toLoadPageInitResponse(
        List<Location> pharmacies,
        List<OptionDto> categoryOptions,
        List<OptionDto> dispenserOptions,
        List<OptionDto> recordTypeOptions,
        String defaultFrom,
        String defaultTo);

    /**
     * 构建记录类型固定下拉。
     *
     * @return 全部/发药/退药选项
     */
    List<OptionDto> buildRecordTypeOptions();

    /**
     * 药房实体列表 → OptionDto。
     *
     * @param pharmacies 药房列表
     * @return 下拉选项
     */
    List<OptionDto> toPharmacyOptions(List<Location> pharmacies);

    /**
     * 装配分页响应。
     *
     * @param page 领域分页结果
     * @param rows 平铺行
     * @return 分页响应
     */
    PageInpatientPharmacyDispenseRecordsResponse toPageResponse(
        Page<MedicationDispenseSlipQueryModel> page,
        List<InpatientPharmacyDispenseRecordRowDto> rows);

    /**
     * 列表行 → 导出行。
     *
     * @param row 列表行；{@code null} 时返回 {@code null}
     * @return 导出行 DTO
     */
    InpatientPharmacyDispenseRecordExportRowDto toExportRowDto(InpatientPharmacyDispenseRecordRowDto row);

    /**
     * querysupport 行 → 应用列表行。
     *
     * @param source querysupport 行；{@code null} 时返回 {@code null}
     * @return 应用列表行 DTO
     */
    InpatientPharmacyDispenseRecordRowDto toRowDto(InpatientPharmacyDispenseRecordQueryRowDto source);

    /**
     * 装配导出响应。
     *
     * @param body       xlsx 字节
     * @param rowCount   导出行数
     * @param truncated  是否截断
     * @return 导出响应
     */
    ExportInpatientPharmacyDispenseRecordsResponse toExportResponse(
        byte[] body,
        int rowCount,
        boolean truncated);
}
