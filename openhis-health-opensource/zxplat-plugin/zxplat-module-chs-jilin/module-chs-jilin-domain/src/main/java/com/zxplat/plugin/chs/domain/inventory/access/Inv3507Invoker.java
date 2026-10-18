/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.access;

import com.zxplat.plugin.chs.domain.inventory.command.InvDelete3507Command;
import com.zxplat.plugin.chs.domain.inventory.result.InvDelete3507Result;

/**
 * 【3507】进销存数据删除出站端口。
 *
 * <p><b>职责：</b>3507 删除平台进销存数据并更新本地流水状态。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.inventory.invoker.Inv3507Invoker}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
public interface Inv3507Invoker {

    /**
     * 调用平台 3507 删除进销存数据。
     *
     * @param command 删除命令（含批次号等）
     * @return 删除结果
     */
    InvDelete3507Result invoke(InvDelete3507Command command);
}
