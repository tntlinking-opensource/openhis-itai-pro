/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.HerbalInboundReceiptConstants;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentApprovedLineListQuery;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentDocumentListQuery;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 产品调价列表可见性：读侧 Query 注入排除「中药自动调价」。
 *
 * <p>原因字符串必须与
 * {@link HerbalInboundReceiptConstants#AUTO_MARKUP_ADJUSTMENT_REASON} 精确相等。
 * 在 {@link com.openhis.health.domain.custom.priceadjustment.service.PriceAdjustmentQueryService}
 * 列表读方法 before 写入 {@code excludeAdjustmentReasons}，使调价工作站、财务审批并集、
 * 调价明细等凡走该 QS 的页面谓词层一致不可见。</p>
 */
@Component
public class HerbalPriceAdjustmentSupport {

    /**
     * 向单据列表 Query 注入排除原因（幂等；已含则不重复追加）。
     *
     * @param query 调价单据列表 Query；null 时忽略
     */
    public void applyAutoMarkupExclude(PriceAdjustmentDocumentListQuery query) {
        // Gate：无 Query 时无法注入
        if (query == null) {
            return;
        }
        query.setExcludeAdjustmentReasons(
            this.mergeExcludeReasons(query.getExcludeAdjustmentReasons()));
    }

    /**
     * 向已生效明细列表 Query 注入排除原因（幂等；已含则不重复追加）。
     *
     * @param query 调价明细列表 Query；null 时忽略
     */
    public void applyAutoMarkupExclude(PriceAdjustmentApprovedLineListQuery query) {
        // Gate：无 Query 时无法注入
        if (query == null) {
            return;
        }
        query.setExcludeAdjustmentReasons(
            this.mergeExcludeReasons(query.getExcludeAdjustmentReasons()));
    }

    /**
     * 合并产品自动调价排除原因到已有列表（精确全文，避免重复）。
     *
     * @param existing 调用方已设排除列表；可为 null
     * @return 含「中药自动调价」的新列表
     */
    private List<String> mergeExcludeReasons(List<String> existing) {
        List<String> merged = new ArrayList<>();
        if (existing != null) {
            for (String reason : existing) {
                if (StringUtils.isEmpty(reason)) {
                    continue;
                }
                merged.add(reason.trim());
            }
        }
        String autoReason = HerbalInboundReceiptConstants.AUTO_MARKUP_ADJUSTMENT_REASON;
        // 已含同一全文：不重复追加
        if (merged.contains(autoReason)) {
            return merged;
        }
        merged.add(autoReason);
        return merged;
    }
}
