/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.access;

import com.openhis.chs.domain.audit.command.AuditFeedback3103Command;

/**
 * 【3103】事前提醒服务反馈出站端口。
 *
 * <p><b>职责：</b>将人工处置（继续/返回修改）回传平台；成功无业务输出。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.audit.invoker.Audit3103Invoker}</p>
 * <p><b>调用方：</b>试调 AppService、W2 嵌入对话框</p>
 * <p><b>架构边界：</b>application 只依赖本接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 */
public interface Audit3103Invoker {

    /**
     * 调用平台 3103 反馈处置结果。
     *
     * @param command warnType + warns（warnRsltId=jrdId）
     */
    void invoke(AuditFeedback3103Command command);
}
