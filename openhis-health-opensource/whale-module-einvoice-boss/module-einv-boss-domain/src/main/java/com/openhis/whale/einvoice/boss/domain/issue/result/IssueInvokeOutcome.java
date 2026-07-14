/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.issue.result;

/**
 * 开具出站结果：领域票面 + 原始请求 JSON。
 */
public record IssueInvokeOutcome(BossInvoiceIssueResult issueResult, String payloadJson) {}
