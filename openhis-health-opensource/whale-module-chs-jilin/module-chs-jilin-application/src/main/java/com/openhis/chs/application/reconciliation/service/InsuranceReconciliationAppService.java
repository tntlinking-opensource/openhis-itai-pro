/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.reconciliation.service;

import com.openhis.chs.application.reconciliation.response.ListLocalClearAppliesResponse;
import com.openhis.chs.application.reconciliation.response.ListLocalReconDailyResponse;
import com.openhis.chs.application.reconciliation.request.ListLocalReconRecordsRequest;
import com.openhis.chs.application.reconciliation.response.ListLocalReconRecordsResponse;
import com.openhis.chs.application.reconciliation.mapper.InsuranceReconciliationAppMapper;
import com.openhis.chs.domain.reconciliation.access.Recon3201Invoker;
import com.openhis.chs.domain.reconciliation.access.Recon3202DetailInvoker;
import com.openhis.chs.domain.reconciliation.access.Recon3203AInvoker;
import com.openhis.chs.domain.reconciliation.access.Recon3204AInvoker;
import com.openhis.chs.domain.reconciliation.access.Recon3205AInvoker;
import com.openhis.chs.domain.reconciliation.access.Recon3206AInvoker;
import com.openhis.chs.domain.reconciliation.access.Recon3209AInvoker;
import com.openhis.chs.domain.reconciliation.command.ListLocalReconRecordsCommand;
import com.openhis.chs.domain.reconciliation.command.ApplyJilinClear3203ACommand;
import com.openhis.chs.domain.reconciliation.command.QueryClearOrg3206ACommand;
import com.openhis.chs.domain.reconciliation.command.QueryClearStatus3205ACommand;
import com.openhis.chs.domain.reconciliation.command.QueryCrossProvinceRecon3209ACommand;
import com.openhis.chs.domain.reconciliation.command.ReconDaily3201Command;
import com.openhis.chs.domain.reconciliation.command.ReconDetail3202Command;
import com.openhis.chs.domain.reconciliation.command.RevokeJilinClear3204ACommand;
import com.openhis.chs.domain.reconciliation.command.SummarizeClearAmountCommand;
import com.openhis.chs.domain.reconciliation.result.ApplyJilinClear3203AResult;
import com.openhis.chs.domain.reconciliation.result.ClearAmountSummaryResult;
import com.openhis.chs.domain.reconciliation.result.ClearOrg3206AResult;
import com.openhis.chs.domain.reconciliation.result.ClearStatus3205ARowResult;
import com.openhis.chs.domain.reconciliation.result.CrossProvinceReconRowResult;
import com.openhis.chs.domain.reconciliation.result.LocalClearApplyResult;
import com.openhis.chs.domain.reconciliation.result.LocalReconDailyResult;
import com.openhis.chs.domain.reconciliation.result.LocalReconDetailResult;
import com.openhis.chs.domain.reconciliation.result.ReconDaily3201Result;
import com.openhis.chs.domain.reconciliation.result.ReconDetail3202Result;
import com.openhis.chs.domain.reconciliation.result.ReconDetailRowResult;
import com.openhis.chs.domain.reconciliation.service.YbReconciliationDomainService;
import com.openhis.chs.domain.reconciliation.service.YbReconciliationQueryService;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对账清算中心 UI（{@code POST /api/app/insuranceReconciliation/*}）。
 * <p>Tab 分组：日对账 3201/3202、月清算 3203A–3206A、本地流水查阅、跨省 3209A。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceReconciliationAppService {

    private final YbReconciliationDomainService reconciliationDomainService;
    private final YbReconciliationQueryService reconciliationQueryService;
    private final Recon3206AInvoker recon3206AInvoker;
    private final Recon3203AInvoker recon3203AInvoker;
    private final Recon3205AInvoker recon3205AInvoker;
    private final Recon3204AInvoker recon3204AInvoker;
    private final Recon3201Invoker recon3201Invoker;
    private final Recon3202DetailInvoker recon3202DetailInvoker;
    private final Recon3209AInvoker recon3209AInvoker;

    /** 【3206A】清算经办机构分页查询。 */
    public ClearOrg3206AResult queryClearOrganizations(QueryClearOrg3206ACommand request) {
        return recon3206AInvoker.invoke(request);
    }

    /** 本地查询：汇总清算相关金额。 */
    public ClearAmountSummaryResult summarizeClearAmounts(SummarizeClearAmountCommand request) {
        return reconciliationQueryService.summarizeClearAmounts(request, TenantUtils.getCurrentTenantId());
    }

    /** 【3203A】吉林清算申请提交。 */
    public ApplyJilinClear3203AResult applyJilinClear(ApplyJilinClear3203ACommand request) {
        return recon3203AInvoker.invoke(request);
    }

    /** 【3205A】清算申请状态查询。 */
    public List<ClearStatus3205ARowResult> queryClearApplyStatus(QueryClearStatus3205ACommand request) {
        return recon3205AInvoker.invoke(request);
    }

    /** 【3204A】吉林清算申请撤销。 */
    public void revokeJilinClear(RevokeJilinClear3204ACommand request) {
        recon3204AInvoker.invoke(request);
    }

    /** 【3201】日对账。 */
    public ReconDaily3201Result reconcileDailyLedger(ReconDaily3201Command request) {
        return recon3201Invoker.invoke(request);
    }

    /** 本地查询：预览明细对账行（本地汇总）。 */
    public List<ReconDetailRowResult> previewReconDetailRows(SummarizeClearAmountCommand request) {
        return reconciliationQueryService.loadDetailRows(request, TenantUtils.getCurrentTenantId());
    }

    /** 【3202】明细对账（9101+3202+9102）。 */
    public ReconDetail3202Result reconcileDetailLedger(ReconDetail3202Command request) {
        return recon3202DetailInvoker.invoke(request);
    }

    /** 本地查询：明细对账流水列表。 */
    public ListLocalReconRecordsResponse listLocalReconDetail(ListLocalReconRecordsRequest request) {
        ListLocalReconRecordsCommand command = InsuranceReconciliationAppMapper.toCommand(request);
        List<LocalReconDetailResult> rows = reconciliationDomainService.listLocalReconDetailRecords(
            command, TenantUtils.getCurrentTenantId());
        return InsuranceReconciliationAppMapper.toResponse(rows);
    }

    /** 本地查询：清算申请流水列表。 */
    public ListLocalClearAppliesResponse listLocalClearApplies(ListLocalReconRecordsRequest request) {
        ListLocalReconRecordsCommand command = InsuranceReconciliationAppMapper.toCommand(request);
        List<LocalClearApplyResult> rows = reconciliationDomainService.listLocalClearApplies(
            command, TenantUtils.getCurrentTenantId());
        return InsuranceReconciliationAppMapper.toClearApplyResponse(rows);
    }

    /** 本地查询：日对账流水列表。 */
    public ListLocalReconDailyResponse listLocalReconDaily(ListLocalReconRecordsRequest request) {
        ListLocalReconRecordsCommand command = InsuranceReconciliationAppMapper.toCommand(request);
        List<LocalReconDailyResult> rows = reconciliationDomainService.listLocalReconDailyRecords(
            command, TenantUtils.getCurrentTenantId());
        return InsuranceReconciliationAppMapper.toReconDailyResponse(rows);
    }

    /** 【3209A】跨省异地对账失败查询。 */
    public List<CrossProvinceReconRowResult> queryCrossProvinceReconFailures(
            QueryCrossProvinceRecon3209ACommand request) {
        return recon3209AInvoker.invoke(request);
    }
}
