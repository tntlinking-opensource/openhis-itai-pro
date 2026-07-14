/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.access;

import com.openhis.chs.domain.inventory.command.InitializeInventoryCommand;
import com.openhis.chs.domain.inventory.result.InitializeInventoryResult;

/**
 * 【3501+3502】批初始化编排出站端口。
 *
 * <p><b>职责：</b>逐对 3501 盘存 + 3502 变更的批初始化编排。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.inventory.workflow.InventoryInitializeWorkflow}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
public interface InventoryInitializeAccess {

    /**
     * 执行批初始化：逐对 3501 盘存 + 3502 变更上报。
     *
     * @param command 初始化命令（含配对行与契约信息）
     * @return 初始化结果摘要
     */
    InitializeInventoryResult initialize(InitializeInventoryCommand command);
}
