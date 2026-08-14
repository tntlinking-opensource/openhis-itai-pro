/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.issue.result;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;

import java.math.BigDecimal;
import java.util.List;

/**
 * 拆票后单张开具结果（含本张明细与出站报文）。
 *
 * <p>票种清单行超上限时，一笔 health 票据对应多张博思票；每张对应一条本记录，
 * 落库为独立 {@code einv_boss_document}，列表才能全部可见。</p>
 *
 * @param partIndex 从 0 起的分片序号（0=主票，busNo 与 health 对齐）
 * @param busNo 本张业务流水号（主票=原 busNo；续票=busNo-2、busNo-3…）
 * @param totalAmt 本张开票额
 * @param lines 本张明细（保序；与出站 listDetail 一致）
 * @param issueResult 博思蓝票票面
 * @param payloadJson 本张出站请求 JSON
 */
public record IssuePartOutcome(
        int partIndex,
        String busNo,
        BigDecimal totalAmt,
        List<EinvBossLine> lines,
        BossInvoiceIssueResult issueResult,
        String payloadJson) {
}
