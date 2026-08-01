/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.reconciliation.mapper;

import com.openhis.chs.application.reconciliation.request.ListLocalReconRecordsRequest;
import com.openhis.chs.application.reconciliation.response.LocalClearApplyResponse;
import com.openhis.chs.application.reconciliation.response.LocalReconDailyResponse;
import com.openhis.chs.application.reconciliation.response.LocalReconDetailResponse;
import com.openhis.chs.domain.reconciliation.command.ListLocalReconRecordsCommand;
import com.openhis.chs.domain.reconciliation.result.LocalClearApplyResult;
import com.openhis.chs.domain.reconciliation.result.LocalReconDailyResult;
import com.openhis.chs.domain.reconciliation.result.LocalReconDetailResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 对账清算中心：UI 入参与领域 Command/Result 映射。
 */
public final class InsuranceReconciliationAppMapper {

    private InsuranceReconciliationAppMapper() {
    }

    /** 将本地流水查询入参转为领域 {@link ListLocalReconRecordsCommand}。 */
    public static ListLocalReconRecordsCommand toCommand(ListLocalReconRecordsRequest request) {
        ListLocalReconRecordsRequest safe = request == null ? new ListLocalReconRecordsRequest() : request;
        return new ListLocalReconRecordsCommand()
            .setContractBusNo(safe.getContractBusNo())
            .setMonthFilter(safe.getMonthFilter());
    }

    /** 将明细对账领域结果列表映射为 UI 出参。 */
    public static List<LocalReconDetailResponse> toDetailResponse(List<LocalReconDetailResult> rows) {
        List<LocalReconDetailResponse> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (LocalReconDetailResult row : rows) {
            if (row == null) {
                continue;
            }
            result.add(new LocalReconDetailResponse()
                .setId(row.getId())
                .setContractBusNo(row.getContractBusNo())
                .setSetlOptins(row.getSetlOptins())
                .setClrType(row.getClrType())
                .setStmtBegndate(row.getStmtBegndate())
                .setStmtEnddate(row.getStmtEnddate())
                .setRowCount(row.getRowCount())
                .setMismatchCount(row.getMismatchCount())
                .setUploadFileQuryNo(row.getUploadFileQuryNo())
                .setResultFileQuryNo(row.getResultFileQuryNo())
                .setCreatedAt(row.getCreatedAt()));
        }
        return result;
    }

    /** 将清算申请领域结果列表映射为 UI 出参。 */
    public static List<LocalClearApplyResponse> toClearApplyResponse(List<LocalClearApplyResult> rows) {
        List<LocalClearApplyResponse> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (LocalClearApplyResult row : rows) {
            if (row == null) {
                continue;
            }
            result.add(new LocalClearApplyResponse()
                .setId(row.getId())
                .setContractBusNo(row.getContractBusNo())
                .setClrType(row.getClrType())
                .setClrOptins(row.getClrOptins())
                .setBegndate(row.getBegndate())
                .setEnddate(row.getEnddate())
                .setMedfeeSumamt(row.getMedfeeSumamt())
                .setFundAppySum(row.getFundAppySum())
                .setAcctPay(row.getAcctPay())
                .setCashPayamt(row.getCashPayamt())
                .setClrAppyEvtId(row.getClrAppyEvtId())
                .setClrStas(row.getClrStas())
                .setStatus(row.getStatus())
                .setCreatedAt(row.getCreatedAt()));
        }
        return result;
    }

    /** 将日对账领域结果列表映射为 UI 出参。 */
    public static List<LocalReconDailyResponse> toReconDailyResponse(List<LocalReconDailyResult> rows) {
        List<LocalReconDailyResponse> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (LocalReconDailyResult row : rows) {
            if (row == null) {
                continue;
            }
            result.add(new LocalReconDailyResponse()
                .setId(row.getId())
                .setContractBusNo(row.getContractBusNo())
                .setInsutype(row.getInsutype())
                .setClrType(row.getClrType())
                .setSetlOptins(row.getSetlOptins())
                .setStmtBegndate(row.getStmtBegndate())
                .setStmtEnddate(row.getStmtEnddate())
                .setMedfeeSumamt(row.getMedfeeSumamt())
                .setFundPaySumamt(row.getFundPaySumamt())
                .setAcctPay(row.getAcctPay())
                .setFixmedinsSetlCnt(row.getFixmedinsSetlCnt())
                .setStmtRslt(row.getStmtRslt())
                .setStmtRsltDscr(row.getStmtRsltDscr())
                .setCreatedAt(row.getCreatedAt()));
        }
        return result;
    }
}
