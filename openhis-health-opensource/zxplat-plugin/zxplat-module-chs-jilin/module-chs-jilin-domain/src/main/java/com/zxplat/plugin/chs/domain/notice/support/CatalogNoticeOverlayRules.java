/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.support;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 局文 overlay 公共判定（门禁 / 选码合成共用）。
 */
public final class CatalogNoticeOverlayRules {

    public static final String FLAG_STOP = "0";
    public static final String FLAG_AVAILABLE = "1";
    /** 契约为空或全局叠加时的 contract_id。 */
    public static final Long CONTRACT_FALLBACK = 0L;

    private CatalogNoticeOverlayRules() {
    }

    /**
     * 通知停用是否已生效：flag=0，且无终止日或 asOf &gt;= 终止日。
     */
    public static boolean isNoticeStopEffective(YbNationalCatalogNoticeOverlay overlay, LocalDate asOf) {
        if (overlay == null) {
            return false;
        }
        if (!FLAG_STOP.equals(trimToNull(overlay.getNoticeAvaliFlag()))) {
            return false;
        }
        LocalDate end = overlay.getNoticeEndDate();
        LocalDate day = asOf == null ? LocalDate.now() : asOf;
        if (end != null && day.isBefore(end)) {
            return false;
        }
        return true;
    }

    /**
     * 从候选列表挑出与契约/品类最匹配的一条（精确契约优先于 contract_id=0）。
     */
    public static Optional<YbNationalCatalogNoticeOverlay> pickBestOverlay(
            Collection<YbNationalCatalogNoticeOverlay> rows,
            Long contractId,
            String listType) {
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        Long contract = contractId != null ? contractId : CONTRACT_FALLBACK;
        String lt = trimToNull(listType);
        if (lt != null) {
            Optional<YbNationalCatalogNoticeOverlay> exact = firstMatching(rows, contract, lt);
            if (exact.isPresent()) {
                return exact;
            }
            if (!CONTRACT_FALLBACK.equals(contract)) {
                Optional<YbNationalCatalogNoticeOverlay> fallback =
                    firstMatching(rows, CONTRACT_FALLBACK, lt);
                if (fallback.isPresent()) {
                    return fallback;
                }
            }
        }
        for (YbNationalCatalogNoticeOverlay row : rows) {
            if (row == null) {
                continue;
            }
            if (contract.equals(row.getContractId()) || CONTRACT_FALLBACK.equals(row.getContractId())) {
                return Optional.of(row);
            }
        }
        for (YbNationalCatalogNoticeOverlay row : rows) {
            if (row != null) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    public static String readPatchString(String patchJson, String key) {
        JSONObject obj = parsePatch(patchJson);
        if (obj == null) {
            return null;
        }
        return trimToNull(obj.getString(key));
    }

    public static BigDecimal readPatchDecimal(String patchJson, String key) {
        String raw = readPatchString(patchJson, key);
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static JSONObject parsePatch(String patchJson) {
        if (patchJson == null || patchJson.isBlank()) {
            return null;
        }
        return JSON.parseObject(patchJson);
    }

    private static Optional<YbNationalCatalogNoticeOverlay> firstMatching(
            Collection<YbNationalCatalogNoticeOverlay> rows,
            Long contractId,
            String listType) {
        for (YbNationalCatalogNoticeOverlay row : rows) {
            if (row == null) {
                continue;
            }
            if (contractId.equals(row.getContractId()) && listType.equals(row.getListType())) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }
}
