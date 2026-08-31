/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.service;

import com.openhis.chs.application.notice.dto.NoticeScanImportResultDto;
import com.openhis.chs.application.notice.mapper.InsuranceCatalogNoticeMapper;
import com.openhis.chs.application.notice.request.ApplyNoticeDocRequest;
import com.openhis.chs.application.notice.request.CheckCatalogIssuesRequest;
import com.openhis.chs.application.notice.request.CloseNoticeTodoRequest;
import com.openhis.chs.application.notice.request.ConfirmNoticeScanImportRequest;
import com.openhis.chs.application.notice.request.DownloadNoticePackRequest;
import com.openhis.chs.application.notice.request.GetComplianceReportRequest;
import com.openhis.chs.application.notice.request.GetConfirmNoticeScanImportProgressRequest;
import com.openhis.chs.application.notice.request.GetScanNoticeCanonicalDirProgressRequest;
import com.openhis.chs.application.notice.request.PageNoticeDocRequest;
import com.openhis.chs.application.notice.request.PageNoticeLineRequest;
import com.openhis.chs.application.notice.request.PageNoticeTodoRequest;
import com.openhis.chs.application.notice.request.PreviewNoticeApplyRequest;
import com.openhis.chs.application.notice.request.RerunNoticeComplianceRequest;
import com.openhis.chs.application.notice.request.ResumeConfirmNoticeScanImportRequest;
import com.openhis.chs.application.notice.request.ScanNoticeCanonicalDirRequest;
import com.openhis.chs.application.notice.response.ApplyNoticeDocResponse;
import com.openhis.chs.application.notice.response.CheckCatalogIssuesResponse;
import com.openhis.chs.application.notice.response.CloseNoticeTodoResponse;
import com.openhis.chs.application.notice.response.ConfirmNoticeScanImportResponse;
import com.openhis.chs.application.notice.response.DownloadNoticePackResponse;
import com.openhis.chs.application.notice.response.GetComplianceReportResponse;
import com.openhis.chs.application.notice.response.GetConfirmNoticeScanImportProgressResponse;
import com.openhis.chs.application.notice.response.GetScanNoticeCanonicalDirProgressResponse;
import com.openhis.chs.application.notice.response.PageNoticeDocResponse;
import com.openhis.chs.application.notice.response.PageNoticeLineResponse;
import com.openhis.chs.application.notice.response.PageNoticeTodoResponse;
import com.openhis.chs.application.notice.response.PreviewNoticeApplyResponse;
import com.openhis.chs.application.notice.response.RerunNoticeComplianceResponse;
import com.openhis.chs.application.notice.response.ScanNoticeCanonicalDirResponse;
import com.openhis.chs.application.notice.response.StartConfirmNoticeScanImportResponse;
import com.openhis.chs.application.notice.response.StartScanNoticeCanonicalDirResponse;
import com.openhis.chs.application.notice.request.GetApplyNoticeDocProgressRequest;
import com.openhis.chs.application.notice.request.StartApplyNoticeDocRequest;
import com.openhis.chs.application.notice.request.StartApplyNoticeDocsRequest;
import com.openhis.chs.application.notice.response.GetApplyNoticeDocProgressResponse;
import com.openhis.chs.application.notice.response.StartApplyNoticeDocResponse;
import com.openhis.chs.application.notice.support.NoticeApplyWorkflow;
import com.openhis.chs.application.notice.support.NoticeComplianceWorkflow;
import com.openhis.chs.application.notice.support.NoticeImportWorkflow;
import com.openhis.chs.application.notice.support.NoticeScanWorkflow;
import com.openhis.chs.application.notice.support.dto.NoticeApplyJob;
import com.openhis.chs.application.notice.support.dto.NoticeImportJob;
import com.openhis.chs.application.notice.support.dto.NoticeScanCachedItem;
import com.openhis.chs.application.notice.support.dto.NoticeScanJob;
import com.openhis.chs.application.notice.support.dto.NoticeScanSession;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.notice.command.ApplyNoticeDocCommand;
import com.openhis.chs.domain.notice.command.CloseNoticeTodoCommand;
import com.openhis.chs.domain.notice.command.RunComplianceCommand;
import com.openhis.chs.domain.notice.model.ApplyNoticeDocResult;
import com.openhis.chs.domain.notice.model.ApplyPreviewView;
import com.openhis.chs.domain.notice.model.CatalogCheckResult;
import com.openhis.chs.domain.notice.model.CloseNoticeTodoResult;
import com.openhis.chs.domain.notice.model.ComplianceReportView;
import com.openhis.chs.domain.notice.model.GetComplianceReportQuery;
import com.openhis.chs.domain.notice.model.NoticeDocView;
import com.openhis.chs.domain.notice.model.NoticeLineView;
import com.openhis.chs.domain.notice.model.NoticePageResult;
import com.openhis.chs.domain.notice.model.NoticeTodoSummaryView;
import com.openhis.chs.domain.notice.model.NoticeTodoView;
import com.openhis.chs.domain.notice.model.PageNoticeDocQuery;
import com.openhis.chs.domain.notice.model.PageNoticeLineQuery;
import com.openhis.chs.domain.notice.model.PageNoticeTodoQuery;
import com.openhis.chs.domain.notice.model.PreviewApplyQuery;
import com.openhis.chs.domain.notice.model.RunComplianceResult;
import com.openhis.chs.domain.notice.port.CatalogNoticePackStorePort;
import com.openhis.chs.domain.notice.queryservice.CatalogCheckQueryService;
import com.openhis.chs.domain.notice.queryservice.CatalogNoticeApplyPreviewQueryService;
import com.openhis.chs.domain.notice.queryservice.CatalogNoticeComplianceQueryService;
import com.openhis.chs.domain.notice.queryservice.CatalogNoticeDocQueryService;
import com.openhis.chs.domain.notice.queryservice.CatalogNoticeLineQueryService;
import com.openhis.chs.domain.notice.queryservice.CatalogNoticeTodoQueryService;
import com.openhis.chs.domain.notice.service.CatalogNoticeApplyDomainService;
import com.openhis.chs.domain.notice.service.CatalogNoticeTodoDomainService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 医保文件管理应用服务（POST /api/app/insuranceCatalogNotice/*）。
 * <p>对应 AS-CHS-CAT-005；覆盖 W-01~W-05 与 R-01~R-06。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceCatalogNoticeAppService {

    private static final String RESULT_IMPORTED = "IMPORTED";
    private static final String RESULT_SKIPPED = "SKIPPED";

    private final NoticeScanWorkflow noticeScanWorkflow;
    private final NoticeImportWorkflow noticeImportWorkflow;
    private final NoticeApplyWorkflow noticeApplyWorkflow;
    private final NoticeComplianceWorkflow noticeComplianceWorkflow;
    private final CatalogNoticeApplyDomainService catalogNoticeApplyDomainService;
    private final CatalogNoticeTodoDomainService catalogNoticeTodoDomainService;
    private final CatalogNoticeDocQueryService catalogNoticeDocQueryService;
    private final CatalogNoticeLineQueryService catalogNoticeLineQueryService;
    private final CatalogNoticeApplyPreviewQueryService catalogNoticeApplyPreviewQueryService;
    private final CatalogNoticeComplianceQueryService catalogNoticeComplianceQueryService;
    private final CatalogNoticeTodoQueryService catalogNoticeTodoQueryService;
    private final CatalogCheckQueryService catalogCheckQueryService;
    private final CatalogNoticePackStorePort catalogNoticePackStorePort;
    private final InsuranceCatalogNoticeMapper insuranceCatalogNoticeMapper;
    private final ChsInsuranceContractReader chsInsuranceContractReader;

    /**
     * 扫描配置目录下标准落地包（IF-CHS-CAT-005-W-01）。
     * 不落库；返回 scanToken 供二次确认入库。
     *
     * @param request 可选 contractId / scanRoot
     * @return 扫描令牌、计数与明细
     */
    public ScanNoticeCanonicalDirResponse scanNoticeCanonicalDir(ScanNoticeCanonicalDirRequest request) {
        // Gate：请求可空；根目录空时由 resolveCanonicalRoot 校验配置
        ScanNoticeCanonicalDirRequest safe = request;
        if (safe == null) {
            safe = new ScanNoticeCanonicalDirRequest();
        }

        // Pre：扫描一级子目录并判定 NEW/DUPLICATE/FAILED
        NoticeScanSession session = noticeScanWorkflow.scanCanonicalDir(
            safe.getScanRoot(), safe.getContractId());

        // Cmd：本接口不落库，仅内存缓存 scanToken
        // Asm：装配扫描响应
        return insuranceCatalogNoticeMapper.toScanResponse(session);
    }

    /**
     * 启动异步扫描目录（带回进度，供 UI 进度条）。
     *
     * @param request 可选 contractId / scanRoot
     * @return jobId
     */
    public StartScanNoticeCanonicalDirResponse startScanNoticeCanonicalDir(
            ScanNoticeCanonicalDirRequest request) {
        // Gate：请求可空
        ScanNoticeCanonicalDirRequest safe = request;
        if (safe == null) {
            safe = new ScanNoticeCanonicalDirRequest();
        }

        // Pre：校验根目录并提交异步任务
        String jobId = noticeScanWorkflow.startAsyncScan(safe.getScanRoot(), safe.getContractId());

        // Asm：返回任务 ID
        StartScanNoticeCanonicalDirResponse response = new StartScanNoticeCanonicalDirResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 轮询异步扫描进度；SUCCEEDED 时附带完整扫描结果。
     *
     * @param request 含 jobId
     * @return 进度与可选结果
     */
    public GetScanNoticeCanonicalDirProgressResponse getScanNoticeCanonicalDirProgress(
            GetScanNoticeCanonicalDirProgressRequest request) {
        // Gate：jobId 必填
        GetScanNoticeCanonicalDirProgressRequest safe = request;
        if (safe == null) {
            safe = new GetScanNoticeCanonicalDirProgressRequest();
        }
        if (safe.getJobId() == null || safe.getJobId().isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "jobId 不能为空");
        }

        // Pre：取任务快照
        NoticeScanJob job = noticeScanWorkflow.getScanJob(safe.getJobId());

        // Asm：进度百分比与成功时的扫描结果
        GetScanNoticeCanonicalDirProgressResponse response = new GetScanNoticeCanonicalDirProgressResponse();
        response.setJobId(job.getJobId());
        response.setStatus(job.getStatus());
        response.setTotal(job.getTotal());
        response.setProcessed(job.getProcessed());
        response.setCurrentPackDir(job.getCurrentPackDir());
        response.setMessage(job.getMessage());
        int percent = 0;
        if (job.getTotal() > 0) {
            percent = (int) Math.round(100.0 * job.getProcessed() / job.getTotal());
            if (percent > 100) {
                percent = 100;
            }
        } else if (NoticeScanJob.STATUS_SUCCEEDED.equals(job.getStatus())) {
            percent = 100;
        }
        if (NoticeScanJob.STATUS_SUCCEEDED.equals(job.getStatus())) {
            percent = 100;
        }
        response.setPercent(percent);
        if (NoticeScanJob.STATUS_SUCCEEDED.equals(job.getStatus()) && job.getSession() != null) {
            response.setResult(insuranceCatalogNoticeMapper.toScanResponse(job.getSession()));
        }
        return response;
    }

    /**
     * 确认扫描结果入库（IF-CHS-CAT-005-W-02）。
     * 二次确认后落标准包 URI + 文件头 + 明细；重复 hash 拒绝除非 force。
     *
     * @param request 含 scanToken、可选 packDirs / force
     * @return 导入/跳过/失败计数与逐包结果
     */
    public ConfirmNoticeScanImportResponse confirmNoticeScanImport(ConfirmNoticeScanImportRequest request) {
        // Gate：扫描令牌必填，过期在 loadScan 抛 NOTICE_PREVIEW_EXPIRED
        ConfirmNoticeScanImportRequest safe = request;
        if (safe == null) {
            safe = new ConfirmNoticeScanImportRequest();
        }
        if (safe.getScanToken() == null || safe.getScanToken().isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PREVIEW_EXPIRED, "扫描令牌不能为空，请重新扫描");
        }

        // Pre：物化扫描会话与待入库目标
        NoticeScanSession session = noticeScanWorkflow.loadScan(safe.getScanToken());
        boolean force = false;
        if (safe.getForce() != null && safe.getForce()) {
            force = true;
        }
        List<NoticeScanCachedItem> targets = noticeScanWorkflow.resolveImportTargets(
            session, safe.getPackDirs(), force);

        ConfirmNoticeScanImportResponse response = new ConfirmNoticeScanImportResponse();
        Long contractId = safe.getContractId();
        if (contractId == null) {
            contractId = session.getContractId();
        }

        // Cmd：逐包落库（PackStore → Doc → Lines）；扫描轻量缓存按盘再物化
        Long tenantId = TenantUtils.getCurrentTenantId();
        for (NoticeScanCachedItem item : targets) {
            NoticeScanImportResultDto row = noticeImportWorkflow.importOneScanItem(
                item, contractId, force, session.getScanRoot(), tenantId);
            response.getResults().add(row);
            if (RESULT_IMPORTED.equals(row.getStatus())) {
                response.setImportedCount(response.getImportedCount() + 1);
            } else if (RESULT_SKIPPED.equals(row.getStatus())) {
                response.setSkippedCount(response.getSkippedCount() + 1);
            } else {
                response.setFailedCount(response.getFailedCount() + 1);
            }
        }

        // Asm：计数已在循环中装填
        return response;
    }

    /**
     * 启动异步确认入库（带回进度，可续跑）。
     *
     * @param request 同同步确认入参
     * @return jobId
     */
    public StartConfirmNoticeScanImportResponse startConfirmNoticeScanImport(
            ConfirmNoticeScanImportRequest request) {
        ConfirmNoticeScanImportRequest safe = request;
        if (safe == null) {
            safe = new ConfirmNoticeScanImportRequest();
        }
        if (safe.getScanToken() == null || safe.getScanToken().isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PREVIEW_EXPIRED, "扫描令牌不能为空，请重新扫描");
        }
        boolean force = false;
        if (safe.getForce() != null && safe.getForce()) {
            force = true;
        }
        String jobId = noticeImportWorkflow.startImport(
            safe.getScanToken(), safe.getPackDirs(), safe.getContractId(), force);
        StartConfirmNoticeScanImportResponse response = new StartConfirmNoticeScanImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 轮询确认入库进度。
     *
     * @param request 含 jobId
     * @return 进度与是否可续跑
     */
    public GetConfirmNoticeScanImportProgressResponse getConfirmNoticeScanImportProgress(
            GetConfirmNoticeScanImportProgressRequest request) {
        GetConfirmNoticeScanImportProgressRequest safe = request;
        if (safe == null) {
            safe = new GetConfirmNoticeScanImportProgressRequest();
        }
        if (safe.getJobId() == null || safe.getJobId().isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "jobId 不能为空");
        }
        NoticeImportJob job = noticeImportWorkflow.getImportJob(safe.getJobId());
        return toImportProgressResponse(job);
    }

    /**
     * 续跑中断的入库任务（剩余包继续；已入库靠 hash 防重）。
     *
     * @param request 含 jobId
     * @return jobId
     */
    public StartConfirmNoticeScanImportResponse resumeConfirmNoticeScanImport(
            ResumeConfirmNoticeScanImportRequest request) {
        ResumeConfirmNoticeScanImportRequest safe = request;
        if (safe == null) {
            safe = new ResumeConfirmNoticeScanImportRequest();
        }
        if (safe.getJobId() == null || safe.getJobId().isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "jobId 不能为空");
        }
        String jobId = noticeImportWorkflow.resumeImport(safe.getJobId());
        StartConfirmNoticeScanImportResponse response = new StartConfirmNoticeScanImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 查询文件列表（IF-CHS-CAT-005-R-01）。
     *
     * @param request 分页与过滤
     * @return total + records
     */
    public PageNoticeDocResponse pageNoticeDocs(PageNoticeDocRequest request) {
        // Gate：补全分页缺省，避免空页码打爆
        PageNoticeDocRequest safe = request;
        if (safe == null) {
            safe = new PageNoticeDocRequest();
        }
        PageNoticeDocQuery query = new PageNoticeDocQuery();
        query.setTenantId(TenantUtils.getCurrentTenantId());
        int page = 1;
        if (safe.getPage() != null && safe.getPage() > 0) {
            page = safe.getPage();
        }
        int size = 20;
        if (safe.getSize() != null && safe.getSize() > 0) {
            size = safe.getSize();
        }
        query.setPage(page);
        query.setSize(size);
        query.setDocKind(safe.getDocKind());
        query.setCategory(safe.getCategory());
        query.setApplyStatus(safe.getApplyStatus());
        query.setKeyword(safe.getKeyword());

        // Pre：领域分页查询
        NoticePageResult<NoticeDocView> pageResult = catalogNoticeDocQueryService.pageDocs(query);
        // Cmd：本接口仅读，无写库
        // Asm：装配列表响应
        return insuranceCatalogNoticeMapper.toPageDocResponse(pageResult);
    }

    /**
     * 查询文件明细（IF-CHS-CAT-005-R-02）。
     *
     * @param request 含 docId 与分页过滤
     * @return total + records
     */
    public PageNoticeLineResponse pageNoticeLines(PageNoticeLineRequest request) {
        // Gate：docId 必填，否则无法定位明细所属文件
        PageNoticeLineRequest safe = request;
        if (safe == null) {
            safe = new PageNoticeLineRequest();
        }
        if (safe.getDocId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "docId 不能为空");
        }
        PageNoticeLineQuery query = new PageNoticeLineQuery();
        query.setTenantId(TenantUtils.getCurrentTenantId());
        query.setDocId(safe.getDocId());
        int page = 1;
        if (safe.getPage() != null && safe.getPage() > 0) {
            page = safe.getPage();
        }
        int size = 20;
        if (safe.getSize() != null && safe.getSize() > 0) {
            size = safe.getSize();
        }
        query.setPage(page);
        query.setSize(size);
        query.setAction(safe.getAction());
        query.setMatchStatus(safe.getMatchStatus());

        // Pre：领域明细分页
        NoticePageResult<NoticeLineView> pageResult = catalogNoticeLineQueryService.pageLines(query);
        // Cmd：本接口仅读，无写库
        // Asm：装配明细响应
        return insuranceCatalogNoticeMapper.toPageLineResponse(pageResult);
    }

    /**
     * 应用差异预览（IF-CHS-CAT-005-R-06）。
     * 不写库；估算 overlay / 回写 / 未收费同步。
     *
     * @param request 含 docId
     * @return 计数与样例
     */
    public PreviewNoticeApplyResponse previewNoticeApply(PreviewNoticeApplyRequest request) {
        // Gate：docId 必填
        PreviewNoticeApplyRequest safe = request;
        if (safe == null) {
            safe = new PreviewNoticeApplyRequest();
        }
        if (safe.getDocId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "docId 不能为空");
        }

        // Pre：领域只读估算
        PreviewApplyQuery query = new PreviewApplyQuery();
        query.setTenantId(TenantUtils.getCurrentTenantId());
        query.setDocId(safe.getDocId());
        ApplyPreviewView view = catalogNoticeApplyPreviewQueryService.previewApply(query);
        // Cmd：本接口仅读，无写库
        // Asm：装配预览响应
        return insuranceCatalogNoticeMapper.toPreviewApplyResponse(view);
    }

    /**
     * 应用文件（IF-CHS-CAT-005-W-03）。
     * 二次确认后写 overlay / map / 院内 lv / 未收费同步 / apply_log。
     *
     * @param request 含 docId、confirm（必须 true）
     * @return 应用计数
     */
    public ApplyNoticeDocResponse applyNoticeDoc(ApplyNoticeDocRequest request) {
        // Gate：confirm 必须为 true，否则拒绝应用
        ApplyNoticeDocRequest safe = request;
        if (safe == null) {
            safe = new ApplyNoticeDocRequest();
        }
        if (safe.getDocId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "docId 不能为空");
        }
        if (safe.getConfirm() == null || !safe.getConfirm()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_APPLY_DENIED, "请二次确认后再应用");
        }

        // Pre：校验文件存在
        catalogNoticeDocQueryService.getDocById(safe.getDocId(), TenantUtils.getCurrentTenantId());

        // Cmd：领域分批短事务应用
        ApplyNoticeDocCommand command = new ApplyNoticeDocCommand();
        command.setTenantId(TenantUtils.getCurrentTenantId());
        command.setDocId(safe.getDocId());
        command.setOperatorId(safe.getOperatorId());
        ApplyNoticeDocResult result = catalogNoticeApplyDomainService.applyDoc(command);

        // Asm：装配应用响应
        return insuranceCatalogNoticeMapper.toApplyResponse(result);
    }

    /**
     * 启动异步应用（推荐；带回按行进度）。
     *
     * @param request 同同步应用：docId + confirm=true
     * @return jobId
     */
    public StartApplyNoticeDocResponse startApplyNoticeDoc(StartApplyNoticeDocRequest request) {
        StartApplyNoticeDocRequest safe = request;
        if (safe == null) {
            safe = new StartApplyNoticeDocRequest();
        }
        if (safe.getDocId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "docId 不能为空");
        }
        if (safe.getConfirm() == null || !safe.getConfirm()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_APPLY_DENIED, "请二次确认后再应用");
        }
        String jobId = noticeApplyWorkflow.startApply(safe.getDocId(), safe.getOperatorId());
        StartApplyNoticeDocResponse response = new StartApplyNoticeDocResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 启动多文件串行异步应用（勾选批量）。
     *
     * @param request docIds + confirm=true
     * @return jobId（与单文件共用进度轮询）
     */
    public StartApplyNoticeDocResponse startApplyNoticeDocs(StartApplyNoticeDocsRequest request) {
        StartApplyNoticeDocsRequest safe = request;
        if (safe == null) {
            safe = new StartApplyNoticeDocsRequest();
        }
        if (safe.getDocIds() == null || safe.getDocIds().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "docIds 不能为空");
        }
        if (safe.getConfirm() == null || !safe.getConfirm()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_APPLY_DENIED, "请二次确认后再应用");
        }
        String jobId = noticeApplyWorkflow.startApplyBatch(safe.getDocIds(), safe.getOperatorId());
        StartApplyNoticeDocResponse response = new StartApplyNoticeDocResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 轮询应用进度。
     *
     * @param request 含 jobId
     * @return 进度与结果计数
     */
    public GetApplyNoticeDocProgressResponse getApplyNoticeDocProgress(
            GetApplyNoticeDocProgressRequest request) {
        GetApplyNoticeDocProgressRequest safe = request;
        if (safe == null) {
            safe = new GetApplyNoticeDocProgressRequest();
        }
        if (safe.getJobId() == null || safe.getJobId().isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "jobId 不能为空");
        }
        NoticeApplyJob job = noticeApplyWorkflow.getJob(safe.getJobId());
        GetApplyNoticeDocProgressResponse response = new GetApplyNoticeDocProgressResponse();
        response.setJobId(job.getJobId());
        response.setStatus(job.getStatus());
        response.setTotal(job.getTotal());
        response.setProcessed(job.getProcessed());
        response.setMessage(job.getMessage());
        response.setCurrentDocId(job.getCurrentDocId());
        response.setDocTotal(job.getDocTotal());
        response.setDocProcessed(job.getDocProcessed());
        response.setAppliedLineCount(job.getAppliedLineCount());
        response.setFailedLineCount(job.getFailedLineCount());
        response.setWritebackCount(job.getWritebackCount());
        response.setSyncedUnchargedCount(job.getSyncedUnchargedCount());
        int percent = 0;
        if (job.getTotal() > 0) {
            percent = (int) Math.round(100.0 * job.getProcessed() / job.getTotal());
            if (percent > 100) {
                percent = 100;
            }
        }
        if (NoticeApplyJob.STATUS_SUCCEEDED.equals(job.getStatus())) {
            percent = 100;
        }
        response.setPercent(percent);
        return response;
    }

    /**
     * 下载已存标准落地包（IF-CHS-CAT-005-R-05）。
     *
     * @param request 含 docId
     * @return fileName + contentBase64
     */
    public DownloadNoticePackResponse downloadNoticePack(DownloadNoticePackRequest request) {
        // Gate：docId 必填
        DownloadNoticePackRequest safe = request;
        if (safe == null) {
            safe = new DownloadNoticePackRequest();
        }
        if (safe.getDocId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "docId 不能为空");
        }

        // Pre：取文件头并读标准包字节
        NoticeDocView doc = catalogNoticeDocQueryService.getDocById(
            safe.getDocId(), TenantUtils.getCurrentTenantId());
        if (doc.getCanonicalPackUri() == null || doc.getCanonicalPackUri().isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_STORE_FAILED, "标准包 URI 为空，无法下载");
        }
        byte[] content = catalogNoticePackStorePort.loadCanonicalPack(doc.getCanonicalPackUri());
        // Cmd：本接口仅读标准包，无写库
        // Asm：装填 Base64 响应
        DownloadNoticePackResponse response = new DownloadNoticePackResponse();
        String fileName = doc.getSourceFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "notice-pack-" + doc.getId() + ".zip";
        } else if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileName = fileName + ".zip";
        }
        response.setFileName(fileName);
        response.setContentBase64(Base64.getEncoder().encodeToString(content));
        return response;
    }

    /**
     * 关闭催办待办（IF-CHS-CAT-005-W-04）。
     * 关闭原因必填，防止无审计地吞掉 OPEN 待办。
     *
     * @param request 含 todoId、closeReason
     * @return 关闭后的 todoId 与状态
     */
    public CloseNoticeTodoResponse closeNoticeTodo(CloseNoticeTodoRequest request) {
        // Gate：待办主键与关闭原因必填，避免误关或空原因
        CloseNoticeTodoRequest safe = request;
        if (safe == null) {
            safe = new CloseNoticeTodoRequest();
        }
        if (safe.getTodoId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_TODO_NOT_FOUND, "todoId 不能为空");
        }
        // 关闭须留下原因，便于审计；与「待办不存在」区分错误码
        if (safe.getCloseReason() == null || safe.getCloseReason().isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "closeReason 不能为空");
        }
        // Pre：组装领域关闭命令
        CloseNoticeTodoCommand command = new CloseNoticeTodoCommand();
        command.setTenantId(TenantUtils.getCurrentTenantId());
        command.setTodoId(safe.getTodoId());
        command.setCloseReason(safe.getCloseReason());
        command.setOperatorId(safe.getOperatorId());
        // Cmd：领域关闭 OPEN 待办
        CloseNoticeTodoResult result = catalogNoticeTodoDomainService.closeTodo(command);
        // Asm：映射关闭响应
        return insuranceCatalogNoticeMapper.toCloseTodoResponse(result);
    }

    /**
     * 再次执行合规校验（IF-CHS-CAT-005-W-05）。
     * 覆盖写入最新报告并重算 OPEN 待办。
     *
     * @param request 可选 contractId / listType
     * @return reportId / issueCount / ranAt
     */
    public RerunNoticeComplianceResponse rerunNoticeCompliance(RerunNoticeComplianceRequest request) {
        // Gate：请求可空；过滤条件由领域解释
        RerunNoticeComplianceRequest safe = request;
        if (safe == null) {
            safe = new RerunNoticeComplianceRequest();
        }
        // Pre：组装跑批命令
        RunComplianceCommand command = new RunComplianceCommand();
        command.setTenantId(TenantUtils.getCurrentTenantId());
        command.setContractId(safe.getContractId());
        command.setListType(safe.getListType());
        command.setOperatorId(safe.getOperatorId());
        // Cmd：编排再跑合规
        RunComplianceResult result = noticeComplianceWorkflow.rerunNoticeCompliance(command);
        // Asm：映射跑批响应
        return insuranceCatalogNoticeMapper.toRerunResponse(result);
    }

    /**
     * 查询最新合规报告（IF-CHS-CAT-005-R-03）。
     *
     * @param request 可选 contractId / listType
     * @return 报告摘要与结构化 issues；summaryJson 为 SDD 契约字段
     */
    public GetComplianceReportResponse getComplianceReport(GetComplianceReportRequest request) {
        // Gate：请求可空
        GetComplianceReportRequest safe = request;
        if (safe == null) {
            safe = new GetComplianceReportRequest();
        }
        // Pre：组装只读查询
        GetComplianceReportQuery query = new GetComplianceReportQuery();
        query.setTenantId(TenantUtils.getCurrentTenantId());
        query.setContractId(safe.getContractId());
        query.setListType(safe.getListType());
        // Cmd：本接口仅读最新报告
        ComplianceReportView view = catalogNoticeComplianceQueryService.getLatestReport(query);
        // Asm：映射报告响应
        return insuranceCatalogNoticeMapper.toComplianceReportResponse(view);
    }

    /**
     * 查询催办汇总与待办（IF-CHS-CAT-005-R-04）。
     *
     * @param request 分页与 todoType/status 过滤
     * @return summary + total + records
     */
    public PageNoticeTodoResponse pageNoticeTodos(PageNoticeTodoRequest request) {
        // Gate：补全分页缺省，避免空页码
        PageNoticeTodoRequest safe = request;
        if (safe == null) {
            safe = new PageNoticeTodoRequest();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Pre：汇总 OPEN 计数 + 组装分页查询
        NoticeTodoSummaryView summary = catalogNoticeTodoQueryService.summarizeTodos(tenantId);
        PageNoticeTodoQuery query = new PageNoticeTodoQuery();
        query.setTenantId(tenantId);
        int page = 1;
        if (safe.getPage() != null && safe.getPage() > 0) {
            page = safe.getPage();
        }
        int size = 20;
        if (safe.getSize() != null && safe.getSize() > 0) {
            size = safe.getSize();
        }
        query.setPage(page);
        query.setSize(size);
        query.setTodoType(safe.getTodoType());
        query.setStatus(safe.getStatus());
        // Cmd：本接口仅读待办
        NoticePageResult<NoticeTodoView> pageResult = catalogNoticeTodoQueryService.pageTodos(query);
        // Asm：装配汇总与分页响应
        return insuranceCatalogNoticeMapper.toPageTodoResponse(summary, pageResult);
    }

    /**
     * 目录检查（只读）：对照院内已启用医保项与国家目录、已应用局文的有效期 / 启用 / 限价。
     * <p>不写待办；人工改目录后再查，查不到即视为已处理。</p>
     *
     * @param request 可选契约、目录类别
     * @return 扫描统计与问题行
     */
    public CheckCatalogIssuesResponse checkCatalogIssues(CheckCatalogIssuesRequest request) {
        CheckCatalogIssuesRequest safe = request;
        if (safe == null) {
            safe = new CheckCatalogIssuesRequest();
        }
        Long contractId = safe.getContractId();
        String contractBusNo = safe.getContractBusNo();
        if (contractId != null || (contractBusNo != null && !contractBusNo.isBlank())) {
            ChsContractSnapshot contract = chsInsuranceContractReader.requireContract(contractBusNo, contractId);
            contractId = contract.id();
        }
        CatalogCheckResult result = catalogCheckQueryService.check(
            TenantUtils.getCurrentTenantId(), contractId, safe.getProductKind());
        return insuranceCatalogNoticeMapper.toCheckCatalogResponse(result);
    }

    private GetConfirmNoticeScanImportProgressResponse toImportProgressResponse(NoticeImportJob job) {
        GetConfirmNoticeScanImportProgressResponse response = new GetConfirmNoticeScanImportProgressResponse();
        response.setJobId(job.getJobId());
        response.setStatus(job.getStatus());
        response.setTotal(job.getTotal());
        response.setProcessed(job.getProcessed());
        response.setCurrentPackDir(job.getCurrentPackDir());
        response.setMessage(job.getMessage());
        response.setImportedCount(job.getImportedCount());
        response.setSkippedCount(job.getSkippedCount());
        response.setFailedCount(job.getFailedCount());
        response.setRemainingCount(job.remainingCount());
        response.setResults(job.copyResults());
        int percent = 0;
        if (job.getTotal() > 0) {
            percent = (int) Math.round(100.0 * job.getProcessed() / job.getTotal());
            if (percent > 100) {
                percent = 100;
            }
        }
        if (NoticeImportJob.STATUS_SUCCEEDED.equals(job.getStatus())) {
            percent = 100;
        }
        response.setPercent(percent);
        boolean resumable = job.remainingCount() > 0
            && !NoticeImportJob.STATUS_RUNNING.equals(job.getStatus())
            && !NoticeImportJob.STATUS_PENDING.equals(job.getStatus());
        response.setResumable(resumable);
        return response;
    }
}
