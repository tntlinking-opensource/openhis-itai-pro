/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.mapper;

import com.zxplat.plugin.chs.application.notice.dto.NoticeDocRowDto;
import com.zxplat.plugin.chs.application.notice.dto.NoticeLineRowDto;
import com.zxplat.plugin.chs.application.notice.dto.NoticeScanItemDto;
import com.zxplat.plugin.chs.application.notice.response.CheckCatalogIssuesResponse;
import com.zxplat.plugin.chs.application.notice.response.CloseNoticeTodoResponse;
import com.zxplat.plugin.chs.application.notice.response.GetComplianceReportResponse;
import com.zxplat.plugin.chs.application.notice.response.PageNoticeDocResponse;
import com.zxplat.plugin.chs.application.notice.response.PageNoticeLineResponse;
import com.zxplat.plugin.chs.application.notice.response.PageNoticeTodoResponse;
import com.zxplat.plugin.chs.application.notice.response.PreviewNoticeApplyResponse;
import com.zxplat.plugin.chs.application.notice.response.RerunNoticeComplianceResponse;
import com.zxplat.plugin.chs.application.notice.response.ScanNoticeCanonicalDirResponse;
import com.zxplat.plugin.chs.application.notice.support.dto.NoticeScanCachedItem;
import com.zxplat.plugin.chs.application.notice.support.dto.NoticeScanSession;
import com.zxplat.plugin.chs.domain.notice.command.PersistNoticeLineCommand;
import com.zxplat.plugin.chs.domain.notice.model.ApplyPreviewView;
import com.zxplat.plugin.chs.domain.notice.model.CatalogCheckResult;
import com.zxplat.plugin.chs.domain.notice.model.CloseNoticeTodoResult;
import com.zxplat.plugin.chs.domain.notice.model.ComplianceReportView;
import com.zxplat.plugin.chs.domain.notice.model.NoticeDocView;
import com.zxplat.plugin.chs.domain.notice.model.NoticeLineView;
import com.zxplat.plugin.chs.domain.notice.model.NoticePageResult;
import com.zxplat.plugin.chs.domain.notice.model.NoticeTodoSummaryView;
import com.zxplat.plugin.chs.domain.notice.model.NoticeTodoView;
import com.zxplat.plugin.chs.domain.notice.model.RunComplianceResult;

import java.util.List;

/**
 * 医保文件通知应用层映射（手写接口，禁止 MapStruct 匿名生成导致缺注释）。
 */
public interface InsuranceCatalogNoticeMapper {

    /**
     * 扫描会话 → 扫描响应。
     *
     * @param session 扫描缓存
     * @return HTTP 响应
     */
    ScanNoticeCanonicalDirResponse toScanResponse(NoticeScanSession session);

    /**
     * 扫描缓存项 → 扫描行 DTO。
     *
     * @param item 缓存项
     * @return 扫描行
     */
    NoticeScanItemDto toScanItemDto(NoticeScanCachedItem item);

    /**
     * 文件头分页 → 响应。
     *
     * @param page 领域分页
     * @return HTTP 响应
     */
    PageNoticeDocResponse toPageDocResponse(NoticePageResult<NoticeDocView> page);

    /**
     * 明细分页 → 响应。
     *
     * @param page 领域分页
     * @return HTTP 响应
     */
    PageNoticeLineResponse toPageLineResponse(NoticePageResult<NoticeLineView> page);

    /**
     * 入库行命令 → 行 DTO。
     *
     * @param line 命令行
     * @return 行 DTO
     */
    NoticeLineRowDto toLineRowDto(PersistNoticeLineCommand line);

    /**
     * 明细视图 → 行 DTO。
     *
     * @param view 明细视图
     * @return 行 DTO
     */
    NoticeLineRowDto toLineRowDto(NoticeLineView view);

    /**
     * 文件头视图 → 列表行 DTO。
     *
     * @param view 文件头视图
     * @return 列表行
     */
    NoticeDocRowDto toDocRowDto(NoticeDocView view);

    /**
     * 批量样例映射。
     *
     * @param lines 命令行列表
     * @return DTO 列表
     */
    List<NoticeLineRowDto> toSampleLineDtos(List<PersistNoticeLineCommand> lines);

    /**
     * 应用预览 → 响应。
     *
     * @param view 领域预览
     * @return HTTP 响应
     */
    PreviewNoticeApplyResponse toPreviewApplyResponse(ApplyPreviewView view);

    /**
     * 关闭待办结果 → 响应。
     */
    CloseNoticeTodoResponse toCloseTodoResponse(CloseNoticeTodoResult result);

    /**
     * 重验结果 → 响应。
     */
    RerunNoticeComplianceResponse toRerunResponse(RunComplianceResult result);

    /**
     * 合规报告视图 → 响应。
     */
    GetComplianceReportResponse toComplianceReportResponse(ComplianceReportView view);

    /**
     * 汇总 + 待办分页 → 响应。
     */
    PageNoticeTodoResponse toPageTodoResponse(
        NoticeTodoSummaryView summary, NoticePageResult<NoticeTodoView> page);

    /**
     * 目录检查结果 → 响应。
     *
     * @param result 领域结果；可空
     * @return HTTP 响应
     */
    CheckCatalogIssuesResponse toCheckCatalogResponse(CatalogCheckResult result);
}
