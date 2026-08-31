/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.issue.result;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;

import java.util.List;
import java.util.Objects;

/**
 * 开具出站结果：单票（医院不允许拆票）。
 *
 * <p>{@link #issueResult()} / {@link #payloadJson()} 取首张，供 health 回写与任务收尾；
 * {@link #parts()} 恒为单元素列表，由 Worker 落一条 {@code einv_boss_document}。</p>
 */
public record IssueInvokeOutcome(List<IssuePartOutcome> parts) {

    /**
     * @param parts 至少一张；首张为主票
     */
    public IssueInvokeOutcome {
        Objects.requireNonNull(parts, "parts");
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("parts must not be empty");
        }
    }

    /**
     * 单张开具（未拆票）工厂。
     *
     * @param issueResult 票面
     * @param payloadJson 请求 JSON
     * @param lines 本张明细
     * @return 仅含一张分片的结果
     */
    public static IssueInvokeOutcome ofSingle(
            BossInvoiceIssueResult issueResult, String payloadJson, List<EinvBossLine> lines) {
        IssuePartOutcome part = new IssuePartOutcome(
                0,
                issueResult.getBusNo(),
                issueResult.getTotalAmt(),
                lines == null ? List.of() : List.copyOf(lines),
                issueResult,
                payloadJson);
        return new IssueInvokeOutcome(List.of(part));
    }

    /** 主票票面（health 回写仍用首张）。 */
    public BossInvoiceIssueResult issueResult() {
        return parts.get(0).issueResult();
    }

    /** 主票出站请求 JSON。 */
    public String payloadJson() {
        return parts.get(0).payloadJson();
    }
}
