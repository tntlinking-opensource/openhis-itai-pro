/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.access;

import com.openhis.chs.domain.reconciliation.command.QueryClearStatus3205ACommand;
import com.openhis.chs.domain.reconciliation.result.ClearStatus3205ARowResult;

import java.util.List;

/**
 * 【3205A】清算状态查询出站端口。
 *
 * <p><b>职责：</b>定义医保平台 3205A 查询清算申请状态 的 domain 出站契约。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.reconciliation.invoker.Recon3205AInvoker}</p>
 * <p><b>调用方：</b>{@code InsuranceReconciliationAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P12</p>
 */
public interface Recon3205AInvoker {

    /**
     * 调用平台 3205A 查询清算申请状态。
     *
     * @param command 状态查询命令
     * @return 清算状态行列表
     */
    List<ClearStatus3205ARowResult> invoke(QueryClearStatus3205ACommand command);
}
