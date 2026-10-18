/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.zxplat.plugin.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.zxplat.plugin.chs.domain.catalog.repository.YbNationalCatalogItemRepository;
import com.zxplat.plugin.chs.domain.notice.command.RunComplianceCommand;
import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeComplianceReport;
import com.zxplat.plugin.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.zxplat.plugin.chs.domain.notice.model.ComplianceIssueView;
import com.zxplat.plugin.chs.domain.notice.model.RunComplianceResult;
import com.zxplat.plugin.chs.domain.notice.port.CatalogNoticeHisProductPort;
import com.zxplat.plugin.chs.domain.notice.repository.YbCatalogNoticeComplianceReportRepository;
import com.zxplat.plugin.chs.domain.notice.repository.YbNationalCatalogNoticeOverlayRepository;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合规校验写服务：生成/覆盖最新报告，并重算 OPEN 待办。
 * <p>聚合边界：写本聚合 compliance_report / todo；读本聚合 overlay；
 * 经 {@link CatalogNoticeHisProductPort} 读院内 lv；
 * 读 catalog 平台镜像 {@code YbNationalCatalogItem} 用于 C-STOP-LAG 对比
 * （跨聚合直读 Repository，待 Port 化前保留并在此标明口径）。</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogNoticeComplianceDomainService {

    private static final String FLAG_STOP = "0";
    private static final String TODO_STOP = "STOP";
    private static final String TODO_PAY_CLASS = "PAY_CLASS";
    private static final String SEV_HIGH = "HIGH";
    private static final String SEV_WARN = "WARN";

    private final YbNationalCatalogNoticeOverlayRepository overlayRepository;
    private final YbCatalogNoticeComplianceReportRepository reportRepository;
    private final YbNationalCatalogItemRepository nationalCatalogItemRepository;
    private final CatalogNoticeHisProductPort hisProductPort;
    private final CatalogNoticeTodoDomainService todoDomainService;

    /**
     * 跑一遍下载侧/全量合规，覆盖写入最新报告（W-05）。
     *
     * @param command 过滤条件；tenantId 必填
     * @return reportId / issueCount / ranAt
     */
    @Transactional
    public RunComplianceResult runCompliance(RunComplianceCommand command) {
        // 前置校验：无租户则无法落报告
        if (command == null || command.getTenantId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_COMPLIANCE_FAILED, "tenantId 不能为空");
        }
        Long tenantId = command.getTenantId();
        Long contractId = command.getContractId();
        String listType = trimToNull(command.getListType());
        LocalDate asOf = LocalDate.now();
        LocalDateTime ranAt = LocalDateTime.now();

        // 加载/组装：扫描 overlay 并收集下载侧问题
        List<YbNationalCatalogNoticeOverlay> overlays = overlayRepository.findAll(buildOverlaySpec(
            tenantId, contractId, listType));
        List<ComplianceIssueView> issues = new ArrayList<>();
        for (YbNationalCatalogNoticeOverlay overlay : overlays) {
            collectStopLag(overlay, asOf, issues);
            collectPayClass(tenantId, overlay, issues);
            collectPriceUnknown(overlay, issues);
        }

        // 持久化：覆盖写入最新报告（summary/issues 存库 JSON，出参 issues 由 QS 结构化）
        YbCatalogNoticeComplianceReport report = new YbCatalogNoticeComplianceReport();
        report.setTenantId(tenantId);
        report.setContractId(contractId);
        report.setListType(listType);
        report.setIssueCount(issues.size());
        report.setIssuesJson(JSON.toJSONString(issues));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("issueCount", issues.size());
        summary.put("overlayCount", overlays.size());
        summary.put("listType", listType);
        summary.put("contractId", contractId);
        report.setSummaryJson(JSON.toJSONString(summary));
        report.setRanAt(ranAt);
        report.setRanBy(command.getOperatorId());
        report.setIsDeleted(false);
        reportRepository.save(report);

        // 重算 OPEN 待办：停用类 / 甲乙类
        for (ComplianceIssueView issue : issues) {
            if (CatalogNoticeComplianceGate.C_STOP_LAG.equals(issue.getCode())
                || CatalogNoticeComplianceGate.C_STOP_ACTIVE.equals(issue.getCode())) {
                todoDomainService.upsertOpenTodo(
                    tenantId,
                    TODO_STOP,
                    issue.getHilistCode(),
                    issue.getProjectCode(),
                    issue.getMessage(),
                    asOf,
                    null,
                    null);
            } else if (CatalogNoticeComplianceGate.C_PAY_CLASS.equals(issue.getCode())) {
                todoDomainService.upsertOpenTodo(
                    tenantId,
                    TODO_PAY_CLASS,
                    issue.getHilistCode(),
                    issue.getProjectCode(),
                    issue.getMessage(),
                    asOf,
                    null,
                    null);
            }
        }

        RunComplianceResult result = new RunComplianceResult();
        result.setReportId(report.getId());
        result.setIssueCount(issues.size());
        result.setRanAt(ranAt);
        return result;
    }

    private void collectStopLag(
            YbNationalCatalogNoticeOverlay overlay,
            LocalDate asOf,
            List<ComplianceIssueView> issues) {
        // 未停用：不产停用类问题
        if (!FLAG_STOP.equals(trimToNull(overlay.getNoticeAvaliFlag()))) {
            return;
        }
        LocalDate end = overlay.getNoticeEndDate();
        // 停用日未到：尚未生效
        if (end != null && asOf.isBefore(end)) {
            return;
        }
        boolean stillAvailable = false;
        if (overlay.getListType() != null && overlay.getHilistCode() != null) {
            // B.6.4：跨聚合读 catalog 镜像 vali_flag，判定通知停用是否已落到平台侧
            stillAvailable = nationalCatalogItemRepository
                .findByContractIdAndListTypeAndHilistCodeAndTenantIdAndIsDeletedFalse(
                    overlay.getContractId(),
                    overlay.getListType(),
                    overlay.getHilistCode(),
                    overlay.getTenantId())
                .map(this::isPlatformAvailable)
                .orElse(false);
        }
        ComplianceIssueView issue = new ComplianceIssueView();
        // 镜像仍可用 → 滞后告警；已不可用 → 停用已生效
        if (stillAvailable) {
            issue.setCode(CatalogNoticeComplianceGate.C_STOP_LAG);
            issue.setMessage("通知已停用但平台镜像仍显示可用：" + overlay.getHilistCode());
        } else {
            issue.setCode(CatalogNoticeComplianceGate.C_STOP_ACTIVE);
            issue.setMessage("通知已停用并已生效：" + overlay.getHilistCode());
        }
        issue.setHilistCode(overlay.getHilistCode());
        issue.setSeverity(SEV_HIGH);
        issues.add(issue);
    }

    private void collectPayClass(
            Long tenantId,
            YbNationalCatalogNoticeOverlay overlay,
            List<ComplianceIssueView> issues) {
        String expected = readPatch(overlay.getNoticePatchJson(), "chrgitm_lv");
        if (expected == null) {
            return;
        }
        String actual = hisProductPort.findProductChrgitmLv(
            tenantId, overlay.getHilistCode(), null);
        if (actual == null || expected.equals(actual)) {
            return;
        }
        ComplianceIssueView issue = new ComplianceIssueView();
        issue.setCode(CatalogNoticeComplianceGate.C_PAY_CLASS);
        issue.setHilistCode(overlay.getHilistCode());
        issue.setMessage("甲乙类不一致：通知=" + expected + "，院内=" + actual);
        issue.setSeverity(SEV_HIGH);
        issues.add(issue);
    }

    private void collectPriceUnknown(
            YbNationalCatalogNoticeOverlay overlay,
            List<ComplianceIssueView> issues) {
        String limitRaw = readPatch(overlay.getNoticePatchJson(), "hilist_pric_uplmt_amt");
        if (limitRaw != null) {
            try {
                BigDecimal limit = new BigDecimal(limitRaw);
                // 有正限价则不产 UNKNOWN
                if (limit.compareTo(BigDecimal.ZERO) > 0) {
                    return;
                }
            } catch (NumberFormatException ignored) {
                // 限价无法解析：按无限价抽查标记
            }
        }
        ComplianceIssueView issue = new ComplianceIssueView();
        issue.setCode(CatalogNoticeComplianceGate.C_PRICE_UNKNOWN);
        issue.setHilistCode(overlay.getHilistCode());
        issue.setMessage("无限价，仅抽查标记：" + overlay.getHilistCode());
        issue.setSeverity(SEV_WARN);
        issues.add(issue);
    }

    private boolean isPlatformAvailable(YbNationalCatalogItem item) {
        if (item == null) {
            return false;
        }
        String flag = item.getValiFlag();
        return flag == null || "1".equals(flag.trim());
    }

    private Specification<YbNationalCatalogNoticeOverlay> buildOverlaySpec(
            Long tenantId, Long contractId, String listType) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (contractId != null) {
                predicates.add(cb.equal(root.get("contractId"), contractId));
            }
            if (listType != null) {
                predicates.add(cb.equal(root.get("listType"), listType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String readPatch(String patchJson, String key) {
        if (patchJson == null || patchJson.isBlank()) {
            return null;
        }
        var obj = com.alibaba.fastjson2.JSON.parseObject(patchJson);
        if (obj == null) {
            return null;
        }
        String v = obj.getString(key);
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }
}
