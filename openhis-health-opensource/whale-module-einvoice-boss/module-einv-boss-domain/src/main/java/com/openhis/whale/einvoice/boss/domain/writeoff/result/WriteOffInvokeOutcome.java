/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.writeoff.result;

import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;

/**
 * 冲红出站二元组：保留原始响应供 markSuccess 读取 eScarlet 字段。
 */
public record WriteOffInvokeOutcome(BossInvokeResult invokeResult, BossInvoiceIssueResult issueResult) {}
