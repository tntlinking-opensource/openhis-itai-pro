/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial;

import java.util.List;

/**
 * 日结/交账收款人可选软筛解析结果（DD §2.6.2：人不作漏交/候选硬栏）。
 *
 * @param headerCashierId    当前操作员 Practitioner 主键（提交人/submitter 锚点，可空）
 * @param selectedCashierIds 请求传入的收款人 Practitioner 主键（展示软筛；可空）
 * @param entererMatchIds    payment.enterer_id 软筛白名单（仅 selected 非空时有值；可空）
 */
public record DailySettlementCashierScope(
    Long headerCashierId,
    List<Long> selectedCashierIds,
    List<Long> entererMatchIds) {
}
