/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.access;

import com.zxplat.plugin.chs.domain.reconciliation.command.RevokeJilinClear3204ACommand;

/**
 * 【3204A】吉林清算撤销出站端口。
 *
 * <p><b>职责：</b>定义医保平台 3204A 撤销清算申请并更新本地状态 的 domain 出站契约。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.reconciliation.invoker.Recon3204AInvoker}</p>
 * <p><b>调用方：</b>{@code InsuranceReconciliationAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P12</p>
 */
public interface Recon3204AInvoker {

    /**
     * 调用平台 3204A 撤销清算申请并更新本地状态。
     *
     * @param command 撤销命令
     * @return 无；失败时抛出业务异常
     */
    void invoke(RevokeJilinClear3204ACommand command);
}
