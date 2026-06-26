/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy;

import com.openhis.chs.domain.reconciliation.dto.request.ApplyJilinClear3203ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryClearOrg3206ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryClearStatus3205ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.ReconDaily3201Request;
import com.openhis.chs.domain.reconciliation.dto.request.ReconDetail3202Request;
import com.openhis.chs.domain.reconciliation.dto.request.QueryCrossProvinceRecon3209ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.RevokeJilinClear3204ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.SummarizeClearAmountRequest;
import com.openhis.chs.domain.reconciliation.dto.response.ApplyJilinClear3203AResult;
import com.openhis.chs.domain.reconciliation.dto.response.ClearAmountSummaryDto;
import com.openhis.chs.domain.reconciliation.dto.response.ClearOrg3206AResult;
import com.openhis.chs.domain.reconciliation.dto.response.ClearStatus3205ARowDto;
import com.openhis.chs.domain.reconciliation.dto.response.CrossProvinceReconRowDto;
import com.openhis.chs.domain.reconciliation.dto.response.LocalClearApplyDto;
import com.openhis.chs.domain.reconciliation.dto.response.LocalReconDailyDto;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDaily3201Result;
import com.openhis.chs.domain.reconciliation.dto.response.LocalReconDetailDto;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetail3202Result;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetailRowDto;
import com.openhis.chs.domain.reconciliation.facade.ChsReconciliationOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import com.openhis.chs.domain.reconciliation.dto.request.ListLocalReconRecordsRequest;

/** 对账清算 bridge：{@code /api/app/insuranceReconciliation/*}。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceReconciliationAppService {

    private final ChsReconciliationOperations reconciliationOperations;

    /**
     * 【3206A】查询清算机构列表。
     *
     * @param request 分页与筛选条件
     * @return 清算机构分页结果
     */
    public ClearOrg3206AResult queryClearOrganizations(QueryClearOrg3206ARequest request) {
        // Cmd：委托 facade
        return reconciliationOperations.queryClearOrganizations(request);
    }

    /**
     * 汇总区间内清算金额（本地结算汇总）。
     *
     * @param request 区间与筛选条件
     * @return 金额汇总 DTO
     */
    public ClearAmountSummaryDto summarizeClearAmounts(SummarizeClearAmountRequest request) {
        // Cmd：委托 facade
        return reconciliationOperations.summarizeClearAmounts(request);
    }

    /**
     * 【3203A】申请吉林清算。
     *
     * @param request 清算申请参数
     * @return 申请结果
     */
    public ApplyJilinClear3203AResult applyJilinClear(ApplyJilinClear3203ARequest request) {
        // Cmd：委托 facade
        return reconciliationOperations.applyJilinClear(request);
    }

    /**
     * 【3205A】查询清算申请状态。
     *
     * @param request 清算机构与状态筛选
     * @return 清算状态行列表
     */
    public List<ClearStatus3205ARowDto> queryClearApplyStatus(QueryClearStatus3205ARequest request) {
        // Cmd：委托 facade
        return reconciliationOperations.queryClearApplyStatus(request);
    }

    /**
     * 【3204A】撤销吉林清算申请。
     *
     * @param request 撤销参数
     */
    public void revokeJilinClear(RevokeJilinClear3204ARequest request) {
        // Cmd：委托 facade
        reconciliationOperations.revokeJilinClear(request);
    }

    /**
     * 【3201】日对账。
     *
     * @param request 对账区间与金额
     * @return 对账结果
     */
    public ReconDaily3201Result reconcileDailyLedger(ReconDaily3201Request request) {
        // Cmd：委托 facade
        return reconciliationOperations.reconcileDailyLedger(request);
    }

    /**
     * 预览对账明细行（本地结算汇总，不上传平台）。
     *
     * @param request 区间与筛选条件
     * @return 明细行列表
     */
    public List<ReconDetailRowDto> previewReconDetailRows(SummarizeClearAmountRequest request) {
        // Cmd：委托 facade
        return reconciliationOperations.previewReconDetailRows(request);
    }

    /**
     * 【3202】明细对账。
     *
     * @param request 对账区间与文件查询号
     * @return 对账结果含差异行
     */
    public ReconDetail3202Result reconcileDetailLedger(ReconDetail3202Request request) {
        // Cmd：委托 facade
        return reconciliationOperations.reconcileDetailLedger(request);
    }

    /**
     * 查询本地明细对账记录。
     *
     * @param request 分页与筛选条件
     * @return 本地明细对账记录列表
     */
    public List<LocalReconDetailDto> listLocalReconDetailRecords(ListLocalReconRecordsRequest request) {
        // Cmd：委托 facade
        return reconciliationOperations.listLocalReconDetailRecords(request);
    }

    /**
     * 查询本地清算申请记录。
     *
     * @param request 分页与筛选条件
     * @return 本地清算申请记录列表
     */
    public List<LocalClearApplyDto> listLocalClearApplies(ListLocalReconRecordsRequest request) {
        // Cmd：委托 facade
        return reconciliationOperations.listLocalClearApplies(request);
    }

    /**
     * 查询本地日对账记录。
     *
     * @param request 分页与筛选条件
     * @return 本地日对账记录列表
     */
    public List<LocalReconDailyDto> listLocalReconDailyRecords(ListLocalReconRecordsRequest request) {
        // Cmd：委托 facade
        return reconciliationOperations.listLocalReconDailyRecords(request);
    }

    /**
     * 【3209A】查询跨省对账失败记录。
     *
     * @param request 区间与清算类型
     * @return 跨省对账失败行列表
     */
    public List<CrossProvinceReconRowDto> queryCrossProvinceReconFailures(
            QueryCrossProvinceRecon3209ARequest request) {
        // Cmd：委托 facade
        return reconciliationOperations.queryCrossProvinceReconFailures(request);
    }
}
