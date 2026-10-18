/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.access;

import com.zxplat.plugin.chs.domain.inventory.command.InitializeInventoryCommand;
import com.zxplat.plugin.chs.domain.inventory.result.InitializeInventoryFillMeta;

/**
 * 批初始化配对填充出站端口。
 *
 * <p><b>职责：</b>从 HIS 库存组装 3501+3502 配对行，供初始化编排消费。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.inventory.support.InventoryInitializeUploadContextLoader}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
public interface InventoryInitializeFillAccess {

    /**
     * 从 HIS 库存填充 3501+3502 配对行。
     *
     * @param command 初始化命令（含库房与分页参数）
     * @return 配对元数据与待上报行
     */
    InitializeInventoryFillMeta fillPairs(InitializeInventoryCommand command);
}
