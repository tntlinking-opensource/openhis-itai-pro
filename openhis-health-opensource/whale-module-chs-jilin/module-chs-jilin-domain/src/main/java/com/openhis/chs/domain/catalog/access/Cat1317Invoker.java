/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.access;

import com.openhis.chs.domain.catalog.command.Refresh1317Command;
import com.openhis.chs.domain.catalog.result.Refresh1317Result;

/**
 * 【1317】医药机构目录匹配拉取出站端口。
 *
 * <p><b>职责：</b>自动翻页拉取平台匹配信息，upsert 本地镜像，并正向回写院内对码标志。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.catalog.invoker.Cat1317Invoker}</p>
 * <p><b>调用方：</b>{@code InsuranceCatalogWorkbenchAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 */
public interface Cat1317Invoker {

    /**
     * 调用 1317 拉取机构目录匹配并落库、正向回写。
     *
     * @param command 同步命令
     * @return 同步统计
     */
    Refresh1317Result invoke(Refresh1317Command command);
}
