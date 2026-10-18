/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.mapper;

import com.zxplat.application.inpatient.financial.settle.request.LoadInpatientSettleSummaryRequest;
import com.zxplat.application.inpatient.financial.settle.request.LoadSettledInpatientPaymentViewRequest;
import com.zxplat.application.inpatient.financial.settle.request.QueryInpatientSettleChargesRequest;
import com.zxplat.application.inpatient.financial.settle.response.LoadInpatientSettleSummaryResponse;
import com.zxplat.application.inpatient.financial.settle.response.LoadSettledInpatientPaymentViewResponse;
import com.zxplat.application.inpatient.financial.settle.response.QueryInpatientSettleChargesResponse;
import com.zxplat.application.inpatient.financial.settle.support.InpatientSettleChargesQuery;
import com.zxplat.application.inpatient.financial.settle.support.InpatientSettleChargesResult;
import com.zxplat.application.inpatient.financial.settle.support.InpatientSettleSummaryQuery;
import com.zxplat.application.inpatient.financial.settle.support.InpatientSettleSummaryResult;
import com.zxplat.application.inpatient.financial.settle.support.SettledInpatientPaymentViewQuery;
import com.zxplat.application.inpatient.financial.settle.support.SettledInpatientPaymentViewResult;
import org.springframework.stereotype.Component;

/**
 * 住院结算读路径 Query/Result 与 HTTP Request/Response 映射（IF-IP-STL-03/04/07）。
 */
@Component
public class InpatientSettleReadMapper {

    /**
     * 摘要 Request → 读 Query。
     *
     * @param request HTTP 请求；可空
     * @return 摘要查询；空请求返回空 Query
     */
    public InpatientSettleSummaryQuery toSummaryQuery(LoadInpatientSettleSummaryRequest request) {
        if (request == null) {
            return new InpatientSettleSummaryQuery();
        }
        return new InpatientSettleSummaryQuery()
            .setEncounterId(request.getEncounterId())
            .setPatientId(request.getPatientId())
            .setSelectedChargeItemIds(request.getSelectedChargeItemIds());
    }

    /**
     * 摘要读结果 → HTTP Response（含自费 P/O/C 预估字段）。
     *
     * @param result 读侧结果；可空
     * @return 摘要响应；空结果返回空壳
     */
    public LoadInpatientSettleSummaryResponse toSummaryResponse(InpatientSettleSummaryResult result) {
        LoadInpatientSettleSummaryResponse response = new LoadInpatientSettleSummaryResponse();
        if (result == null) {
            return response;
        }
        response.setEncounterId(result.getEncounterId());
        response.setPatientId(result.getPatientId());
        response.setEncounterBusNo(result.getEncounterBusNo());
        response.setPatientName(result.getPatientName());
        response.setOrganizationName(result.getOrganizationName());
        response.setWardName(result.getWardName());
        response.setBedName(result.getBedName());
        response.setStatusEnum(result.getStatusEnum());
        response.setStatusEnumText(result.getStatusEnumText());
        response.setPendingSettlementTotal(result.getPendingSettlementTotal());
        response.setPrepayNetBalance(result.getPrepayNetBalance());
        response.setSelectedTotal(result.getSelectedTotal());
        response.setBalanceHint(result.getBalanceHint());
        response.setPersonalCashPayable(result.getPersonalCashPayable());
        response.setDepositOffsetAmount(result.getDepositOffsetAmount());
        response.setChannelPayable(result.getChannelPayable());
        response.setPrepayRefundAmount(result.getPrepayRefundAmount());
        response.setPrepayFullRefund(result.getPrepayFullRefund());
        response.setDepositSettleRuleCode(result.getDepositSettleRuleCode());
        response.setDisplayAccountBalance(result.getDisplayAccountBalance());
        response.setAccountBalanceDisplayMode(result.getAccountBalanceDisplayMode());
        response.setAvailableBalance(result.getAvailableBalance());
        response.setAccountBalanceSummaryText(result.getAccountBalanceSummaryText());
        response.setAccountBalanceSubText(result.getAccountBalanceSubText());
        response.setLowBalanceWarning(result.getLowBalanceWarning());
        return response;
    }

    /**
     * 费用查询 Request → 读 Query。
     *
     * @param request HTTP 请求；可空
     * @return 费用查询；空请求返回空 Query
     */
    public InpatientSettleChargesQuery toChargesQuery(QueryInpatientSettleChargesRequest request) {
        if (request == null) {
            return new InpatientSettleChargesQuery();
        }
        return new InpatientSettleChargesQuery()
            .setEncounterId(request.getEncounterId())
            .setPatientId(request.getPatientId())
            .setPresentationMode(request.getPresentationMode())
            .setChargeDateFrom(request.getChargeDateFrom())
            .setChargeDateTo(request.getChargeDateTo());
    }

    /**
     * 费用读结果 → HTTP Response。
     *
     * @param result 读侧结果；可空
     * @return 费用响应；空结果返回空壳
     */
    public QueryInpatientSettleChargesResponse toChargesResponse(InpatientSettleChargesResult result) {
        QueryInpatientSettleChargesResponse response = new QueryInpatientSettleChargesResponse();
        if (result == null) {
            return response;
        }
        response.setEncounterId(result.getEncounterId());
        response.setPresentationMode(result.getPresentationMode());
        response.setLines(result.getLines());
        response.setCategories(result.getCategories());
        response.setPendingSettlementTotal(result.getPendingSettlementTotal());
        response.setDefaultSelectAll(result.getDefaultSelectAll());
        return response;
    }

    /**
     * 已结算查阅 Request → 读 Query。
     *
     * @param request HTTP 请求；可空
     * @return 查阅查询；空请求返回空 Query
     */
    public SettledInpatientPaymentViewQuery toPaymentViewQuery(LoadSettledInpatientPaymentViewRequest request) {
        if (request == null) {
            return new SettledInpatientPaymentViewQuery();
        }
        return new SettledInpatientPaymentViewQuery()
            .setEncounterId(request.getEncounterId())
            .setPatientId(request.getPatientId())
            .setPaymentId(request.getPaymentId())
            .setPresentationMode(request.getPresentationMode());
    }

    /**
     * 已结算查阅读结果 → HTTP Response（含 netSettleTotal）。
     *
     * @param result 读侧结果；可空
     * @return 查阅响应；空结果返回空壳
     */
    public LoadSettledInpatientPaymentViewResponse toPaymentViewResponse(SettledInpatientPaymentViewResult result) {
        LoadSettledInpatientPaymentViewResponse response = new LoadSettledInpatientPaymentViewResponse();
        if (result == null) {
            return response;
        }
        response.setEncounterId(result.getEncounterId());
        response.setPayments(result.getPayments());
        response.setActivePaymentId(result.getActivePaymentId());
        response.setSettleTime(result.getSettleTime());
        response.setOperatorName(result.getOperatorName());
        response.setReceivableTotal(result.getReceivableTotal());
        response.setPayMethodText(result.getPayMethodText());
        response.setLines(result.getLines());
        response.setCategories(result.getCategories());
        // Asm：历史 signedAmount 代数和（一正一负抵净）。
        response.setNetSettleTotal(result.getNetSettleTotal());
        return response;
    }
}
