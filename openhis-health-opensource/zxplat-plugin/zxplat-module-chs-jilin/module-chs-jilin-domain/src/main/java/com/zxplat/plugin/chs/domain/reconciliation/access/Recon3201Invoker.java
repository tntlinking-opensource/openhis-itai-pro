/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.access;

import com.zxplat.plugin.chs.domain.reconciliation.command.ReconDaily3201Command;
import com.zxplat.plugin.chs.domain.reconciliation.result.ReconDaily3201Result;

/**
 * 【3201】日对账出站端口。
 *
 * <p><b>职责：</b>定义医保平台 3201 日对账并落本地日账 的 domain 出站契约。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.reconciliation.invoker.Recon3201Invoker}</p>
 * <p><b>调用方：</b>{@code InsuranceReconciliationAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P12</p>
 */
public interface Recon3201Invoker {

    /**
     * 调用平台 3201 日对账并落本地日账。
     *
     * @param command 日对账命令
     * @return 对账结果摘要与本地记录 ID
     */
    ReconDaily3201Result invoke(ReconDaily3201Command command);
}
