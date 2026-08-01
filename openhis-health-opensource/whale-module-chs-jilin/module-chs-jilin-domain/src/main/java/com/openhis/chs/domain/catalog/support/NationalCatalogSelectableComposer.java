/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.catalog.result.NationalCatalogItemResult;
import com.openhis.chs.domain.notice.entity.YbNationalCatalogNoticeOverlay;
import com.openhis.chs.domain.notice.support.CatalogNoticeOverlayRules;
import com.openhis.chs.domain.share.enums.ValiFlag;

import java.time.LocalDate;

/**
 * 国家目录选码合成：镜像 ⋈ overlay → isSelectable + 补字段展示。
 */
public final class NationalCatalogSelectableComposer {

    public static final String REASON_PLATFORM_INVALID = "平台停用";
    public static final String REASON_PLATFORM_EXPIRED = "平台已过期";
    public static final String REASON_NOTICE_STOP = "局文已停用";

    private NationalCatalogSelectableComposer() {
    }

    /**
     * 就地合成：优先用 patch 补齐展示字段，并写入可选性。
     *
     * @param row     镜像结果行（可含 match 字段的子类）
     * @param overlay 匹配到的叠加；可空
     * @param asOf    判定日；空则当天
     */
    public static void compose(
            NationalCatalogItemResult row,
            YbNationalCatalogNoticeOverlay overlay,
            LocalDate asOf) {
        if (row == null) {
            return;
        }
        LocalDate day = asOf == null ? LocalDate.now() : asOf;
        mergePatchFields(row, overlay);
        if (overlay != null) {
            row.setNoticeAvaliFlag(overlay.getNoticeAvaliFlag());
            row.setNoticeEndDate(overlay.getNoticeEndDate());
        }

        String platformReason = platformUnselectableReason(row, day);
        if (platformReason != null) {
            row.setIsSelectable(false);
            row.setUnselectableReason(platformReason);
            return;
        }
        if (CatalogNoticeOverlayRules.isNoticeStopEffective(overlay, day)) {
            row.setIsSelectable(false);
            String reason = REASON_NOTICE_STOP;
            if (overlay.getNoticeEndDate() != null) {
                reason = reason + "（" + overlay.getNoticeEndDate() + "）";
            }
            row.setUnselectableReason(reason);
            return;
        }
        row.setIsSelectable(true);
        row.setUnselectableReason(null);
    }

    private static void mergePatchFields(
            NationalCatalogItemResult row,
            YbNationalCatalogNoticeOverlay overlay) {
        if (overlay == null) {
            return;
        }
        String patch = overlay.getNoticePatchJson();
        String lv = CatalogNoticeOverlayRules.readPatchString(patch, "chrgitm_lv");
        if (lv != null) {
            row.setChrgitmLv(lv);
        }
        String name = CatalogNoticeOverlayRules.readPatchString(patch, "hilist_name");
        if (name != null) {
            row.setHilistName(name);
        }
        String lmt = CatalogNoticeOverlayRules.readPatchString(patch, "lmt_used_flag");
        if (lmt != null) {
            row.setLmtUsedFlag(lmt);
        }
        String price = CatalogNoticeOverlayRules.readPatchString(patch, "hilist_pric_uplmt_amt");
        if (price != null) {
            row.setPriceUplmtAmt(price);
        }
    }

    private static String platformUnselectableReason(NationalCatalogItemResult row, LocalDate asOf) {
        String vali = trimToNull(row.getValiFlag());
        if (vali != null && !ValiFlag.VALID.getCode().equals(vali)) {
            return REASON_PLATFORM_INVALID;
        }
        LocalDate end = row.getEnddate();
        if (end != null && end.getYear() > 1 && asOf.isAfter(end)) {
            return REASON_PLATFORM_EXPIRED;
        }
        return null;
    }
}
