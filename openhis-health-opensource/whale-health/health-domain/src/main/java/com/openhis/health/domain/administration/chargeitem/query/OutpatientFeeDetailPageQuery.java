/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentScopeQueryModel;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 门诊费用明细分页读查询（DM-ADM-011 §6.2.25～6.2.26；AS-OP-FIN-005 IF-OP-FDT-02/03）。
 *
 * <p>payment 侧筛选（{@code billDateFrom/To}、{@code cashierId}、{@code tradeDirection}）由
 * {@link com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService}
 * 解析为 {@link #paymentChargeItemLinks}；就诊/患者/产品筛选项由
 * {@link com.openhis.health.application.outpatient.financial.feedetail.support.OutpatientFeeDetailLoader} 预解析后写入 scope 字段。</p>
 */
@Getter
@Setter
public class OutpatientFeeDetailPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 结算业务日起（闭区间，本地日历日）。 */
    private LocalDate billDateFrom;

    /** 结算业务日止（闭区间，本地日历日）。 */
    private LocalDate billDateTo;

    /** 挂号科室（Encounter.organization_id）。 */
    private Long registrationDepartmentId;

    /** 费用大类多选（{@link com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants}）。 */
    private List<String> feeCategoryCodes;

    /** 产品细分类字典 code 多选。 */
    private List<String> subCategoryCodes;

    /** 患者关键字（姓名、门诊号 busNo）。 */
    private String patientKeyword;

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）；空则不筛选。 */
    private String feeNatureContractNo;

    /** 项目名称模糊；与 {@link #productCodeKeyword} 双填时 AND。 */
    private String productNameKeyword;

    /** 院内项目编码模糊；与 {@link #productNameKeyword} 双填时 AND。 */
    private String productCodeKeyword;

    /** 收费人（payment.enterer_id）。 */
    private Long cashierId;

    /**
     * payment {@code enterer_id} 匹配白名单（Practitioner 主键 + 绑定用户主键）；由应用层写入。
     */
    private List<Long> entererMatchIds;

    /** 收退 Tab：ALL / CHARGE / REFUND。 */
    private String tradeDirection;

    /**
     * Payment 读侧预解析后的 chargeItem 主键集合；空集合表示无命中。
     */
    private List<Long> resolvedChargeItemScope;

    /**
     * 可选就诊收窄集合；{@code null} 表示不按就诊 IN 过滤。
     */
    private List<Long> resolvedEncounterScope;

    /**
     * payment 快照展开后的 chargeItem 关联列表（一快照一行；同 chargeItem 可多条以实现收退平账）。
     */
    private List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentChargeItemLinks;

    /**
     * Support 预解析后保留的 payment↔chargeItem 关联（与 {@link #resolvedChargeItemScope} 同源筛选）。
     */
    private List<OutpatientFeeDetailChargePaymentLinkQueryModel> resolvedPaymentChargeItemLinks;

    /**
     * chargeItemId → 任一 payment 上下文（兼容快照截断补全；明细行以 {@link #resolvedPaymentChargeItemLinks} 为准）。
     */
    private Map<Long, OutpatientFeeDetailPaymentContextQueryModel> paymentContextByChargeItemId;

    /**
     * bill_date 筛选命中的 payment scope 列表；供快照截断时补全已结 chargeItem（DM-FIN-002 §6.2.17）。
     */
    private List<OutpatientFeeDetailPaymentScopeQueryModel> paymentsInScope;

    /**
     * Support 预解析阶段已批量加载的 ChargeItem（id → 实体）；供同请求内候选行物化复用，避免二次 IN。
     */
    private Map<Long, ChargeItem> preloadedChargeItemsById;

    /**
     * 同一次请求内已物化的候选行缓存；page / summary / 导出翻页复用，禁止重复加载 ChargeItem。
     */
    private List<OutpatientFeeDetailChargeRowQueryModel> cachedChargeRows;
}
