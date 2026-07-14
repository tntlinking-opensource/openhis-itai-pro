/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.reconciliation.mapper;

import com.openhis.chs.application.reconciliation.response.ListLocalClearAppliesResponse;
import com.openhis.chs.application.reconciliation.response.ListLocalReconDailyResponse;
import com.openhis.chs.application.reconciliation.request.ListLocalReconRecordsRequest;
import com.openhis.chs.application.reconciliation.response.ListLocalReconRecordsResponse;
import com.openhis.chs.application.reconciliation.response.LocalClearApplyResponse;
import com.openhis.chs.application.reconciliation.response.LocalReconDailyResponse;
import com.openhis.chs.application.reconciliation.response.LocalReconDetailResponse;
import com.openhis.chs.domain.reconciliation.command.ListLocalReconRecordsCommand;
import com.openhis.chs.domain.reconciliation.result.LocalClearApplyResult;
import com.openhis.chs.domain.reconciliation.result.LocalReconDailyResult;
import com.openhis.chs.domain.reconciliation.result.LocalReconDetailResult;

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
    public static ListLocalReconRecordsResponse toResponse(List<LocalReconDetailResult> rows) {
        ListLocalReconRecordsResponse response = new ListLocalReconRecordsResponse();
        if (rows == null) {
            return response;
        }
        for (LocalReconDetailResult row : rows) {
            if (row == null) {
                continue;
            }
            response.getDetails().add(new LocalReconDetailResponse()
                .setContractBusNo(row.getContractBusNo())
                .setStmtBegndate(row.getStmtBegndate())
                .setStmtEnddate(row.getStmtEnddate())
                .setUploadFilename(row.getUploadFileQuryNo())
                .setRowCount(row.getRowCount())
                .setMismatchCount(row.getMismatchCount()));
        }
        return response;
    }

    /** 将清算申请领域结果列表映射为 UI 出参。 */
    public static ListLocalClearAppliesResponse toClearApplyResponse(List<LocalClearApplyResult> rows) {
        ListLocalClearAppliesResponse response = new ListLocalClearAppliesResponse();
        if (rows == null) {
            return response;
        }
        for (LocalClearApplyResult row : rows) {
            if (row == null) {
                continue;
            }
            response.getRows().add(new LocalClearApplyResponse()
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
        return response;
    }

    /** 将日对账领域结果列表映射为 UI 出参。 */
    public static ListLocalReconDailyResponse toReconDailyResponse(List<LocalReconDailyResult> rows) {
        ListLocalReconDailyResponse response = new ListLocalReconDailyResponse();
        if (rows == null) {
            return response;
        }
        for (LocalReconDailyResult row : rows) {
            if (row == null) {
                continue;
            }
            response.getRows().add(new LocalReconDailyResponse()
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
        return response;
    }
}
