/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.queryservice;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeComplianceReport;
import com.zxplat.plugin.chs.domain.notice.model.ComplianceIssueView;
import com.zxplat.plugin.chs.domain.notice.model.ComplianceReportView;
import com.zxplat.plugin.chs.domain.notice.model.GetComplianceReportQuery;
import com.zxplat.plugin.chs.domain.notice.repository.YbCatalogNoticeComplianceReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 合规报告只读查询（R-03）。
 * <p>仅本聚合 Repository（{@link YbCatalogNoticeComplianceReportRepository}）。</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogNoticeComplianceQueryService {

    private final YbCatalogNoticeComplianceReportRepository reportRepository;

    /**
     * 取最新合规报告；无报告返回空视图（issueCount=0）。
     * issuesJson 反序列化为结构化 {@link ComplianceIssueView} 列表。
     *
     * @param query 租户 + 可选契约/类别过滤
     * @return 最新报告视图；无数据时 issueCount=0
     */
    public ComplianceReportView getLatestReport(GetComplianceReportQuery query) {
        ComplianceReportView empty = new ComplianceReportView();
        empty.setIssueCount(0);
        empty.setIssues(Collections.emptyList());
        if (query == null || query.getTenantId() == null) {
            return empty;
        }
        Optional<YbCatalogNoticeComplianceReport> opt;
        // 契约+类别最具体 → 仅契约 → 租户最新
        if (query.getContractId() != null && query.getListType() != null && !query.getListType().isBlank()) {
            opt = reportRepository
                .findFirstByTenantIdAndContractIdAndListTypeAndIsDeletedFalseOrderByRanAtDesc(
                    query.getTenantId(), query.getContractId(), query.getListType().trim());
        } else if (query.getContractId() != null) {
            opt = reportRepository.findFirstByTenantIdAndContractIdAndIsDeletedFalseOrderByRanAtDesc(
                query.getTenantId(), query.getContractId());
        } else {
            opt = reportRepository.findFirstByTenantIdAndIsDeletedFalseOrderByRanAtDesc(query.getTenantId());
        }
        // 尚无报告：前端展示空态
        if (opt.isEmpty()) {
            return empty;
        }
        YbCatalogNoticeComplianceReport report = opt.get();
        ComplianceReportView view = new ComplianceReportView();
        view.setReportId(report.getId());
        view.setContractId(report.getContractId());
        view.setListType(report.getListType());
        view.setSummaryJson(report.getSummaryJson());
        view.setIssueCount(report.getIssueCount());
        view.setRanAt(report.getRanAt());
        view.setIssues(parseIssues(report.getIssuesJson()));
        return view;
    }

    private static List<ComplianceIssueView> parseIssues(String issuesJson) {
        if (issuesJson == null || issuesJson.isBlank()) {
            return Collections.emptyList();
        }
        List<ComplianceIssueView> list = JSON.parseObject(
            issuesJson, new TypeReference<List<ComplianceIssueView>>() {
            });
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }
}
