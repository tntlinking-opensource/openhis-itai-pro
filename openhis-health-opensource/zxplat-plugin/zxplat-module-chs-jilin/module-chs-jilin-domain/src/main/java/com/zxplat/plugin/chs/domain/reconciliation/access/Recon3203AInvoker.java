/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.access;

import com.zxplat.plugin.chs.domain.reconciliation.command.ApplyJilinClear3203ACommand;
import com.zxplat.plugin.chs.domain.reconciliation.result.ApplyJilinClear3203AResult;

/**
 * 【3203A】吉林清算申请出站端口。
 *
 * <p><b>职责：</b>定义医保平台 3203A 清算申请并落本地清算单 的 domain 出站契约。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.reconciliation.invoker.Recon3203AInvoker}</p>
 * <p><b>调用方：</b>{@code InsuranceReconciliationAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P12</p>
 */
public interface Recon3203AInvoker {

    /**
     * 调用平台 3203A 清算申请并落本地清算单。
     *
     * @param command 清算申请命令
     * @return 平台事件 ID 与本地记录 ID
     */
    ApplyJilinClear3203AResult invoke(ApplyJilinClear3203ACommand command);
}
