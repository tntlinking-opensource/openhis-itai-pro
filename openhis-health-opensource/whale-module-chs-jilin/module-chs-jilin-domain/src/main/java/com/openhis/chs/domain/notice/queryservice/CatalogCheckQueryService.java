/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.queryservice;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogItemRepository;
import com.openhis.chs.domain.catalog.support.CatalogCheckEvaluator;
import com.openhis.chs.domain.catalog.support.CatalogCheckIssueCodes;
import com.openhis.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.openhis.chs.domain.notice.model.CatalogCheckIssueView;
import com.openhis.chs.domain.notice.model.CatalogCheckResult;
import com.openhis.chs.domain.notice.model.HisMappedProduct;
import com.openhis.chs.domain.notice.port.CatalogNoticeHisProductPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 目录检查只读查询：院内已启用医保项对照国家目录有效期 / 启用 / 限价。
 * <p>不写待办；人工改目录后再查，查不到即视为已处理。</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogCheckQueryService {

    private static final int HILIST_CHUNK = 400;
    private static final int MAX_ISSUES = 2000;

    private final CatalogNoticeHisProductPort hisProductPort;
    private final YbNationalCatalogItemRepository nationalCatalogItemRepository;
    private final NationalCatalogOverlayQueryService overlayQueryService;

    /**
     * 扫描院内已启用医保目录项，返回当前仍存在的问题。
     *
     * @param tenantId    租户；空则返回空结果
     * @param contractId  契约；空则不按契约过滤镜像
     * @param productKind ACTIVITY / MEDICATION / DEVICE；空表示全部
     * @return 扫描统计与问题行
     */
    @Transactional(readOnly = true)
    public CatalogCheckResult check(Long tenantId, Long contractId, String productKind) {
        LocalDate asOf = LocalDate.now();
        CatalogCheckResult result = new CatalogCheckResult();
        result.setAsOf(asOf);
        if (tenantId == null) {
            return result;
        }
        String kindFilter = trimToNull(productKind);
        List<HisMappedProduct> products = hisProductPort.listActiveInsuranceProducts();
        List<HisMappedProduct> scoped = new ArrayList<>();
        for (HisMappedProduct product : products) {
            if (product == null) {
                continue;
            }
            if (kindFilter != null && !kindFilter.equals(product.getProductKind())) {
                continue;
            }
            scoped.add(product);
        }
        result.setScannedCount(scoped.size());
        long catalogCount = nationalCatalogItemRepository.countByTenantIdAndIsDeletedFalse(tenantId);
        result.setCatalogItemCount(catalogCount);
        boolean catalogPresent = catalogCount > 0;

        Set<String> hilistCodes = new LinkedHashSet<>();
        for (HisMappedProduct product : scoped) {
            String ybNo = trimToNull(product.getYbNo());
            if (ybNo != null) {
                hilistCodes.add(ybNo);
            }
        }
        Map<String, List<YbNationalCatalogItem>> catalogByHilist =
            loadCatalogByHilist(tenantId, contractId, hilistCodes);
        Map<String, YbNationalCatalogNoticeOverlay> overlayByHilist =
            overlayQueryService.mapOverlaysByHilistCodes(tenantId, contractId, null, hilistCodes);

        List<CatalogCheckIssueView> issues = new ArrayList<>();
        for (HisMappedProduct product : scoped) {
            if (issues.size() >= MAX_ISSUES) {
                break;
            }
            String ybNo = trimToNull(product.getYbNo());
            YbNationalCatalogItem catalog = pickCatalogRow(
                ybNo == null ? null : catalogByHilist.get(ybNo), product.getListType());
            YbNationalCatalogNoticeOverlay overlay =
                ybNo == null ? null : overlayByHilist.get(ybNo);
            List<CatalogCheckEvaluator.Finding> findings = CatalogCheckEvaluator.evaluate(
                product, catalog, overlay, catalogPresent, asOf);
            for (CatalogCheckEvaluator.Finding finding : findings) {
                if (issues.size() >= MAX_ISSUES) {
                    break;
                }
                issues.add(toView(product, finding));
            }
        }
        result.setIssues(issues);
        result.setIssueCount(issues.size());
        tally(result, issues);
        return result;
    }

    private Map<String, List<YbNationalCatalogItem>> loadCatalogByHilist(
            Long tenantId, Long contractId, Set<String> hilistCodes) {
        Map<String, List<YbNationalCatalogItem>> map = new HashMap<>();
        if (hilistCodes.isEmpty()) {
            return map;
        }
        List<String> codes = new ArrayList<>(hilistCodes);
        for (int from = 0; from < codes.size(); from += HILIST_CHUNK) {
            int to = Math.min(from + HILIST_CHUNK, codes.size());
            List<YbNationalCatalogItem> rows = nationalCatalogItemRepository
                .findByTenantIdAndHilistCodeInAndIsDeletedFalse(tenantId, codes.subList(from, to));
            for (YbNationalCatalogItem row : rows) {
                if (row == null || trimToNull(row.getHilistCode()) == null) {
                    continue;
                }
                if (contractId != null && row.getContractId() != null && !contractId.equals(row.getContractId())) {
                    continue;
                }
                map.computeIfAbsent(row.getHilistCode().trim(), key -> new ArrayList<>()).add(row);
            }
        }
        return map;
    }

    private static YbNationalCatalogItem pickCatalogRow(List<YbNationalCatalogItem> rows, String listType) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        String lt = trimToNull(listType);
        if (lt != null) {
            for (YbNationalCatalogItem row : rows) {
                if (row != null && lt.equals(row.getListType())) {
                    return row;
                }
            }
        }
        return rows.get(0);
    }

    private static CatalogCheckIssueView toView(HisMappedProduct product, CatalogCheckEvaluator.Finding finding) {
        CatalogCheckIssueView view = new CatalogCheckIssueView();
        view.setIssueCode(finding.getIssueCode());
        view.setIssueLabel(finding.getIssueLabel());
        view.setMessage(finding.getMessage());
        view.setProductKind(product.getProductKind());
        view.setProductTable(product.getProductTable());
        view.setProductId(product.getProductId());
        view.setProjectCode(product.getBusNo());
        view.setProductName(product.getName());
        view.setHilistCode(product.getYbNo());
        view.setEnddate(finding.getEnddate());
        view.setValiFlag(finding.getValiFlag());
        view.setSalePrice(finding.getSalePrice());
        view.setLimitAmt(finding.getLimitAmt());
        return view;
    }

    private static void tally(CatalogCheckResult result, List<CatalogCheckIssueView> issues) {
        int expired = 0;
        int disabled = 0;
        int noticeStop = 0;
        int noticeExpired = 0;
        int noticeNotEffective = 0;
        int priceOver = 0;
        int missing = 0;
        int notIn = 0;
        for (CatalogCheckIssueView issue : issues) {
            String code = issue.getIssueCode();
            if (CatalogCheckIssueCodes.EXPIRED.equals(code)) {
                expired++;
            } else if (CatalogCheckIssueCodes.DISABLED.equals(code)) {
                disabled++;
            } else if (CatalogCheckIssueCodes.NOTICE_STOP.equals(code)) {
                noticeStop++;
            } else if (CatalogCheckIssueCodes.NOTICE_EXPIRED.equals(code)) {
                noticeExpired++;
            } else if (CatalogCheckIssueCodes.NOTICE_NOT_EFFECTIVE.equals(code)) {
                noticeNotEffective++;
            } else if (CatalogCheckIssueCodes.PRICE_OVER.equals(code)) {
                priceOver++;
            } else if (CatalogCheckIssueCodes.MISSING_YB_NO.equals(code)) {
                missing++;
            } else if (CatalogCheckIssueCodes.NOT_IN_CATALOG.equals(code)) {
                notIn++;
            }
        }
        result.setExpiredCount(expired);
        result.setDisabledCount(disabled);
        result.setNoticeStopCount(noticeStop);
        result.setNoticeExpiredCount(noticeExpired);
        result.setNoticeNotEffectiveCount(noticeNotEffective);
        result.setPriceOverCount(priceOver);
        result.setMissingYbNoCount(missing);
        result.setNotInCatalogCount(notIn);
    }
}
