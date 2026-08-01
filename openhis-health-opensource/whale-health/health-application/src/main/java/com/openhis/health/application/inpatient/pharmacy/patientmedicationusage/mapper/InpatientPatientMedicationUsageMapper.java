/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.mapper;

import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageDetailLineDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageSummaryLineDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageTotalsDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.request.PageInpatientPatientMedicationUsagePatientsRequest;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.request.QueryInpatientPatientMedicationUsageRequest;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response.LoadInpatientPatientMedicationUsagePageInitResponse;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response.PageInpatientPatientMedicationUsagePatientsResponse;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response.QueryInpatientPatientMedicationUsageResponse;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPatientMedicationUsageDispenseQuery;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

/**
 * 住院患者药品使用 Request/Response 映射（AS-IP-PHA-005）。
 *
 * <p>仅做字段搬运与初始化选项装配，禁止承载业务流程判断。</p>
 */
public interface InpatientPatientMedicationUsageMapper {

    /**
     * 装配页初始化响应：默认当日日期 + 展示模式/发药状态选项。
     *
     * @param today 服务端当日；调用方保证非 null
     * @return 初始化响应
     */
    LoadInpatientPatientMedicationUsagePageInitResponse buildPageInitResponse(LocalDate today);

    /**
     * 患者分页请求 → 住院就诊上下文分页查询（默认在院态）。
     *
     * @param request 分页请求；调用方保证非 null
     * @return 领域分页入参
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(
        PageInpatientPatientMedicationUsagePatientsRequest request);

    /**
     * 住院上下文分页 → 患者候选分页响应。
     *
     * @param page 住院就诊上下文分页；调用方保证非 null
     * @return 分页响应（经 {@code PagedResultBuilder}）
     */
    PageInpatientPatientMedicationUsagePatientsResponse toPatientPageResponse(
        Page<InpatientEncounterContextQueryModel> page);

    /**
     * 使用查询请求 → 发放使用行领域查询（时间窗；状态/类型留空由 QS 套默认白名单）。
     *
     * @param request 查询请求；调用方保证非 null
     * @return 领域查询入参
     */
    InpatientPatientMedicationUsageDispenseQuery toUsageDispenseQuery(
        QueryInpatientPatientMedicationUsageRequest request);

    /**
     * 组装使用查询响应。
     *
     * @param presentationMode 展示模式回显
     * @param detailLines      明细行（SUMMARY 时传空列表）
     * @param summaryLines     汇总行（DETAIL 时传空列表）
     * @param totals           合计区
     * @return 查询响应
     */
    QueryInpatientPatientMedicationUsageResponse toQueryResponse(
        String presentationMode,
        List<InpatientPatientMedicationUsageDetailLineDto> detailLines,
        List<InpatientPatientMedicationUsageSummaryLineDto> summaryLines,
        InpatientPatientMedicationUsageTotalsDto totals);
}
