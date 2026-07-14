/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.mapper;

import com.openhis.health.application.inpatient.financial.settle.request.LoadInpatientSettleSummaryRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadSettledInpatientPaymentViewRequest;
import com.openhis.health.application.inpatient.financial.settle.request.QueryInpatientSettleChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.response.LoadInpatientSettleSummaryResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadSettledInpatientPaymentViewResponse;
import com.openhis.health.application.inpatient.financial.settle.response.QueryInpatientSettleChargesResponse;
import com.openhis.health.application.inpatient.financial.settle.support.InpatientSettleChargesQuery;
import com.openhis.health.application.inpatient.financial.settle.support.InpatientSettleChargesResult;
import com.openhis.health.application.inpatient.financial.settle.support.InpatientSettleSummaryQuery;
import com.openhis.health.application.inpatient.financial.settle.support.InpatientSettleSummaryResult;
import com.openhis.health.application.inpatient.financial.settle.support.SettledInpatientPaymentViewQuery;
import com.openhis.health.application.inpatient.financial.settle.support.SettledInpatientPaymentViewResult;
import org.springframework.stereotype.Component;

/** 住院结算读路径 Query/Result 与 HTTP Request/Response 映射。 */
@Component
public class InpatientSettleReadMapper {

    public InpatientSettleSummaryQuery toSummaryQuery(LoadInpatientSettleSummaryRequest request) {
        if (request == null) {
            return new InpatientSettleSummaryQuery();
        }
        return new InpatientSettleSummaryQuery()
            .setEncounterId(request.getEncounterId())
            .setPatientId(request.getPatientId())
            .setSelectedChargeItemIds(request.getSelectedChargeItemIds());
    }

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
        response.setDisplayAccountBalance(result.getDisplayAccountBalance());
        response.setAccountBalanceDisplayMode(result.getAccountBalanceDisplayMode());
        response.setAvailableBalance(result.getAvailableBalance());
        response.setAccountBalanceSummaryText(result.getAccountBalanceSummaryText());
        response.setAccountBalanceSubText(result.getAccountBalanceSubText());
        response.setLowBalanceWarning(result.getLowBalanceWarning());
        return response;
    }

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
