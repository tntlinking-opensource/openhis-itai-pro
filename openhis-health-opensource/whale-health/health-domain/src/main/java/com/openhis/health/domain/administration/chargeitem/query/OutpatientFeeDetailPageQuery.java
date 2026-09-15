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

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 门诊费用明细分页读查询（DM-ADM-011 §6.2.25～6.2.26；AS-OP-FIN-005 IF-OP-FDT-02/03；AS-RPT-008/009）。
 *
 * <p>时间窗 {@code billDateFrom/To} 为本地时刻闭区间，映射 Payment {@code bill_date}/{@code enterer_id}
 * （BR-OP-FDT-003；与日结同轴；报表可精确到秒）。应用层将 Request 的
 * {@code yyyy-MM-dd} 或 {@code yyyy-MM-dd HH:mm:ss} 规范为时刻（日仅默认 00:00:00～23:59:59）。
 * 由 {@link com.openhis.health.application.outpatient.financial.feedetail.support.OutpatientFeeDetailLoader}
 * 写入 payment→chargeItem 链路与 resolved scope。ChargeItem 状态白名单默认
 * {@code SETTLED}∪{@code REFUNDING}∪{@code REFUNDED}（写路径退费会改行态；行额符号看 payment 收退上下文）。</p>
 */
@Getter
@Setter
public class OutpatientFeeDetailPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 结算业务窗起（闭区间，本地时刻；含时分秒）。 */
    private LocalDateTime billDateFrom;

    /** 结算业务窗止（闭区间，本地时刻；含时分秒）。 */
    private LocalDateTime billDateTo;

    /** 挂号科室（Encounter.organization_id；遗留筛选项，报表中心改用 {@link #requestingOrgId}）。 */
    private Long registrationDepartmentId;

    /**
     * 开立科室（账单 {@code ChargeItem.requesting_org_id} / 对应 req 开立科室）。
     *
     * <p>报表中心费用明细 / 工作量等统一按本字段筛选；与就诊挂号科室不是同一概念。</p>
     */
    private Long requestingOrgId;

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
     * 允许纳入的收费状态码白名单（门诊费用明细由 Loader 写入
     * {@code SETTLED}/{@code REFUNDING}/{@code REFUNDED}；住院可含 {@code PENDING_SETTLEMENT}）。
     *
     * <p>空或 null 时域层候选行回落仅 {@code SETTLED}；门诊读路径须由
     * {@code OutpatientFeeDetailLoader} 显式写入三态，避免退费中行被静默丢弃。</p>
     */
    private List<String> allowedChargeItemStatusCodes;

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

    /**
     * 为 true 时跳过医保基金+个账分摊金额挂载（fin_type 汇总不读该列；避免按 payment 拉 PaymentRecDetail）。
     */
    private Boolean skipYbFundAmountAttach;

    /**
     * 明细分页：DB 分页后的就诊块 total（命中 encounter 全集计数）。
     *
     * <p>由 {@code prepare*PageScopes} 写入；{@code pageOutpatientFeeDetailEncounterBlocks}
     * 在 {@link #pageScopeOnly} 时直接作为 Page.total，禁止再内存 collect 全日窗 encounter 序。</p>
     */
    private Long resolvedEncounterPageTotal;

    /**
     * 为 true 时表示 {@link #resolvedPaymentChargeItemLinks} / {@link #preloadedChargeItemsById}
     * 仅含本页就诊；域层分页须短路组块，禁止再对全日窗 links 排序切片。
     */
    private Boolean pageScopeOnly;

    /**
     * 硬筛选后命中的就诊主键全集（非仅本页）；供页脚 SQL SUM 的 encounter IN。
     *
     * <p>明细分页页脚须对全集求和，禁止只 SUM 本页。</p>
     */
    private List<Long> resolvedFeeDetailMatchedEncounterIds;

    /**
     * 页脚售价合计（已由 Loader 对命中投影/SQL 算出时写入；非空则 AppService 直接采用）。
     *
     * <p>项目关键字等无法完全下推 SQL 时，由轻量投影代数和写入，避免为页脚再装全日窗实体。</p>
     */
    private BigDecimal resolvedSaleAmountTotal;

    /**
     * 硬筛选后命中的 chargeItem 主键全集（非仅本页）；供页脚历史进价批量解析。
     *
     * <p>无硬筛时可空：页脚进价改为按 {@link #resolvedFeeDetailMatchedEncounterIds} 日窗 IN 装载。</p>
     */
    private List<Long> resolvedFeeDetailMatchedChargeItemIds;
}
