/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.registrationdetail.support;

import com.openhis.health.domain.administration.encounter.queries.OutpatientRegistrationReportPageQuery;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.share.constants.OutpatientRegistrationReportConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 门诊挂号明细报表 scope 预解析（AS-RPT-007；医生/关键字 → {@code resolvedEncounterIds}）。
 *
 * <p>跨聚合只读编排；不写库。医生筛复用
 * {@link EncounterQueryService#filterEncounterIdsByActiveAdmitterDoctor}
 * （活动接诊医生 ADMITTER；B.6.4）。
 * 无关键字的接诊医生候选须走
 * {@link EncounterQueryService#listEncounterIdsForRegistrationBusinessWindow}
 * （coalesce 挂号业务日窗，与明细/汇总规约一致；可叠 {@code statusEnum}）。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientRegistrationDetailReportQuerySupport {

    /** 就诊读侧（AMB scope / 医生筛 / 关键字 busNo）。 */
    private final EncounterQueryService encounterQueryService;

    /** 患者关键字 → 患者主键。 */
    private final PatientQueryService patientQueryService;

    /** 门诊挂号科室全量（未选科室时医生候选 scope）。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 将日期窗与科室写入 Query，并按关键字/接诊医生预写 {@code resolvedEncounterIds}。
     *
     * @param query          领域筛选（非空）
     * @param dateFrom       业务日起
     * @param dateTo         业务日止
     * @param organizationId 挂号科室；可空
     * @param practitionerId 接诊医生（活动 ADMITTER）；可空
     * @param keyword        患者/门诊号关键字；可空
     */
    public void prepareQueryScopes(
        OutpatientRegistrationReportPageQuery query,
        LocalDate dateFrom,
        LocalDate dateTo,
        Long organizationId,
        Long practitionerId,
        String keyword) {
        // Pre：业务日闭区间 → 时刻窗（与挂号列表 coalesce 时间轴对齐）
        LocalDateTime businessTimeFrom = dateFrom.atStartOfDay();
        LocalDateTime businessTimeTo = dateTo.atTime(LocalTime.MAX);
        query.setBusinessTimeFrom(businessTimeFrom);
        query.setBusinessTimeTo(businessTimeTo);
        query.setOrganizationId(organizationId);

        LinkedHashSet<Long> resolvedEncounterIds = null;
        String trimmedKeyword = StringUtils.trimToNull(keyword);
        // Pre：关键字 → 门诊号 ∪ 患者命中就诊（空命中写空列表拒查，防全表扫）
        if (trimmedKeyword != null) {
            resolvedEncounterIds = this.resolveEncounterIdsByKeyword(trimmedKeyword);
        }
        // Pre：接诊医生筛；候选为关键字命中或科室 AMB scope，再滤活动 ADMITTER（B.6.4）
        if (practitionerId != null) {
            List<Long> candidates;
            // 已有关键字收窄：在命中集合上再滤接诊医生
            if (resolvedEncounterIds != null) {
                candidates = new ArrayList<>(resolvedEncounterIds);
            } else {
                // 未选关键字：以科室（或全院门诊科）AMB 就诊为候选，叠 coalesce 业务日窗 + 可选状态
                candidates = this.listAmbEncounterCandidates(
                    organizationId,
                    businessTimeFrom,
                    businessTimeTo,
                    query.getStatusEnum());
            }
            Set<Long> doctorMatched = this.encounterQueryService.filterEncounterIdsByActiveAdmitterDoctor(
                candidates, practitionerId);
            resolvedEncounterIds = new LinkedHashSet<>(doctorMatched);
        }
        // 有关键字或医生筛时写入 resolvedEncounterIds（含空列表＝拒查）
        if (resolvedEncounterIds != null) {
            query.setResolvedEncounterIds(new ArrayList<>(resolvedEncounterIds));
        }
    }

    /**
     * 将应用层 {@code summaryView}（DEPARTMENT/DOCTOR）映射为领域 {@code summaryDimension}。
     *
     * @param summaryView 请求视角；空则按科室
     * @return 领域汇总维码
     */
    public String resolveSummaryDimension(String summaryView) {
        String trimmed = StringUtils.trimToNull(summaryView);
        // 未传视角：默认按科室 × 号源
        if (trimmed == null) {
            return OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_ORGANIZATION;
        }
        // DEPARTMENT 与 ORGANIZATION 同义（SDD 用 DEPARTMENT，领域存 ORGANIZATION）
        if (OutpatientRegistrationReportConstants.SUMMARY_VIEW_DEPARTMENT.equals(trimmed)
            || OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_ORGANIZATION.equals(trimmed)) {
            return OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_ORGANIZATION;
        }
        // 按接诊医生 × 号源
        if (OutpatientRegistrationReportConstants.SUMMARY_VIEW_DOCTOR.equals(trimmed)) {
            return OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_DOCTOR;
        }
        // 未识别码原样回传，由 AppService assert 拒绝
        return trimmed;
    }

    /**
     * 关键字解析就诊主键：门诊号模糊 ∪ 患者关键字命中就诊。
     *
     * @param keyword 非空关键字
     * @return 命中主键集合；无命中为空集合（非 null）
     */
    private LinkedHashSet<Long> resolveEncounterIdsByKeyword(String keyword) {
        LinkedHashSet<Long> hitIds = new LinkedHashSet<>();
        List<Long> byBusNo = this.encounterQueryService.listEncounterIdsByBusNoKeyword(keyword);
        // 门诊号模糊命中并入集合
        if (byBusNo != null) {
            for (Long encounterId : byBusNo) {
                if (encounterId != null) {
                    hitIds.add(encounterId);
                }
            }
        }
        List<Long> patientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(keyword);
        // 患者关键字命中后再反查就诊主键并并入
        if (patientIds != null && !patientIds.isEmpty()) {
            List<Long> byPatient = this.encounterQueryService.listEncounterIdsByPatientIds(patientIds);
            if (byPatient != null) {
                for (Long encounterId : byPatient) {
                    if (encounterId != null) {
                        hitIds.add(encounterId);
                    }
                }
            }
        }
        return hitIds;
    }

    /**
     * 医生筛候选：指定科室或门诊挂号科室全量下的 AMB 就诊主键
     * （叠 coalesce 挂号业务日窗 + 可选状态，避免无界 IN / 全状态候选）。
     *
     * @param organizationId     科室；空则全量门诊挂号科
     * @param businessTimeFrom   业务窗起（含）
     * @param businessTimeTo     业务窗止（含）
     * @param statusEnum         就诊状态 code；空不按状态收窄
     * @return 候选就诊主键
     */
    private List<Long> listAmbEncounterCandidates(
        Long organizationId,
        LocalDateTime businessTimeFrom,
        LocalDateTime businessTimeTo,
        String statusEnum) {
        List<Long> organizationIds = new ArrayList<>();
        // 已选科室：单科候选
        if (organizationId != null) {
            organizationIds.add(organizationId);
        } else {
            // 未选科室：门诊挂号科全量（与 IF-01 下拉同源）
            List<Organization> organizations =
                this.organizationQueryService.listOrganizationsForOutpatientRegistration();
            if (organizations != null) {
                for (Organization organization : organizations) {
                    if (organization != null && organization.getId() != null) {
                        organizationIds.add(organization.getId());
                    }
                }
            }
        }
        // 无科室候选时返回空列表，后续医生筛结果亦为空（拒查）
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        // 须与明细/汇总同 coalesce 时间轴；禁止工作量「相交日窗」口径误伤挂号业务日
        return this.encounterQueryService.listEncounterIdsForRegistrationBusinessWindow(
            EncounterClass.AMB.getCode(),
            organizationIds,
            businessTimeFrom,
            businessTimeTo,
            statusEnum);
    }
}

