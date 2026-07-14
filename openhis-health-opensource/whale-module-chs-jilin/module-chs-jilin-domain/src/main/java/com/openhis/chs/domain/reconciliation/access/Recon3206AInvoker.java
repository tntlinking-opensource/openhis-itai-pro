/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.access;

import com.openhis.chs.domain.reconciliation.command.QueryClearOrg3206ACommand;
import com.openhis.chs.domain.reconciliation.result.ClearOrg3206AResult;

/**
 * 【3206A】清算经办机构查询出站端口。
 *
 * <p><b>职责：</b>定义医保平台 3206A 分页查询清算经办机构 的 domain 出站契约。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.reconciliation.invoker.Recon3206AInvoker}</p>
 * <p><b>调用方：</b>{@code InsuranceReconciliationAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P12</p>
 */
public interface Recon3206AInvoker {

    /**
     * 调用平台 3206A 分页查询清算经办机构。
     *
     * @param command 机构查询命令
     * @return 分页结果
     */
    ClearOrg3206AResult invoke(QueryClearOrg3206ACommand command);
}
