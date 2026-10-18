/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial;

import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.financial.cashiersettlement.queries.model.PreviewCashierSettlementQueryModel;
import com.zxplat.domain.financial.cashiersettlement.queries.model.UnsettledPaymentPreviewItemQueryModel;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 收款日结 org → location 范围解析与交账头人员/金额快照辅助（DD §2.6 / §2.7）。
 */
@Component
@RequiredArgsConstructor
public class DailySettlementOrgLocationScopeSupport {

    /** 科室下启用 location 读侧（B.6.4 由应用层解析后下推 Payment QS）。 */
    private final LocationQueryService locationQueryService;

    /**
     * 解析科室下全部启用 location 主键（可选工具；<strong>收款日结候选/漏交不得调用本方法做 payment 过滤</strong>）。
     *
     * <p>收款不绑款台、payment 无科室列；误用 {@code location_id IN (...)} 会把候选扫空。
     * 药房日结范围键直接用请求 {@code locationId}，亦不经本方法。</p>
     *
     * @param orgId 交账科室主键
     * @return location 主键列表；org 为空或无 location 时返回空列表
     */
    public List<Long> resolveLocationIdsForOrg(Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        List<Location> locations = this.locationQueryService.listActiveLocationsByOrganization(orgId);
        List<Long> locationIds = new ArrayList<>();
        for (Location location : locations) {
            if (location != null && location.getId() != null) {
                locationIds.add(location.getId());
            }
        }
        return locationIds;
    }

    /**
     * Practitioner 主键列表 → 交账头 {@code practitioner_ids} 逗号分隔文本。
     *
     * @param practitionerIds 窗内 enterer 去重列表
     * @return 逗号分隔；空输入返回空串
     */
    public String joinPractitionerIds(List<Long> practitionerIds) {
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Long practitionerId : practitionerIds) {
            if (practitionerId == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(",");
            }
            builder.append(practitionerId);
        }
        return builder.toString();
    }

    /**
     * 按勾选 payment 汇总待交账实收净额（{@code tendered−returned}，与 handover 行 {@code netAmount} 同源）。
     *
     * <p><strong>勿</strong>用于头表 {@code total_income}：费用日结总收入须走门/住
     * {@code *DailySettlementMapper#resolveSelectedSettlementTotalIncome}
     *（与预览 {@code handoverSummary.totalIncome} 同 {@code buildDisplayBundle} 管线）。</p>
     *
     * @param lockedPreview      提交前锁定的未交账预览
     * @param selectedPaymentIds 本次勾选挂载主键
     * @return 勾选合计实收净额
     */
    public BigDecimal sumSelectedPaymentNetIncome(
        PreviewCashierSettlementQueryModel lockedPreview,
        List<Long> selectedPaymentIds) {
        if (lockedPreview == null || selectedPaymentIds == null || selectedPaymentIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Set<Long> selectedSet = new LinkedHashSet<>(selectedPaymentIds);
        BigDecimal total = BigDecimal.ZERO;
        List<UnsettledPaymentPreviewItemQueryModel> items = lockedPreview.getItems();
        if (items == null) {
            return total;
        }
        for (UnsettledPaymentPreviewItemQueryModel item : items) {
            if (item == null || item.getPaymentId() == null) {
                continue;
            }
            if (!selectedSet.contains(item.getPaymentId())) {
                continue;
            }
            BigDecimal netAmount = item.getNetAmount();
            if (netAmount == null) {
                netAmount = BigDecimal.ZERO;
            }
            total = total.add(netAmount);
        }
        return total;
    }

    /**
     * 漏交硬栏提示文案：截止前存在窗外更早未交账时返回可读说明。
     *
     * @param gapHardBlockEarliestAt 最早漏交业务时刻；无漏交时为 {@code null}
     * @return 提示文案；无漏交时为 {@code null}
     */
    public String buildGapHardBlockMessage(LocalDateTime gapHardBlockEarliestAt) {
        if (gapHardBlockEarliestAt == null) {
            return null;
        }
        return "存在截止前未交账流水，最早时刻为 "
            + gapHardBlockEarliestAt.format(GeneralSharedConstants.DATE_TIME_FORMATTER)
            + "，请将日结起点调整至不晚于该时刻";
    }
}
