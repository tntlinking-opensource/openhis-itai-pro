/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.access;

import com.openhis.chs.domain.catalog.command.Refresh1312Command;

/**
 * 【1312】国家目录分页拉取出站端口。
 *
 * <p><b>职责：</b>分页拉取国家目录并 upsert 至本地镜像。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.catalog.invoker.Cat1312Invoker}</p>
 * <p><b>调用方：</b>{@code InsuranceCatalogAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P13</p>
 */
public interface Cat1312Invoker {

    /**
     * 调用 1312 分页拉取国家目录并 upsert 至本地镜像。
     *
     * @param command 刷新命令
     * @return 本次 upsert 行数
     */
    int invoke(Refresh1312Command command);
}
