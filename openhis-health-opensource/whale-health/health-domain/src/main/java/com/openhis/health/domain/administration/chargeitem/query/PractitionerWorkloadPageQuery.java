/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import com.openhis.health.domain.administration.encounter.queries.model.PractitionerWorkloadEncounterContextQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentScopeQueryModel;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 科室/医生工作量统计读查询（DM-ADM-011 §6.2.27～6.2.29；AS-RPT-001）。
 *
 * <p>payment 筛选由 {@link com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService}
 * 解析；科室范围与就诊 scope 由 {@link com.openhis.health.application.report.practitionerworkload.support.PractitionerWorkloadLoader} 预解析。
 * 汇总 Tab 未选科室时应用层跨 org 合并 fin_type / 号源（BR-RPT-WKL-016）。</p>
 */
@Getter
@Setter
public class PractitionerWorkloadPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 结算业务日起（闭区间；对齐 {@code fin_payment_reconciliation.bill_date}）。 */
    private LocalDate billDateFrom;

    /** 结算业务日止（闭区间）。 */
    private LocalDate billDateTo;

    /** 门诊/住院范围（{@link com.openhis.health.domain.share.constants.PractitionerWorkloadConstants}）。 */
    private String encounterScope;

    /** 可选单选科室；未选时 {@link #resolvedOrganizationIds} 为该域全部 ACTIVE 科室。 */
    private Long organizationId;

    /** 可选医生（费用 {@code enterer_id}；挂号 {@code REGISTRATION_DOCTOR} 参与者）。 */
    private Long practitionerId;

    /** Support 写入：解析后的科室 id 集合（未选科室 = 该域全部 ACTIVE 科室）。 */
    private List<Long> resolvedOrganizationIds;

    /** Support 写入：解析后的就诊类别存储码（AMB / IMP）。 */
    private String resolvedEncounterClassCode;

    /** Support 写入：class + 挂号科室收窄后的就诊 id 集合。 */
    private List<Long> resolvedEncounterScope;

    /** Support 写入：选医生时 REGISTRATION_DOCTOR 命中就诊；未选医生时为 {@code null}。 */
    private Set<Long> registrationDoctorEncounterScope;

    /** Support 写入：payment 预解析 chargeItem 主键集合。 */
    private List<Long> resolvedChargeItemScope;

    /** Support 写入：payment 快照展开关联（ChargeItem 读侧主入口）。 */
    private List<OutpatientFeeDetailChargePaymentLinkQueryModel> resolvedPaymentChargeItemLinks;

    /** Payment 读侧写入：与门诊费用明细同构的 chargeItem↔payment 关联。 */
    private List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentChargeItemLinks;

    /** Payment 读侧写入：chargeItemId → payment 上下文。 */
    private Map<Long, OutpatientFeeDetailPaymentContextQueryModel> paymentContextByChargeItemId;

    /** Payment 读侧写入：本次 bill_date 窗内 payment 头快照。 */
    private List<OutpatientFeeDetailPaymentScopeQueryModel> paymentsInScope;

    /** Encounter 读侧批量 enrich：encounterId → 挂号科室/号源/状态。 */
    private Map<Long, PractitionerWorkloadEncounterContextQueryModel> encounterContextByEncounterId;
}
