/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.queryservice;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.catalog.result.NationalCatalogItemResult;
import com.zxplat.plugin.chs.domain.catalog.support.NationalCatalogSelectableComposer;
import com.zxplat.plugin.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.zxplat.plugin.chs.domain.notice.repository.YbNationalCatalogNoticeOverlayRepository;
import com.zxplat.plugin.chs.domain.notice.support.CatalogNoticeOverlayRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 局文叠加只读查询；供 CAT-001 选码合成复用。
 */
@Service
@RequiredArgsConstructor
public class NationalCatalogOverlayQueryService {

    private final YbNationalCatalogNoticeOverlayRepository overlayRepository;

    /**
     * 单码取 overlay（契约精确 → contract_id=0 → 编码兜底）。
     */
    public Optional<YbNationalCatalogNoticeOverlay> getOverlay(
            Long tenantId,
            Long contractId,
            String listType,
            String hilistCode) {
        String hilist = trimToNull(hilistCode);
        if (tenantId == null || hilist == null) {
            return Optional.empty();
        }
        List<YbNationalCatalogNoticeOverlay> rows =
            overlayRepository.findByTenantIdAndHilistCodeAndIsDeletedFalse(tenantId, hilist);
        return CatalogNoticeOverlayRules.pickBestOverlay(rows, contractId, listType);
    }

    /**
     * 按编码批量取 overlay；key=hilistCode。
     *
     * <p>pick 规则与 {@link #getOverlay} <b>完全一致</b>：入参 {@code listType} 原样传入
     * {@link CatalogNoticeOverlayRules#pickBestOverlay}；<b>禁止</b>在 listType 为空时用候选行的
     * listType 回填（否则会改变契约优先于「无品类过滤」的命中结果，导致结算门禁与单条路径不一致）。</p>
     *
     * @param tenantId    租户
     * @param contractId  契约
     * @param listType    品类；可空（与 getOverlay 同义）
     * @param hilistCodes 国家目录编码集合
     * @return hilistCode → 命中 overlay；无命中者不入 Map
     */
    public Map<String, YbNationalCatalogNoticeOverlay> mapOverlaysByHilistCodes(
            Long tenantId,
            Long contractId,
            String listType,
            Collection<String> hilistCodes) {
        Map<String, YbNationalCatalogNoticeOverlay> result = new HashMap<>();
        if (tenantId == null || hilistCodes == null || hilistCodes.isEmpty()) {
            return result;
        }
        List<String> codes = new ArrayList<>();
        for (String code : hilistCodes) {
            String hilist = trimToNull(code);
            if (hilist != null) {
                codes.add(hilist);
            }
        }
        if (codes.isEmpty()) {
            return result;
        }
        List<YbNationalCatalogNoticeOverlay> rows =
            overlayRepository.findByTenantIdAndHilistCodeInAndIsDeletedFalse(tenantId, codes);
        Map<String, List<YbNationalCatalogNoticeOverlay>> byCode = new LinkedHashMap<>();
        for (YbNationalCatalogNoticeOverlay row : rows) {
            if (row == null || trimToNull(row.getHilistCode()) == null) {
                continue;
            }
            byCode.computeIfAbsent(row.getHilistCode(), k -> new ArrayList<>()).add(row);
        }
        // 与 getOverlay 同口径：listType 原样传入，空则不按品类先过滤
        String lt = trimToNull(listType);
        for (Map.Entry<String, List<YbNationalCatalogNoticeOverlay>> entry : byCode.entrySet()) {
            CatalogNoticeOverlayRules.pickBestOverlay(entry.getValue(), contractId, lt)
                .ifPresent(o -> result.put(entry.getKey(), o));
        }
        return result;
    }

    /**
     * 对选码结果列表就地合成 isSelectable / 补字段。
     * <p>若行携带各自 listType，按行解析 overlay；否则统一用参数 listType。</p>
     */
    public void decorateSelectable(
            Long tenantId,
            Long contractId,
            String listType,
            List<? extends NationalCatalogItemResult> rows,
            LocalDate asOf) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<String> codes = new ArrayList<>(rows.size());
        for (NationalCatalogItemResult row : rows) {
            if (row != null && trimToNull(row.getHilistCode()) != null) {
                codes.add(row.getHilistCode());
            }
        }
        // 按行 listType 可能不同：先批量拉齐再按行 pick
        List<YbNationalCatalogNoticeOverlay> all = List.of();
        if (tenantId != null && !codes.isEmpty()) {
            all = overlayRepository.findByTenantIdAndHilistCodeInAndIsDeletedFalse(tenantId, codes);
        }
        Map<String, List<YbNationalCatalogNoticeOverlay>> byCode = new LinkedHashMap<>();
        for (YbNationalCatalogNoticeOverlay overlay : all) {
            if (overlay == null || trimToNull(overlay.getHilistCode()) == null) {
                continue;
            }
            byCode.computeIfAbsent(overlay.getHilistCode(), k -> new ArrayList<>()).add(overlay);
        }
        LocalDate day = asOf == null ? LocalDate.now() : asOf;
        String defaultLt = trimToNull(listType);
        for (NationalCatalogItemResult row : rows) {
            if (row == null) {
                continue;
            }
            String hilist = trimToNull(row.getHilistCode());
            YbNationalCatalogNoticeOverlay overlay = null;
            if (hilist != null) {
                String rowLt = trimToNull(row.getListType());
                if (rowLt == null) {
                    rowLt = defaultLt;
                }
                List<YbNationalCatalogNoticeOverlay> candidates = byCode.get(hilist);
                overlay = CatalogNoticeOverlayRules.pickBestOverlay(candidates, contractId, rowLt)
                    .orElse(null);
            }
            NationalCatalogSelectableComposer.compose(row, overlay, day);
        }
    }
}
