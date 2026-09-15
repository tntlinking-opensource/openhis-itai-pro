/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.settlement;

import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.queries.InsuranceFeeDetailAmountSummary;
import com.openhis.chs.domain.settlement.queries.InsuranceFeeDetailPageCriteria;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.openhis.health.domain.share.constants.InsuranceFeeDetailReportConstants;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionIn.InsuranceSettlementFeeDetailQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFeeDetailLineExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFeeDetailPageExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFeeDetailSummaryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFundLookupExtensionOut;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 吉林医保结算回执 lookup SPI 实现（日结 insutype、博思基金分项）。
 *
 * <p><b>SPI 接口</b>：{@link InsuranceSettlementRecordLookupExtension}</p>
 * <p><b>Bridge 委托</b>：{@link YbSettlementRecordQueryService}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(YbSettlementRecordQueryService.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceSettlementRecordLookupExtensionImpl implements InsuranceSettlementRecordLookupExtension {

    private final YbSettlementRecordQueryService settlementRecordQueryService;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Map<Long, String> mapInsutypeBySettlementRecordIds(Collection<Long> settlementRecordIds) {
        if (settlementRecordIds == null || settlementRecordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return Collections.emptyMap();
        }
        return settlementRecordQueryService.mapInsutypeByIds(settlementRecordIds, tenantId);
    }

    @Override
    public Map<Long, String> mapInsutypeByPaymentIds(Collection<Long> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return Collections.emptyMap();
        }
        return settlementRecordQueryService.mapInsutypeByPaymentIds(paymentIds, tenantId);
    }

    @Override
    public Map<Long, BigDecimal> mapRetailUploadDiffBySettlementRecordIds(
        Collection<Long> settlementRecordIds) {
        if (settlementRecordIds == null || settlementRecordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return Collections.emptyMap();
        }
        return settlementRecordQueryService.mapRetailUploadDiffByIds(settlementRecordIds, tenantId);
    }

    @Override
    public Map<Long, BigDecimal> mapRetailUploadDiffByPaymentIds(Collection<Long> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return Collections.emptyMap();
        }
        return settlementRecordQueryService.mapRetailUploadDiffByPaymentIds(paymentIds, tenantId);
    }

    @Override
    public Optional<InsuranceSettlementFundLookupExtensionOut> findFundByPaymentId(Long paymentId) {
        return findFundByPaymentId(paymentId, TenantUtils.getCurrentTenantId());
    }

    /**
     * 按支付单 + 显式租户取基金分项；租户优先用入参，避免异步开票线程 TenantUtils 为空误走全自费。
     *
     * @param paymentId 支付单主键
     * @param tenantId  票据租户；可空（再回落 TenantUtils）
     * @return 已确认结算基金分项
     */
    @Override
    public Optional<InsuranceSettlementFundLookupExtensionOut> findFundByPaymentId(
            Long paymentId, Long tenantId) {
        if (paymentId == null) {
            return Optional.empty();
        }
        Long resolvedTenantId = tenantId;
        // 入参租户优先；空则回落线程租户（HTTP 入队场景）
        if (resolvedTenantId == null) {
            resolvedTenantId = TenantUtils.getCurrentTenantId();
        }
        if (resolvedTenantId == null) {
            return Optional.empty();
        }
        return settlementRecordQueryService.findFirstConfirmedByPaymentId(paymentId, resolvedTenantId)
            .map(this::toFundOut);
    }

    /**
     * 结算回执 → 博思基金分项出参（字段一一搬运）。
     *
     * @param row CONFIRMED 回执
     * @return 基金分项
     */
    private InsuranceSettlementFundLookupExtensionOut toFundOut(YbSettlementRecord row) {
        return new InsuranceSettlementFundLookupExtensionOut()
            .setSettlementRecordId(row.getId())
            .setPaymentId(row.getPaymentId())
            .setContractId(row.getContractId())
            .setAccountId(row.getAccountId())
            .setPsnNo(row.getPsnNo())
            .setStatus(row.getStatus())
            .setMedfeeSumamt(row.getMedfeeSumamt())
            .setFulamtOwnpayAmt(row.getFulamtOwnpayAmt())
            .setOverlmtSelfpay(row.getOverlmtSelfpay())
            .setPreselfpayAmt(row.getPreselfpayAmt())
            .setInscpScpAmt(row.getInscpScpAmt())
            .setActPayDedc(row.getActPayDedc())
            .setHifpPay(row.getHifpPay())
            .setPoolPropSelfpay(row.getPoolPropSelfpay())
            .setCvlservPay(row.getCvlservPay())
            .setHifesPay(row.getHifesPay())
            .setHifmiPay(row.getHifmiPay())
            .setHifobPay(row.getHifobPay())
            .setMafPay(row.getMafPay())
            .setHifdmPay(row.getHifdmPay())
            .setOthPay(row.getOthPay())
            .setFundPaySumamt(row.getFundPaySumamt())
            .setPsnPartAmt(row.getPsnPartAmt())
            .setAcctPay(row.getAcctPay())
            .setPsnCashPay(row.getPsnCashPay())
            .setHospPartAmt(row.getHospPartAmt());
    }

    /**
     * 医保费用明细：委托领域 CONFIRMED 净行 DB 分页。
     *
     * @param query SPI 筛选入参
     * @return 分页出参
     */
    @Override
    public InsuranceSettlementFeeDetailPageExtensionOut pageConfirmedFeeDetailLines(
            InsuranceSettlementFeeDetailQueryExtensionIn query) {
        InsuranceFeeDetailPageCriteria criteria = this.toFeeDetailCriteria(query);
        Page<YbSettlementRecord> page =
            this.settlementRecordQueryService.pageConfirmedInsuranceFeeDetail(criteria);
        InsuranceSettlementFeeDetailPageExtensionOut out =
            new InsuranceSettlementFeeDetailPageExtensionOut();
        out.setTotalCount(page.getTotalElements());
        out.setPageNo(page.getNumber() + 1);
        out.setPageSize(page.getSize());
        List<InsuranceSettlementFeeDetailLineExtensionOut> lines = new ArrayList<>();
        for (YbSettlementRecord row : page.getContent()) {
            // 跳过空实体，避免脏页污染
            if (row == null) {
                continue;
            }
            lines.add(this.toFeeDetailLineOut(row));
        }
        out.setContent(lines);
        return out;
    }

    /**
     * 医保费用明细：委托领域 SQL SUM 四列合计。
     *
     * @param query SPI 筛选入参（分页字段忽略）
     * @return 合计出参
     */
    @Override
    public InsuranceSettlementFeeDetailSummaryExtensionOut summarizeConfirmedFeeDetailAmounts(
            InsuranceSettlementFeeDetailQueryExtensionIn query) {
        InsuranceFeeDetailPageCriteria criteria = this.toFeeDetailCriteria(query);
        InsuranceFeeDetailAmountSummary summary =
            this.settlementRecordQueryService.summarizeConfirmedInsuranceFeeDetail(criteria);
        InsuranceSettlementFeeDetailSummaryExtensionOut out =
            new InsuranceSettlementFeeDetailSummaryExtensionOut();
        if (summary == null) {
            out.setLineCount(0L);
            out.setMedfeeSumamtTotal(BigDecimal.ZERO);
            out.setFundPaySumamtTotal(BigDecimal.ZERO);
            out.setAcctPayTotal(BigDecimal.ZERO);
            out.setPsnCashPayTotal(BigDecimal.ZERO);
            return out;
        }
        out.setLineCount(summary.getLineCount());
        out.setMedfeeSumamtTotal(summary.getMedfeeSumamtTotal());
        out.setFundPaySumamtTotal(summary.getFundPaySumamtTotal());
        out.setAcctPayTotal(summary.getAcctPayTotal());
        out.setPsnCashPayTotal(summary.getPsnCashPayTotal());
        return out;
    }

    /**
     * SPI 入参 → 领域 Criteria；租户优先入参，空则 TenantUtils。
     *
     * @param query SPI 入参；可空
     * @return 领域筛选（字段可空由 QS Gate）
     */
    private InsuranceFeeDetailPageCriteria toFeeDetailCriteria(
            InsuranceSettlementFeeDetailQueryExtensionIn query) {
        InsuranceFeeDetailPageCriteria criteria = new InsuranceFeeDetailPageCriteria();
        if (query == null) {
            return criteria;
        }
        Long tenantId = query.getTenantId();
        // 入参租户优先；空则回落线程租户
        if (tenantId == null) {
            tenantId = TenantUtils.getCurrentTenantId();
        }
        criteria.setTenantId(tenantId);
        criteria.setEncounterClass(query.getEncounterClass());
        criteria.setSetlTimeFromInclusive(query.getSetlTimeFromInclusive());
        criteria.setSetlTimeToExclusive(query.getSetlTimeToExclusive());
        criteria.setWorkInjuryOnly(query.getWorkInjuryOnly());
        criteria.setWorkInjurySettleMode(InsuranceFeeDetailReportConstants.SETTLE_MODE_WORK_INJURY);
        criteria.setContractIds(query.getContractIds());
        criteria.setInsutypes(query.getInsutypes());
        criteria.setEmptyEncounterScope(query.getEmptyEncounterScope());
        criteria.setEncounterIds(query.getEncounterIds());
        criteria.setSetlIdKeyword(query.getSetlIdKeyword());
        criteria.setMdtrtIdKeyword(query.getMdtrtIdKeyword());
        criteria.setPatientNameKeyword(query.getPatientNameKeyword());
        criteria.setPageNo(query.getPageNo());
        criteria.setPageSize(query.getPageSize());
        return criteria;
    }

    /**
     * 结算回执实体 → SPI 明细行（busNo 由应用层 Encounter enrich）。
     *
     * @param row 未删除 CONFIRMED 回执
     * @return 行出参
     */
    private InsuranceSettlementFeeDetailLineExtensionOut toFeeDetailLineOut(YbSettlementRecord row) {
        InsuranceSettlementFeeDetailLineExtensionOut out =
            new InsuranceSettlementFeeDetailLineExtensionOut();
        out.setSettlementRecordId(row.getId());
        out.setEncounterId(row.getEncounterId());
        out.setPatientName(row.getPsnName());
        out.setSetlId(row.getSetlId());
        out.setMdtrtId(row.getMdtrtId());
        // 展示用结算时间：空 setl_time 回落 created_at，与筛选窗口径一致
        if (row.getSetlTime() != null) {
            out.setSetlTime(row.getSetlTime());
        } else {
            out.setSetlTime(row.getCreatedAt());
        }
        out.setMedfeeSumamt(row.getMedfeeSumamt());
        out.setFundPaySumamt(row.getFundPaySumamt());
        out.setAcctPay(row.getAcctPay());
        out.setPsnCashPay(row.getPsnCashPay());
        return out;
    }
}
