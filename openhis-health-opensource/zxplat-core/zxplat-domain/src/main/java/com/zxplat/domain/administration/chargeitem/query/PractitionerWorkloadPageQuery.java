/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.query;

import com.zxplat.domain.administration.encounter.queries.model.PractitionerWorkloadEncounterContextQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentScopeQueryModel;
import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 科室/医生工作量统计读查询（DM-ADM-011；AS-RPT-001；DD-报表中心-进售价口径）。
 *
 * <p>明细/汇总统一 ChargeItem-only：账单收费时间窗 + 合成链路（禁止扫 Payment）。
 * 开立科室筛 {@link #requestingOrgId}（{@code ChargeItem.requesting_org_id}）；
 * 科室范围与就诊 scope 由 {@code PractitionerWorkloadLoader} 预解析。
 * 汇总 Tab 未选科室时应用层跨 org 合并 fin_type / 号源（BR-RPT-WKL-016）。</p>
 */
@Getter
@Setter
public class PractitionerWorkloadPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 业务日起（闭区间；对齐账单 {@code entered_date}/{@code occurrence_time} 窗）。 */
    private LocalDate billDateFrom;

    /** 业务日止（闭区间）。 */
    private LocalDate billDateTo;

    /** 门诊/住院范围（{@link com.zxplat.domain.share.constants.PractitionerWorkloadConstants}）。 */
    private String encounterScope;

    /**
     * 遗留科室筛（挂号净人次 Encounter.organization_id）；报表中心请用 {@link #requestingOrgId}。
     *
     * <p>Loader 会与 requestingOrgId 互相同步。</p>
     */
    private Long organizationId;

    /**
     * 开立科室（可选；{@code ChargeItem.requesting_org_id}）。
     *
     * <p>未选 = 域内全部 ACTIVE 科室开立行合并。</p>
     */
    private Long requestingOrgId;

    /** 可选医生（费用 Request {@code requester_id}；挂号 {@code REGISTRATION_DOCTOR} 参与者）。 */
    private Long practitionerId;

    /** Support 写入：解析后的科室 id 集合（未选科室 = 该域全部 ACTIVE 科室）。 */
    private List<Long> resolvedOrganizationIds;

    /** Support 写入：解析后的就诊类别存储码（AMB / IMP）。 */
    private String resolvedEncounterClassCode;

    /** Support 写入：class + 科室收窄后的就诊 id 集合。 */
    private List<Long> resolvedEncounterScope;

    /** Support 写入：选医生时 REGISTRATION_DOCTOR 命中就诊；未选医生时为 {@code null}。 */
    private Set<Long> registrationDoctorEncounterScope;

    /**
     * Support 写入：选医生时三类 Request {@code requester_id} 命中的 chargeItem 主键；未选医生时为 {@code null}。
     */
    private Set<Long> orderingDoctorChargeItemScope;

    /** Support 写入：账单预解析 chargeItem 主键集合。 */
    private List<Long> resolvedChargeItemScope;

    /**
     * Support 写入：账单合成关联（与费用明细同构；无真实 paymentId）。
     */
    private List<OutpatientFeeDetailChargePaymentLinkQueryModel> resolvedPaymentChargeItemLinks;

    /** Support 写入：与门诊费用明细同构的 chargeItem↔合成上下文关联。 */
    private List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentChargeItemLinks;

    /** Support 写入：chargeItemId → 合成读上下文。 */
    private Map<Long, OutpatientFeeDetailPaymentContextQueryModel> paymentContextByChargeItemId;

    /** 账单路径固定空列表（兼容旧字段；禁止扫 Payment）。 */
    private List<OutpatientFeeDetailPaymentScopeQueryModel> paymentsInScope;

    /** Encounter 读侧批量 enrich：encounterId → 挂号科室/号源/状态。 */
    private Map<Long, PractitionerWorkloadEncounterContextQueryModel> encounterContextByEncounterId;
}
