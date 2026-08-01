/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.mapper;

import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageDetailLineDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsagePatientRowDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsagePresentationOptionDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageStatusOptionDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageSummaryLineDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageTotalsDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.request.PageInpatientPatientMedicationUsagePatientsRequest;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.request.QueryInpatientPatientMedicationUsageRequest;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response.LoadInpatientPatientMedicationUsagePageInitResponse;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response.PageInpatientPatientMedicationUsagePatientsResponse;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response.QueryInpatientPatientMedicationUsageResponse;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPatientMedicationUsageDispenseQuery;
import com.openhis.health.domain.share.constants.InpatientPharmacyPatientMedicationUsageConstants;
import com.openhis.health.domain.share.enums.EncounterStatus;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link InpatientPatientMedicationUsageMapper} 实现：字段映射与初始化选项装配。
 */
@Component
public class InpatientPatientMedicationUsageMapperImpl implements InpatientPatientMedicationUsageMapper {

    /**
     * 装配页初始化响应：默认当日日期 + 展示模式/发药状态选项。
     *
     * @param today 服务端当日；调用方保证非 null
     * @return 初始化响应
     */
    @Override
    public LoadInpatientPatientMedicationUsagePageInitResponse buildPageInitResponse(LocalDate today) {
        LoadInpatientPatientMedicationUsagePageInitResponse response =
            new LoadInpatientPatientMedicationUsagePageInitResponse();
        // 默认查询日均为服务端当日（闭区间）
        response.setDateFrom(today);
        response.setDateTo(today);
        // 展示模式：明细 / 汇总
        List<InpatientPatientMedicationUsagePresentationOptionDto> presentationOptions = new ArrayList<>();
        InpatientPatientMedicationUsagePresentationOptionDto detailOption =
            new InpatientPatientMedicationUsagePresentationOptionDto();
        detailOption.setCode(InpatientPharmacyPatientMedicationUsageConstants.PRESENTATION_MODE_DETAIL);
        detailOption.setDisplay(InpatientPharmacyPatientMedicationUsageConstants.PRESENTATION_MODE_DETAIL_DISPLAY);
        presentationOptions.add(detailOption);
        InpatientPatientMedicationUsagePresentationOptionDto summaryOption =
            new InpatientPatientMedicationUsagePresentationOptionDto();
        summaryOption.setCode(InpatientPharmacyPatientMedicationUsageConstants.PRESENTATION_MODE_SUMMARY);
        summaryOption.setDisplay(InpatientPharmacyPatientMedicationUsageConstants.PRESENTATION_MODE_SUMMARY_DISPLAY);
        presentationOptions.add(summaryOption);
        response.setPresentationOptions(presentationOptions);
        // 展示发药状态三态说明（仅明细使用）
        List<InpatientPatientMedicationUsageStatusOptionDto> usageStatusOptions = new ArrayList<>();
        InpatientPatientMedicationUsageStatusOptionDto pending =
            new InpatientPatientMedicationUsageStatusOptionDto();
        pending.setCode(InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_PENDING);
        pending.setDisplay(InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_PENDING_DISPLAY);
        usageStatusOptions.add(pending);
        InpatientPatientMedicationUsageStatusOptionDto dispensed =
            new InpatientPatientMedicationUsageStatusOptionDto();
        dispensed.setCode(InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_DISPENSED);
        dispensed.setDisplay(InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_DISPENSED_DISPLAY);
        usageStatusOptions.add(dispensed);
        InpatientPatientMedicationUsageStatusOptionDto returned =
            new InpatientPatientMedicationUsageStatusOptionDto();
        returned.setCode(InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_RETURNED);
        returned.setDisplay(InpatientPharmacyPatientMedicationUsageConstants.USAGE_STATUS_RETURNED_DISPLAY);
        usageStatusOptions.add(returned);
        response.setUsageStatusOptions(usageStatusOptions);
        return response;
    }

    /**
     * 患者分页请求 → 住院就诊上下文分页查询（默认在院态）。
     *
     * @param request 分页请求；调用方保证非 null
     * @return 领域分页入参
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(
        PageInpatientPatientMedicationUsagePatientsRequest request) {
        // 空请求不构造查询，交由上层 Gate
        if (request == null) {
            return null;
        }
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        // 分页窗口 + keyword 由 PageQueryBinder 搬运
        PageQueryBinder.bind(request, query);
        // BR-IP-PMU-010：默认在院 IN_PROGRESS ∪ ON_HOLD；不按库房收窄
        query.setStatusEnumIn(List.of(
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode()));
        // 患者检索无需诊断快照，避免多余 hydrate
        query.setIncludeDiagnosisSnapshot(Boolean.FALSE);
        return query;
    }

    /**
     * 住院上下文分页 → 患者候选分页响应。
     *
     * @param page 住院就诊上下文分页；调用方保证非 null
     * @return 分页响应（经 {@code PagedResultBuilder}）
     */
    @Override
    public PageInpatientPatientMedicationUsagePatientsResponse toPatientPageResponse(
        Page<InpatientEncounterContextQueryModel> page) {
        List<InpatientPatientMedicationUsagePatientRowDto> rows = new ArrayList<>();
        // 有分页壳时逐行搬运候选字段（最小 PII）
        if (page != null) {
            for (InpatientEncounterContextQueryModel model : page.getContent()) {
                // 跳过空模型
                if (model == null) {
                    continue;
                }
                InpatientPatientMedicationUsagePatientRowDto row = new InpatientPatientMedicationUsagePatientRowDto();
                row.setEncounterId(model.getEncounterId());
                row.setPatientId(model.getPatientId());
                row.setPatientName(model.getPatientName());
                // 住院号取自就诊业务号
                row.setInpatientNo(model.getEncounterBusNo());
                row.setWardName(model.getWardName());
                row.setBedName(model.getBedName());
                row.setStatusEnum(model.getStatusEnum());
                rows.add(row);
            }
        }
        // B.10.11：页码换算仅在 PagedResultBuilder 内完成
        return PagedResultBuilder.build(new PageInpatientPatientMedicationUsagePatientsResponse(), rows, page);
    }

    /**
     * 使用查询请求 → 发放使用行领域查询（时间窗；状态/类型留空由 QS 套默认白名单）。
     *
     * @param request 查询请求；调用方保证非 null
     * @return 领域查询入参
     */
    @Override
    public InpatientPatientMedicationUsageDispenseQuery toUsageDispenseQuery(
        QueryInpatientPatientMedicationUsageRequest request) {
        // 空请求不构造 Query
        if (request == null) {
            return null;
        }
        InpatientPatientMedicationUsageDispenseQuery query = new InpatientPatientMedicationUsageDispenseQuery();
        query.setEncounterId(request.getEncounterId());
        // patientId 下推防串患（应用层已交叉校验）
        query.setPatientId(request.getPatientId());
        query.setPlannedDispenseDateFrom(request.getDateFrom());
        query.setPlannedDispenseDateTo(request.getDateTo());
        // statusEnumIn / dispenseEnumIn 留空：由 MedicationDispenseQueryService 套 AS §1.3 默认白名单
        return query;
    }

    /**
     * 组装使用查询响应。
     *
     * @param presentationMode 展示模式回显
     * @param detailLines      明细行（SUMMARY 时传空列表）
     * @param summaryLines     汇总行（DETAIL 时传空列表）
     * @param totals           合计区
     * @return 查询响应
     */
    @Override
    public QueryInpatientPatientMedicationUsageResponse toQueryResponse(
        String presentationMode,
        List<InpatientPatientMedicationUsageDetailLineDto> detailLines,
        List<InpatientPatientMedicationUsageSummaryLineDto> summaryLines,
        InpatientPatientMedicationUsageTotalsDto totals) {
        QueryInpatientPatientMedicationUsageResponse response = new QueryInpatientPatientMedicationUsageResponse();
        response.setPresentationMode(presentationMode);
        // null 明细统一为空列表，避免前端 NPE
        if (detailLines == null) {
            response.setDetailLines(List.of());
        } else {
            response.setDetailLines(detailLines);
        }
        // null 汇总统一为空列表
        if (summaryLines == null) {
            response.setSummaryLines(List.of());
        } else {
            response.setSummaryLines(summaryLines);
        }
        response.setTotals(totals);
        return response;
    }
}
