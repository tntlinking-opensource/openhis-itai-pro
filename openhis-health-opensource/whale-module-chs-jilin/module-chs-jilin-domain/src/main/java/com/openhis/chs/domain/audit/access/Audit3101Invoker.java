/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.access;

import com.openhis.chs.domain.audit.command.AuditAnalyze3101Command;
import com.openhis.chs.domain.audit.result.AuditAnalyzeResult;

/**
 * 【3101】明细审核事前提醒分析出站端口。
 *
 * <p><b>职责：</b>将就诊树提交省平台规则引擎，返回违规结果列表。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.audit.invoker.Audit3101Invoker}</p>
 * <p><b>调用方：</b>试调 AppService、W2 Extension</p>
 * <p><b>架构边界：</b>application 只依赖本接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 */
public interface Audit3101Invoker {

    /**
     * 调用平台 3101 事前分析。
     *
     * @param command 含 syscode、trigScen、patient 树
     * @return 违规结果；无违规时 violations 为空列表
     */
    AuditAnalyzeResult invoke(AuditAnalyze3101Command command);
}
