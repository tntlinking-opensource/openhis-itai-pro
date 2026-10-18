/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.support;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.queries.model.PractitionerWorkloadEncounterContextQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.zxplat.application.shared.report.ChargeItemOrderingDoctorFromRequestSupport;
import com.zxplat.application.shared.report.ReportChargeBillSummarizeScopeSupport;
import com.zxplat.domain.share.constants.PractitionerWorkloadConstants;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.OrganizationClassification;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 科室/医生工作量读路径 scope 预解析（application/report/practitionerworkload/support；AS-RPT-001 IF-RPT-WKL-02～04）。
 *
 * <p>跨聚合编排 Organization / Encounter / ChargeItem 读侧；不写库。
 * <strong>明细/汇总/导出统一 ChargeItem-only</strong>（账单 {@code entered_date} 窗 + 仅 {@code SETTLED} + 合成链路），
 * 禁止扫 Payment；开立科室筛 {@code ChargeItem.requesting_org_id}
 * （DD-报表中心-进售价口径，与门诊费用明细同净口径）。</p>
 * <p>挂号净人次仍按 {@code Encounter.organization_id}（方案 C），与费用开立科室筛选值同源但语义不同。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerWorkloadLoader {

    private final OrganizationQueryService organizationQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ReportChargeBillSummarizeScopeSupport reportChargeBillSummarizeScopeSupport;
    private final ChargeItemOrderingDoctorFromRequestSupport chargeItemOrderingDoctorFromRequestSupport;

    /**
     * 预解析科室范围、就诊 scope、账单 chargeItem 快照与就诊上下文（与汇总同口径，禁止扫 Payment）。
     *
     * @param query 须含 {@code encounterScope}、{@code billDateFrom}/{@code billDateTo}
     * @throws IllegalArgumentException 参数非法或科室与就诊域不一致（对齐 EX-RPT-WKL-102/106）
     */
    public void preparePractitionerWorkloadQueryScopes(PractitionerWorkloadPageQuery query) {
        this.preparePractitionerWorkloadSummarizeScopesFromChargeBill(query);
    }

    /**
     * 明细/汇总/导出：按账单收费时间窗解析 chargeItem（与费用明细同构），禁止扫 Payment。
     *
     * <p>费用行<strong>不再</strong>先按挂号科室就诊求交——否则跨科开立会被误剔，售价与费用明细对不上。
     * 仅按就诊 class（AMB/IMP）+ 开立科室 {@code requesting_org_id} + 仅 SETTLED 收窄。</p>
     *
     * @param query 须含日期窗与 encounterScope
     */
    public void preparePractitionerWorkloadSummarizeScopesFromChargeBill(PractitionerWorkloadPageQuery query) {
        // Gate：与明细分页同必填校验
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
        // Pre：开立科室优先；遗留 organizationId 回落（报表中心统一 requesting_org_id）
        this.normalizeRequestingOrgFilter(query);
        String encounterClassCode = this.resolveEncounterClassCode(query.getEncounterScope());
        query.setResolvedEncounterClassCode(encounterClassCode);
        List<Long> organizationIds = this.resolveOrganizationIds(query, encounterClassCode);
        query.setResolvedOrganizationIds(organizationIds);
        // Pre：挂号医生收窄仍走挂号业务日窗就诊（方案 C）；与费用 scope 解耦
        this.resolveRegistrationDoctorEncounterScope(query, encounterClassCode, organizationIds);
        LocalDateTime enteredFrom = query.getBillDateFrom().atStartOfDay();
        LocalDateTime enteredToExclusive = query.getBillDateTo().plusDays(1).atStartOfDay();
        // Pre：仅 SETTLED（与费用明细净口径一致；退费不入统计）
        List<ChargeItem> windowChargeItems =
            this.chargeItemQueryService.listSettledChargeItemsByEnteredDateWindow(
                enteredFrom, enteredToExclusive, null);
        LinkedHashSet<Long> windowEncounterIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : windowChargeItems) {
            if (chargeItem != null && chargeItem.getEncounterId() != null) {
                windowEncounterIds.add(chargeItem.getEncounterId());
            }
        }
        // Pre：门诊/住院 class 求交（禁止再按 Encounter.organization_id 当开立科室）
        Set<Long> classMatchedEncounterIds =
            this.filterEncounterIdsByClass(windowEncounterIds, encounterClassCode);
        if (classMatchedEncounterIds.isEmpty()) {
            this.clearChargeScopes(query);
            return;
        }
        List<ChargeItem> scopedChargeItems = new ArrayList<>();
        for (ChargeItem chargeItem : windowChargeItems) {
            if (chargeItem == null || chargeItem.getId() == null || chargeItem.getEncounterId() == null) {
                continue;
            }
            // Pre：就诊域 class 不匹配则跳过（住院行不进门诊工作量）
            if (!classMatchedEncounterIds.contains(chargeItem.getEncounterId())) {
                continue;
            }
            // Pre：开立科室 = ChargeItem.requesting_org_id（DD；与费用明细同筛）
            if (!this.matchesRequestingOrgFilter(query, chargeItem)) {
                continue;
            }
            scopedChargeItems.add(chargeItem);
        }
        if (scopedChargeItems.isEmpty()) {
            this.clearChargeScopes(query);
            return;
        }
        // Pre：选医生时按 Request.requester_id 预筛（BR-RPT-WKL-005）
        if (query.getPractitionerId() != null) {
            query.setOrderingDoctorChargeItemScope(
                this.chargeItemOrderingDoctorFromRequestSupport.filterChargeItemIdsByOrderingDoctor(
                    scopedChargeItems, query.getPractitionerId()));
        } else {
            query.setOrderingDoctorChargeItemScope(null);
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> links =
            this.reportChargeBillSummarizeScopeSupport.buildSyntheticPaymentLinks(scopedChargeItems);
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextByChargeItemId =
            this.reportChargeBillSummarizeScopeSupport.buildSyntheticPaymentContextByChargeItemId(scopedChargeItems);
        List<Long> chargeItemIds = new ArrayList<>();
        LinkedHashSet<Long> encounterIdsForContext = new LinkedHashSet<>();
        for (ChargeItem chargeItem : scopedChargeItems) {
            chargeItemIds.add(chargeItem.getId());
            encounterIdsForContext.add(chargeItem.getEncounterId());
        }
        // Asm：费用命中就诊即 resolvedEncounterScope（供下游 charge 行匹配；非挂号科室全量）
        query.setResolvedEncounterScope(new ArrayList<>(encounterIdsForContext));
        query.setPaymentChargeItemLinks(links);
        query.setResolvedPaymentChargeItemLinks(links);
        query.setPaymentContextByChargeItemId(contextByChargeItemId);
        query.setResolvedChargeItemScope(chargeItemIds);
        query.setPaymentsInScope(List.of());
        Map<Long, PractitionerWorkloadEncounterContextQueryModel> contextByEncounterId =
            this.encounterQueryService.mapPractitionerWorkloadEncounterContextByIds(encounterIdsForContext);
        query.setEncounterContextByEncounterId(contextByEncounterId);
    }

    /**
     * 选医生时解析挂号医生就诊白名单（业务日窗 + REGISTRATION_DOCTOR）；未选医生则清空。
     *
     * @param query              已归一查询
     * @param encounterClassCode AMB/IMP
     * @param organizationIds    挂号科室范围
     */
    private void resolveRegistrationDoctorEncounterScope(
        PractitionerWorkloadPageQuery query,
        String encounterClassCode,
        List<Long> organizationIds) {
        if (query.getPractitionerId() == null) {
            query.setRegistrationDoctorEncounterScope(null);
            return;
        }
        // Pre：与 AS-RPT-007 同业务日闭区间，再滤挂号医生
        LocalDateTime businessTimeFrom = query.getBillDateFrom().atStartOfDay();
        LocalDateTime businessTimeTo = query.getBillDateTo().atTime(LocalTime.MAX);
        List<Long> registrationCandidates =
            this.encounterQueryService.listEncounterIdsForRegistrationBusinessWindow(
                encounterClassCode, organizationIds, businessTimeFrom, businessTimeTo);
        Set<Long> registrationScope = this.encounterQueryService.filterEncounterIdsByActiveRegistrationDoctor(
            registrationCandidates, query.getPractitionerId());
        query.setRegistrationDoctorEncounterScope(registrationScope);
    }

    /**
     * 费用无命中时清空 charge 相关 scope（保留已解析科室/挂号医生 scope）。
     *
     * @param query 目标查询
     */
    private void clearChargeScopes(PractitionerWorkloadPageQuery query) {
        query.setResolvedEncounterScope(List.of());
        query.setResolvedChargeItemScope(List.of());
        query.setPaymentChargeItemLinks(List.of());
        query.setResolvedPaymentChargeItemLinks(List.of());
        query.setPaymentContextByChargeItemId(Collections.emptyMap());
        query.setPaymentsInScope(List.of());
        query.setEncounterContextByEncounterId(Collections.emptyMap());
        query.setOrderingDoctorChargeItemScope(null);
    }

    /**
     * 按就诊 class 过滤主键集合（与费用明细 {@code filterEncounterIdsByClass} 同构）。
     *
     * @param encounterIds       候选就诊
     * @param encounterClassCode AMB/IMP 存储码
     * @return 命中 class 的就诊 id
     */
    private Set<Long> filterEncounterIdsByClass(Set<Long> encounterIds, String encounterClassCode) {
        if (encounterIds == null || encounterIds.isEmpty() || StringUtils.isEmpty(encounterClassCode)) {
            return Set.of();
        }
        List<Encounter> encounters = this.encounterQueryService.listEncounterEntitiesByIds(encounterIds);
        LinkedHashSet<Long> matched = new LinkedHashSet<>();
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            // Pre：仅保留当前门诊/住院域
            if (encounterClassCode.equals(encounter.getClassEnum())) {
                matched.add(encounter.getId());
            }
        }
        return matched;
    }

    /**
     * 开立科室筛：已选 {@code requestingOrgId}（或遗留 organizationId 归一后）须等于账单开立科室。
     *
     * @param query      已归一请求
     * @param chargeItem 账单行
     * @return 未选科室或命中为 true
     */
    private boolean matchesRequestingOrgFilter(PractitionerWorkloadPageQuery query, ChargeItem chargeItem) {
        Long requestingOrgId = query.getRequestingOrgId();
        if (requestingOrgId == null) {
            // 未选开立科室：仍须落在域内 resolved 科室内（与挂号科室列表同域）
            List<Long> resolvedOrganizationIds = query.getResolvedOrganizationIds();
            if (resolvedOrganizationIds == null || resolvedOrganizationIds.isEmpty()) {
                return false;
            }
            Long chargeRequestingOrgId = chargeItem.getRequestingOrgId();
            if (chargeRequestingOrgId == null) {
                return false;
            }
            return resolvedOrganizationIds.contains(chargeRequestingOrgId);
        }
        return requestingOrgId.equals(chargeItem.getRequestingOrgId());
    }

    /**
     * 报表中心开立科室优先；仅传遗留 organizationId 时回填 requestingOrgId。
     *
     * @param query 工作量查询
     */
    private void normalizeRequestingOrgFilter(PractitionerWorkloadPageQuery query) {
        if (query.getRequestingOrgId() == null && query.getOrganizationId() != null) {
            query.setRequestingOrgId(query.getOrganizationId());
        }
        // 挂号净人次仍用 organizationId 解析科室列表；与开立科室同值时保持一致
        if (query.getOrganizationId() == null && query.getRequestingOrgId() != null) {
            query.setOrganizationId(query.getRequestingOrgId());
        }
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
     * @param query              分页入参
     * @param encounterClassCode IMP/AMB code
     * @return 参与统计的 organizationId 列表
     * @throws IllegalArgumentException 科室不存在或 class 不匹配
     */
    private List<Long> resolveOrganizationIds(PractitionerWorkloadPageQuery query, String encounterClassCode) {
        Long selectedOrgId = query.getOrganizationId();
        if (selectedOrgId == null) {
            selectedOrgId = query.getRequestingOrgId();
        }
        if (selectedOrgId != null) {
            Organization organization =
                this.organizationQueryService.loadOrganizationByIdForDisplay(selectedOrgId);
            if (organization == null) {
                throw new IllegalArgumentException("organization not found or not in tenant scope");
            }
            // 已选科室须与门诊/住院域 class_enum 一致（BR-RPT-WKL-004a）
            if (!this.expectedOrganizationClassification(encounterClassCode).equals(organization.getClassEnum())) {
                throw new IllegalArgumentException("organization class does not match encounterScope");
            }
            return List.of(selectedOrgId);
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
