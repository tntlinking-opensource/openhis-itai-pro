/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial;

import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 日结收款人可选软筛解析：未传收款人时不作 enterer 过滤；传了则解析 enterer 白名单。
 *
 * <p>人选为软筛而非代交账权限门禁（DD §2.6.2 / §2.7）：岗池 + 窗内收费人均可勾选，空选=不按人。</p>
 */
@Component
@RequiredArgsConstructor
public class DailySettlementCashierScopeResolver {

    private final PaymentEntererMatchResolver paymentEntererMatchResolver;

    /**
     * 解析收款人可选软筛（仅 {@code cashierIds}；空=不按人过滤）。
     *
     * @param cashierIds                   多选收款人 Practitioner 主键；空表示不按人过滤
     * @param currentCashierPractitionerId 当前操作员 Practitioner 主键
     * @param sessionUserId                当前登录用户主键（透传 Resolver；BR-OPC-016 后不参与 enterer 扩白名单）
     * @param invalidExCode                参数异常码
     * @return 软筛范围；未选收款人时 entererMatchIds 为空
     */
    public DailySettlementCashierScope resolve(
        List<Long> cashierIds,
        Long currentCashierPractitionerId,
        Long sessionUserId,
        String invalidExCode) {
        List<Long> normalized = this.normalizeSelectedCashierIds(cashierIds);
        if (normalized.isEmpty()) {
            // 默认路径：不按 enterer 硬筛/软筛（DD §2.6.2）
            return new DailySettlementCashierScope(
                currentCashierPractitionerId,
                List.of(),
                List.of());
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(normalized);
        for (Long cashierId : uniqueIds) {
            // 勾选值须为有效 Practitioner 主键（前端岗池/窗内带出均为该口径）
            if (cashierId == null) {
                throw new BusinessException(invalidExCode, "请选择有效的收款人");
            }
        }
        List<Long> selected = new ArrayList<>(uniqueIds);
        List<Long> entererMatchIds =
            this.paymentEntererMatchResolver.resolveEntererMatchIds(selected, sessionUserId);
        return new DailySettlementCashierScope(
            currentCashierPractitionerId,
            selected,
            entererMatchIds);
    }

    /**
     * 规范化勾选列表；空或全空元素则返回空列表。
     *
     * @param cashierIds 请求多选
     * @return 去空后的列表
     */
    private List<Long> normalizeSelectedCashierIds(List<Long> cashierIds) {
        if (cashierIds == null || cashierIds.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Long id : cashierIds) {
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }
}
