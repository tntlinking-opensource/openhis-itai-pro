/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.support;

import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.PractitionerWorkloadEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.PractitionerWorkloadConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 科室/医生工作量读路径 scope 预解析（application/report/practitionerworkload/support；AS-RPT-001 IF-RPT-WKL-02～04）。
 *
 * <p>跨聚合编排 Organization / Encounter / Payment 读侧；不写库。汇总 Tab 展示合并由应用层承担（BR-RPT-WKL-016）。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerWorkloadLoader {

    private final OrganizationQueryService organizationQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /**
     * 预解析科室范围、就诊 scope、payment 快照与就诊上下文。
     *
     * @param query 须含 {@code encounterScope}、{@code billDateFrom}/{@code billDateTo}
     * @throws IllegalArgumentException 参数非法或科室与就诊域不一致（对齐 EX-RPT-WKL-102/106）
     */
    public void preparePractitionerWorkloadQueryScopes(PractitionerWorkloadPageQuery query) {
        // Gate：必填筛选项与日期窗合法，避免下游空跑全表
        if (query == null) {
            throw new IllegalArgumentException("practitioner workload query is required");
        }
        if (StringUtils.isEmpty(query.getEncounterScope())) {
            throw new IllegalArgumentException("encounterScope is required");
        }
        if (query.getBillDateFrom() == null || query.getBillDateTo() == null) {
            throw new IllegalArgumentException("billDateFrom and billDateTo are required");
        }
        if (query.getBillDateFrom().isAfter(query.getBillDateTo())) {
            throw new IllegalArgumentException("billDateFrom must not be after billDateTo");
        }
        // Pre：门诊/住院 → Encounter.class_enum 存储码
        String encounterClassCode = this.resolveEncounterClassCode(query.getEncounterScope());
        query.setResolvedEncounterClassCode(encounterClassCode);
        // Pre：未选科室 = 该域全部 ACTIVE 科室；已选须与 encounterScope 类别一致
        List<Long> organizationIds = this.resolveOrganizationIds(query, encounterClassCode);
        query.setResolvedOrganizationIds(organizationIds);
        // Pre：就诊 scope = class + 挂号科室 IN（经 Encounter QS，B.6.4）
        List<Long> encounterScope = this.encounterQueryService.listEncounterIdsForPractitionerWorkloadScope(
            encounterClassCode, organizationIds);
        query.setResolvedEncounterScope(encounterScope);
        // Pre：选医生时挂号统计仅保留 REGISTRATION_DOCTOR 命中就诊
        if (query.getPractitionerId() != null) {
            Set<Long> registrationScope = this.encounterQueryService.filterEncounterIdsByActiveRegistrationDoctor(
                encounterScope, query.getPractitionerId());
            query.setRegistrationDoctorEncounterScope(registrationScope);
        } else {
            query.setRegistrationDoctorEncounterScope(null);
        }
        // Pre：payment bill_date 窗 + kind 解析 chargeItem 快照（DM-FIN-002 §6.2.19）
        List<Long> chargeItemIds =
            this.paymentReconciliationQueryService.listPractitionerWorkloadChargeItemIdsByPaymentFilter(query);
        query.setResolvedChargeItemScope(chargeItemIds);
        if (chargeItemIds.isEmpty()) {
            query.setEncounterContextByEncounterId(Collections.emptyMap());
            return;
        }
        // Pre：挂号号源分组需 Encounter 上下文（organization_id / service_type_id / status）
        LinkedHashSet<Long> encounterIdsForContext = new LinkedHashSet<>();
        for (Long encounterId : encounterScope) {
            if (encounterId != null) {
                encounterIdsForContext.add(encounterId);
            }
        }
        Map<Long, PractitionerWorkloadEncounterContextQueryModel> contextByEncounterId =
            this.encounterQueryService.mapPractitionerWorkloadEncounterContextByIds(encounterIdsForContext);
        query.setEncounterContextByEncounterId(contextByEncounterId);
    }

    /**
     * 工作量 encounterScope → {@link EncounterClass} code（住院 IMP / 门诊 AMB）。
     *
     * @param encounterScope 报表域 scope 常量
     * @return class_enum code
     * @throws IllegalArgumentException 不支持的 scope
     */
    private String resolveEncounterClassCode(String encounterScope) {
        if (PractitionerWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT.equals(encounterScope)) {
            return EncounterClass.IMP.getCode();
        }
        if (PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT.equals(encounterScope)) {
            return EncounterClass.AMB.getCode();
        }
        throw new IllegalArgumentException("unsupported encounterScope: " + encounterScope);
    }

    /**
     * 解析报表科室 id 列表：已选科室须与门诊/住院 class 一致；未选则全量 ACTIVE 科室。
     *
     * @param query               分页入参
     * @param encounterClassCode  IMP/AMB code
     * @return 参与统计的 organizationId 列表
     * @throws IllegalArgumentException 科室不存在或 class 不匹配
     */
    private List<Long> resolveOrganizationIds(PractitionerWorkloadPageQuery query, String encounterClassCode) {
        if (query.getOrganizationId() != null) {
            Organization organization =
                this.organizationQueryService.loadOrganizationByIdForDisplay(query.getOrganizationId());
            if (organization == null) {
                throw new IllegalArgumentException("organization not found or not in tenant scope");
            }
            // 已选科室须与门诊/住院域 class_enum 一致（BR-RPT-WKL-004a）
            if (!this.expectedOrganizationClassification(encounterClassCode).equals(organization.getClassEnum())) {
                throw new IllegalArgumentException("organization class does not match encounterScope");
            }
            return List.of(query.getOrganizationId());
        }
        // 未选科室：门诊挂号科室 / 住院入院科室全量 ACTIVE 列表
        List<Organization> organizations;
        if (EncounterClass.IMP.getCode().equals(encounterClassCode)) {
            organizations = this.organizationQueryService.listOrganizationsForInpatientAdmission();
        } else {
            organizations = this.organizationQueryService.listOrganizationsForOutpatientRegistration();
        }
        List<Long> ids = new ArrayList<>();
        for (Organization organization : organizations) {
            if (organization != null && organization.getId() != null) {
                ids.add(organization.getId());
            }
        }
        return ids;
    }

    /**
     * encounterClass 对应的科室 {@link OrganizationClassification}（住院 INPATIENT / 门诊 CLINIC）。
     *
     * @param encounterClassCode IMP 或 AMB code
     * @return 期望科室分类
     */
    private OrganizationClassification expectedOrganizationClassification(String encounterClassCode) {
        if (EncounterClass.IMP.getCode().equals(encounterClassCode)) {
            return OrganizationClassification.INPATIENT;
        }
        return OrganizationClassification.CLINIC;
    }
}
