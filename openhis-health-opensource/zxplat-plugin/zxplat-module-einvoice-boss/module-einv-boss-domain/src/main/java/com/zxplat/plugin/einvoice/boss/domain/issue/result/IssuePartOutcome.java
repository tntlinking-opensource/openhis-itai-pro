/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.issue.result;

import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossLine;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单张开具结果（含本张明细与出站报文）。
 *
 * <p>医院要求一结算一票；{@code listDetail} 全量、{@code chargeDetail} 按大类聚合后单次出站。</p>
 *
 * @param partIndex 从 0 起的分片序号（现恒为 0）
 * @param busNo 业务流水号（与 health 对齐）
 * @param totalAmt 开票额
 * @param lines 明细（保序；与出站 listDetail 一致）
 * @param issueResult 博思蓝票票面
 * @param payloadJson 出站请求 JSON
 */
public record IssuePartOutcome(
        int partIndex,
        String busNo,
        BigDecimal totalAmt,
        List<EinvBossLine> lines,
        BossInvoiceIssueResult issueResult,
        String payloadJson) {
}
