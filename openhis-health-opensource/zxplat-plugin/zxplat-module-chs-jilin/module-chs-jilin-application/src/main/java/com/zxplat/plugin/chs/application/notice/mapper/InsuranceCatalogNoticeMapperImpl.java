/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.mapper;

import com.zxplat.plugin.chs.application.notice.dto.ApplyPreviewSampleDto;
import com.zxplat.plugin.chs.application.notice.dto.CatalogCheckIssueDto;
import com.zxplat.plugin.chs.application.notice.dto.ComplianceIssueDto;
import com.zxplat.plugin.chs.application.notice.dto.NoticeDocRowDto;
import com.zxplat.plugin.chs.application.notice.dto.NoticeLineRowDto;
import com.zxplat.plugin.chs.application.notice.dto.NoticeScanItemDto;
import com.zxplat.plugin.chs.application.notice.dto.NoticeTodoRowDto;
import com.zxplat.plugin.chs.application.notice.response.CheckCatalogIssuesResponse;
import com.zxplat.plugin.chs.application.notice.response.CloseNoticeTodoResponse;
import com.zxplat.plugin.chs.application.notice.response.GetComplianceReportResponse;
import com.zxplat.plugin.chs.application.notice.response.NoticeTodoSummaryResponse;
import com.zxplat.plugin.chs.application.notice.response.PageNoticeDocResponse;
import com.zxplat.plugin.chs.application.notice.response.PageNoticeLineResponse;
import com.zxplat.plugin.chs.application.notice.response.PageNoticeTodoResponse;
import com.zxplat.plugin.chs.application.notice.response.PreviewNoticeApplyResponse;
import com.zxplat.plugin.chs.application.notice.response.RerunNoticeComplianceResponse;
import com.zxplat.plugin.chs.application.notice.response.ScanNoticeCanonicalDirResponse;
import com.zxplat.plugin.chs.application.notice.support.dto.NoticePreviewContext;
import com.zxplat.plugin.chs.application.notice.support.dto.NoticeScanCachedItem;
import com.zxplat.plugin.chs.application.notice.support.dto.NoticeScanSession;
import com.zxplat.plugin.chs.domain.notice.command.PersistNoticeLineCommand;
import com.zxplat.plugin.chs.domain.notice.model.ApplyPreviewSampleView;
import com.zxplat.plugin.chs.domain.notice.model.ApplyPreviewView;
import com.zxplat.plugin.chs.domain.notice.model.CatalogCheckIssueView;
import com.zxplat.plugin.chs.domain.notice.model.CatalogCheckResult;
import com.zxplat.plugin.chs.domain.notice.model.CloseNoticeTodoResult;
import com.zxplat.plugin.chs.domain.notice.model.ComplianceIssueView;
import com.zxplat.plugin.chs.domain.notice.model.ComplianceReportView;
import com.zxplat.plugin.chs.domain.notice.model.NoticeDocView;
import com.zxplat.plugin.chs.domain.notice.model.NoticeLineView;
import com.zxplat.plugin.chs.domain.notice.model.NoticePageResult;
import com.zxplat.plugin.chs.domain.notice.model.NoticeTodoSummaryView;
import com.zxplat.plugin.chs.domain.notice.model.NoticeTodoView;
import com.zxplat.plugin.chs.domain.notice.model.RunComplianceResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link InsuranceCatalogNoticeMapper} 手写实现；每个映射方法写全 JavaDoc。
 */
@Component
public class InsuranceCatalogNoticeMapperImpl implements InsuranceCatalogNoticeMapper {

    private static final String STATUS_NEW = "NEW";
    private static final String STATUS_DUPLICATE = "DUPLICATE";
    private static final String STATUS_FAILED = "FAILED";

    /**
     * 将扫描会话装配为 W-01 响应（含计数与明细行）。
     *
     * @param session 扫描缓存
     * @return 扫描响应；session 为空时返回空壳
     */
    @Override
    public ScanNoticeCanonicalDirResponse toScanResponse(NoticeScanSession session) {
        ScanNoticeCanonicalDirResponse response = new ScanNoticeCanonicalDirResponse();
        if (session == null) {
            return response;
        }
        response.setScanToken(session.getScanToken());
        response.setScanRoot(session.getScanRoot());
        int newCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;
        List<NoticeScanItemDto> items = new ArrayList<>();
        if (session.getItemsByPackDir() != null) {
            for (NoticeScanCachedItem cached : session.getItemsByPackDir().values()) {
                NoticeScanItemDto dto = toScanItemDto(cached);
                items.add(dto);
                if (STATUS_NEW.equals(dto.getStatus())) {
                    newCount++;
                } else if (STATUS_DUPLICATE.equals(dto.getStatus())) {
                    duplicateCount++;
                } else if (STATUS_FAILED.equals(dto.getStatus())) {
                    failedCount++;
                }
            }
        }
        response.setNewCount(newCount);
        response.setDuplicateCount(duplicateCount);
        response.setFailedCount(failedCount);
        response.setItems(items);
        return response;
    }

    /**
     * 将扫描缓存项映射为展示行（含动作计数）。
     *
     * @param item 缓存项
     * @return 扫描行 DTO
     */
    @Override
    public NoticeScanItemDto toScanItemDto(NoticeScanCachedItem item) {
        NoticeScanItemDto dto = new NoticeScanItemDto();
        if (item == null) {
            return dto;
        }
        dto.setPackDir(item.getPackDir());
        dto.setStatus(item.getStatus());
        dto.setExistingDocId(item.getExistingDocId());
        dto.setMessage(item.getMessage());
        NoticePreviewContext preview = item.getPreview();
        if (preview == null) {
            return dto;
        }
        dto.setSourceFilename(preview.getSourceFilename());
        dto.setDocKind(preview.getDocKind());
        dto.setCategory(preview.getCategory());
        dto.setTitle(preview.getTitle());
        dto.setNoticeDate(preview.getNoticeDate());
        dto.setEffectiveDate(preview.getEffectiveDate());
        if (preview.getNoticeDate() == null && preview.getEffectiveDate() == null) {
            String old = dto.getMessage();
            String hint = "缺业务日，禁止应用（请补 notice_date 或 effective_date）";
            if (old == null || old.isBlank()) {
                dto.setMessage(hint);
            } else if (!old.contains("缺业务日")) {
                dto.setMessage(old + "；" + hint);
            }
        }
        int lineCount = preview.getLineCount();
        if (lineCount <= 0 && preview.getLines() != null) {
            lineCount = preview.getLines().size();
        }
        dto.setLineCount(lineCount);
        dto.setAddCount(preview.getAddCount());
        dto.setStopCount(preview.getStopCount());
        dto.setModifyCount(preview.getModifyCount());
        dto.setSetPayClassCount(preview.getSetPayClassCount());
        dto.setSourceFileHash(preview.getSourceFileHash());
        return dto;
    }

    /**
     * 将文件头领域分页装配为 R-01 响应（total + records）。
     *
     * @param page 领域分页
     * @return HTTP 分页响应
     */
    @Override
    public PageNoticeDocResponse toPageDocResponse(NoticePageResult<NoticeDocView> page) {
        PageNoticeDocResponse response = new PageNoticeDocResponse();
        if (page == null) {
            return response;
        }
        response.setTotal(page.getTotal());
        List<NoticeDocRowDto> rows = new ArrayList<>();
        if (page.getRecords() != null) {
            for (NoticeDocView view : page.getRecords()) {
                rows.add(toDocRowDto(view));
            }
        }
        response.setRecords(rows);
        return response;
    }

    /**
     * 将明细领域分页装配为 R-02 响应。
     *
     * @param page 领域分页
     * @return HTTP 分页响应
     */
    @Override
    public PageNoticeLineResponse toPageLineResponse(NoticePageResult<NoticeLineView> page) {
        PageNoticeLineResponse response = new PageNoticeLineResponse();
        if (page == null) {
            return response;
        }
        response.setTotal(page.getTotal());
        List<NoticeLineRowDto> rows = new ArrayList<>();
        if (page.getRecords() != null) {
            for (NoticeLineView view : page.getRecords()) {
                rows.add(toLineRowDto(view));
            }
        }
        response.setRecords(rows);
        return response;
    }

    /**
     * 将预览入库命令行映射为展示 DTO（样例用）。
     *
     * @param line 命令行
     * @return 行 DTO
     */
    @Override
    public NoticeLineRowDto toLineRowDto(PersistNoticeLineCommand line) {
        NoticeLineRowDto dto = new NoticeLineRowDto();
        if (line == null) {
            return dto;
        }
        dto.setAction(line.getAction());
        dto.setHilistCode(line.getHilistCode());
        dto.setProjectCode(line.getProjectCode());
        dto.setHilistName(line.getHilistName());
        dto.setBeginDate(line.getBeginDate());
        dto.setEndDate(line.getEndDate());
        dto.setChrgitmLv(line.getChrgitmLv());
        dto.setPriceLimitAmt(line.getPriceLimitAmt());
        dto.setSheetName(line.getSheetName());
        dto.setRowNo(line.getRowNo());
        dto.setWarning(line.getWarningMessage());
        return dto;
    }

    /**
     * 将持久化后的明细视图映射为展示 DTO（含 match/apply）。
     *
     * @param view 明细视图
     * @return 行 DTO
     */
    @Override
    public NoticeLineRowDto toLineRowDto(NoticeLineView view) {
        NoticeLineRowDto dto = new NoticeLineRowDto();
        if (view == null) {
            return dto;
        }
        dto.setAction(view.getAction());
        dto.setHilistCode(view.getHilistCode());
        dto.setProjectCode(view.getProjectCode());
        dto.setHilistName(view.getHilistName());
        dto.setBeginDate(view.getBeginDate());
        dto.setEndDate(view.getEndDate());
        dto.setChrgitmLv(view.getChrgitmLv());
        dto.setPriceLimitAmt(view.getPriceLimitAmt());
        dto.setSheetName(view.getSheetName());
        dto.setRowNo(view.getRowNo());
        dto.setWarning(view.getWarning());
        dto.setMatchStatus(view.getMatchStatus());
        dto.setApplyResult(view.getApplyResult());
        return dto;
    }

    /**
     * 将文件头视图映射为列表行 DTO。
     *
     * @param view 文件头视图
     * @return 列表行
     */
    @Override
    public NoticeDocRowDto toDocRowDto(NoticeDocView view) {
        NoticeDocRowDto dto = new NoticeDocRowDto();
        if (view == null) {
            return dto;
        }
        dto.setId(view.getId());
        dto.setDocKind(view.getDocKind());
        dto.setCategory(view.getCategory());
        dto.setTitle(view.getTitle());
        dto.setSourceFilename(view.getSourceFilename());
        dto.setNoticeDate(view.getNoticeDate());
        dto.setEffectiveDate(view.getEffectiveDate());
        dto.setParseStatus(view.getParseStatus());
        dto.setApplyStatus(view.getApplyStatus());
        dto.setLineCount(view.getLineCount());
        dto.setImportedAt(view.getImportedAt());
        return dto;
    }

    /**
     * 批量将预览样例行转为 DTO 列表。
     *
     * @param lines 命令行列表
     * @return DTO 列表；空入参返回空列表
     */
    @Override
    public List<NoticeLineRowDto> toSampleLineDtos(List<PersistNoticeLineCommand> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<NoticeLineRowDto> rows = new ArrayList<>(lines.size());
        for (PersistNoticeLineCommand line : lines) {
            rows.add(toLineRowDto(line));
        }
        return rows;
    }

    @Override
    public PreviewNoticeApplyResponse toPreviewApplyResponse(ApplyPreviewView view) {
        PreviewNoticeApplyResponse response = new PreviewNoticeApplyResponse();
        if (view == null) {
            return response;
        }
        response.setOverlayUpsertCount(view.getOverlayUpsertCount());
        response.setPendingMapCount(view.getPendingMapCount());
        response.setSkippedCount(view.getSkippedCount());
        response.setWritebackCount(view.getWritebackCount());
        response.setUnchargedSyncCount(view.getUnchargedSyncCount());
        List<ApplyPreviewSampleDto> samples = new ArrayList<>();
        if (view.getSamples() != null) {
            for (ApplyPreviewSampleView sample : view.getSamples()) {
                ApplyPreviewSampleDto dto = new ApplyPreviewSampleDto();
                dto.setAction(sample.getAction());
                dto.setHilistCode(sample.getHilistCode());
                dto.setProjectCode(sample.getProjectCode());
                dto.setEffectSummary(sample.getEffectSummary());
                samples.add(dto);
            }
        }
        response.setSamples(samples);
        return response;
    }

    /**
     * 将关闭待办结果映射为 W-04 响应。
     *
     * @param result 领域关闭结果
     * @return todoId 与关闭后状态；result 为空时返回空壳
     */
    @Override
    public CloseNoticeTodoResponse toCloseTodoResponse(CloseNoticeTodoResult result) {
        CloseNoticeTodoResponse response = new CloseNoticeTodoResponse();
        if (result == null) {
            return response;
        }
        response.setTodoId(result.getTodoId());
        response.setStatus(result.getStatus());
        return response;
    }

    /**
     * 将再跑合规结果映射为 W-05 响应。
     *
     * @param result 领域跑批结果
     * @return reportId / issueCount / ranAt；result 为空时返回空壳
     */
    @Override
    public RerunNoticeComplianceResponse toRerunResponse(RunComplianceResult result) {
        RerunNoticeComplianceResponse response = new RerunNoticeComplianceResponse();
        if (result == null) {
            return response;
        }
        response.setReportId(result.getReportId());
        response.setIssueCount(result.getIssueCount());
        response.setRanAt(result.getRanAt());
        return response;
    }

    /**
     * 将合规报告视图映射为 R-03 响应（issues 结构化；summaryJson 为契约透传字段）。
     *
     * @param view 领域报告视图
     * @return 报告响应；view 为空时返回空壳
     */
    @Override
    public GetComplianceReportResponse toComplianceReportResponse(ComplianceReportView view) {
        GetComplianceReportResponse response = new GetComplianceReportResponse();
        if (view == null) {
            return response;
        }
        response.setReportId(view.getReportId());
        response.setRanAt(view.getRanAt());
        response.setSummaryJson(view.getSummaryJson());
        response.setIssueCount(view.getIssueCount());
        List<ComplianceIssueDto> issues = new ArrayList<>();
        if (view.getIssues() != null) {
            for (ComplianceIssueView issue : view.getIssues()) {
                ComplianceIssueDto dto = new ComplianceIssueDto();
                dto.setCode(issue.getCode());
                dto.setHilistCode(issue.getHilistCode());
                dto.setProjectCode(issue.getProjectCode());
                dto.setMessage(issue.getMessage());
                dto.setSeverity(issue.getSeverity());
                issues.add(dto);
            }
        }
        response.setIssues(issues);
        return response;
    }

    /**
     * 将待办汇总与分页装配为 R-04 响应。
     *
     * @param summary OPEN 停用/甲乙类计数；可空
     * @param page    待办分页；空则 total=0、records 空列表
     * @return 汇总 + 分页行
     */
    @Override
    public PageNoticeTodoResponse toPageTodoResponse(
            NoticeTodoSummaryView summary, NoticePageResult<NoticeTodoView> page) {
        PageNoticeTodoResponse response = new PageNoticeTodoResponse();
        NoticeTodoSummaryResponse summaryDto = new NoticeTodoSummaryResponse();
        if (summary != null) {
            summaryDto.setStopOpenCount(summary.getStopOpenCount());
            summaryDto.setPayClassOpenCount(summary.getPayClassOpenCount());
        }
        response.setSummary(summaryDto);
        if (page == null) {
            response.setTotal(0L);
            response.setRecords(Collections.emptyList());
            return response;
        }
        response.setTotal(page.getTotal());
        List<NoticeTodoRowDto> rows = new ArrayList<>();
        if (page.getRecords() != null) {
            for (NoticeTodoView view : page.getRecords()) {
                NoticeTodoRowDto dto = new NoticeTodoRowDto();
                dto.setId(view.getId());
                dto.setTodoType(view.getTodoType());
                dto.setHilistCode(view.getHilistCode());
                dto.setProjectCode(view.getProjectCode());
                dto.setProductName(view.getProductName());
                dto.setEffectiveDate(view.getEffectiveDate());
                dto.setDocId(view.getDocId());
                dto.setStatus(view.getStatus());
                rows.add(dto);
            }
        }
        response.setRecords(rows);
        return response;
    }

    /**
     * 将目录检查领域结果装配为 HTTP 响应。
     *
     * @param result 扫描结果；可空
     * @return 响应；result 空时计数为 0
     */
    @Override
    public CheckCatalogIssuesResponse toCheckCatalogResponse(CatalogCheckResult result) {
        CheckCatalogIssuesResponse response = new CheckCatalogIssuesResponse();
        if (result == null) {
            return response;
        }
        response.setAsOf(result.getAsOf());
        response.setScannedCount(result.getScannedCount());
        response.setCatalogItemCount(result.getCatalogItemCount());
        response.setIssueCount(result.getIssueCount());
        response.setExpiredCount(result.getExpiredCount());
        response.setDisabledCount(result.getDisabledCount());
        response.setNoticeStopCount(result.getNoticeStopCount());
        response.setNoticeExpiredCount(result.getNoticeExpiredCount());
        response.setNoticeNotEffectiveCount(result.getNoticeNotEffectiveCount());
        response.setPriceOverCount(result.getPriceOverCount());
        response.setMissingYbNoCount(result.getMissingYbNoCount());
        response.setNotInCatalogCount(result.getNotInCatalogCount());
        List<CatalogCheckIssueDto> rows = new ArrayList<>();
        if (result.getIssues() != null) {
            for (CatalogCheckIssueView view : result.getIssues()) {
                CatalogCheckIssueDto dto = new CatalogCheckIssueDto();
                dto.setIssueCode(view.getIssueCode());
                dto.setIssueLabel(view.getIssueLabel());
                dto.setMessage(view.getMessage());
                dto.setProductKind(view.getProductKind());
                dto.setProductTable(view.getProductTable());
                dto.setProductId(view.getProductId());
                dto.setProjectCode(view.getProjectCode());
                dto.setProductName(view.getProductName());
                dto.setHilistCode(view.getHilistCode());
                dto.setEnddate(view.getEnddate());
                dto.setValiFlag(view.getValiFlag());
                dto.setSalePrice(view.getSalePrice());
                dto.setLimitAmt(view.getLimitAmt());
                rows.add(dto);
            }
        }
        response.setIssues(rows);
        return response;
    }
}
