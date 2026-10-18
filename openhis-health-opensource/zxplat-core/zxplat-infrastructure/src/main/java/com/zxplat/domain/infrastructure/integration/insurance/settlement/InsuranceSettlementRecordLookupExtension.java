/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.settlement;

import com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionIn.InsuranceSettlementFeeDetailQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFeeDetailPageExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFeeDetailSummaryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFundLookupExtensionOut;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * 医保结算回执只读查询扩展（日结 insutype、博思基金分项、医保费用明细报表）。
 *
 * <p><b>吉林实现</b>：{@code InsuranceSettlementRecordLookupExtensionImpl} →
 * {@code YbSettlementRecordQueryService}。</p>
 */
public interface InsuranceSettlementRecordLookupExtension {

    /**
     * 扩展是否已装配。
     *
     * @return false 时日结险种维度回落为空码
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 批量解析结算回执险种类型。
     *
     * @param settlementRecordIds {@code yb_settlement_record.id} 集合
     * @return id → insutype；未命中不包含该 key
     */
    default Map<Long, String> mapInsutypeBySettlementRecordIds(Collection<Long> settlementRecordIds) {
        return Collections.emptyMap();
    }

    /**
     * 按支付单批量解析险种类型（桥表缺 {@code settlement_record_id} 时日结回落）。
     *
     * @param paymentIds {@code fin_payment_reconciliation.id} 集合
     * @return paymentId → insutype；未命中不包含该 key
     */
    default Map<Long, String> mapInsutypeByPaymentIds(Collection<Long> paymentIds) {
        return Collections.emptyMap();
    }

    /**
     * 批量解析结算回执「零售−上传」分差（收费日结勾稽）。
     *
     * @param settlementRecordIds {@code yb_settlement_record.id} 集合
     * @return id → retail_upload_diff_amt；未命中不包含该 key
     */
    default Map<Long, BigDecimal> mapRetailUploadDiffBySettlementRecordIds(
        Collection<Long> settlementRecordIds) {
        return Collections.emptyMap();
    }

    /**
     * 按支付单批量解析「零售−上传」分差（桥表缺 settlement_record_id 时回落）。
     *
     * @param paymentIds {@code fin_payment_reconciliation.id} 集合
     * @return paymentId → retail_upload_diff_amt；未命中不包含该 key
     */
    default Map<Long, BigDecimal> mapRetailUploadDiffByPaymentIds(Collection<Long> paymentIds) {
        return Collections.emptyMap();
    }

    /**
     * 按支付单取最早一笔 CONFIRMED 结算回执的基金分项（博思开票）。
     *
     * <p>租户取自 {@code TenantUtils}；异步开票线程可能无租户，调用方应优先
     * {@link #findFundByPaymentId(Long, Long)} 传入票据租户。</p>
     *
     * @param paymentId {@code fin_payment_reconciliation.id}
     * @return 基金分项；无有效回执时 empty
     */
    default Optional<InsuranceSettlementFundLookupExtensionOut> findFundByPaymentId(Long paymentId) {
        return Optional.empty();
    }

    /**
     * 按支付单 + 显式租户取最早一笔 CONFIRMED 结算回执基金分项（博思开票）。
     *
     * <p>异步 Worker 无线程租户时须走本方法，避免误判为全自费。</p>
     *
     * @param paymentId {@code fin_payment_reconciliation.id}
     * @param tenantId  票据/单据租户；空则回落 {@link #findFundByPaymentId(Long)}
     * @return 基金分项；无有效回执时 empty
     */
    default Optional<InsuranceSettlementFundLookupExtensionOut> findFundByPaymentId(
            Long paymentId, Long tenantId) {
        return findFundByPaymentId(paymentId);
    }

    /**
     * 医保费用明细：CONFIRMED 净行 DB 分页（AS-RPT-012/013）。
     *
     * <p>冲销/REVOKED 不入集；合计请另调 {@link #summarizeConfirmedFeeDetailAmounts}。</p>
     *
     * @param query 时间窗、就诊类、合同/工伤、险种、就诊白名单与编号模糊
     * @return 当前页行 + totalCount；未装配 CHS 时空页
     */
    default InsuranceSettlementFeeDetailPageExtensionOut pageConfirmedFeeDetailLines(
            InsuranceSettlementFeeDetailQueryExtensionIn query) {
        InsuranceSettlementFeeDetailPageExtensionOut empty =
            new InsuranceSettlementFeeDetailPageExtensionOut();
        empty.setTotalCount(0L);
        if (query != null) {
            if (query.getPageNo() != null) {
                empty.setPageNo(query.getPageNo());
            }
            if (query.getPageSize() != null) {
                empty.setPageSize(query.getPageSize());
            }
        }
        return empty;
    }

    /**
     * 医保费用明细：同筛选四列金额 SQL SUM（禁止全窗物化后再加总）。
     *
     * @param query 与 {@link #pageConfirmedFeeDetailLines} 同筛选（分页字段可忽略）
     * @return 笔数与四列合计；未装配 CHS 时零合计
     */
    default InsuranceSettlementFeeDetailSummaryExtensionOut summarizeConfirmedFeeDetailAmounts(
            InsuranceSettlementFeeDetailQueryExtensionIn query) {
        return new InsuranceSettlementFeeDetailSummaryExtensionOut()
            .setLineCount(0L)
            .setMedfeeSumamtTotal(BigDecimal.ZERO)
            .setFundPaySumamtTotal(BigDecimal.ZERO)
            .setAcctPayTotal(BigDecimal.ZERO)
            .setPsnCashPayTotal(BigDecimal.ZERO);
    }
}
