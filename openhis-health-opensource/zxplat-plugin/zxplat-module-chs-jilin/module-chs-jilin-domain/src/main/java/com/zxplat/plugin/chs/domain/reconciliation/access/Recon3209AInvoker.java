/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.access;

import com.zxplat.plugin.chs.domain.reconciliation.command.QueryCrossProvinceRecon3209ACommand;
import com.zxplat.plugin.chs.domain.reconciliation.result.CrossProvinceReconRowResult;

import java.util.List;

/**
 * 【3209A】跨省异地对账失败查询出站端口。
 *
 * <p><b>职责：</b>定义医保平台 3209A 查询跨省异地对账失败记录 的 domain 出站契约。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.reconciliation.invoker.Recon3209AInvoker}</p>
 * <p><b>调用方：</b>{@code InsuranceReconciliationAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P12</p>
 */
public interface Recon3209AInvoker {

    /**
     * 调用平台 3209A 查询跨省异地对账失败记录。
     *
     * @param command 跨省对账查询命令
     * @return 失败记录行列表
     */
    List<CrossProvinceReconRowResult> invoke(QueryCrossProvinceRecon3209ACommand command);
}
