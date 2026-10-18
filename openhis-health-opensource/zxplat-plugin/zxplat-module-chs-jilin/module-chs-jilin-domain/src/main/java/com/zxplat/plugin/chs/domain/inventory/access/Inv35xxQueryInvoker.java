/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.access;

import com.zxplat.plugin.chs.domain.inventory.queries.QueryPlatformInvQuery;
import com.zxplat.plugin.chs.domain.inventory.result.PlatformInvQueryResult;

/**
 * 【3508–3513】平台进销存查询出站端口。
 *
 * <p><b>职责：</b>3508–3513 只读查询平台进销存，不落库。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.inventory.invoker.Inv35xxQueryInvoker}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
public interface Inv35xxQueryInvoker {

    /**
     * 3508 平台进销存查询（只读）。
     *
     * @param query 查询条件（含 contractBusNo、批次等）
     * @return 平台返回的进销存查询结果
     */
    PlatformInvQueryResult query3508(QueryPlatformInvQuery query);

    /**
     * 3509 平台进销存查询（只读）。
     *
     * @param query 查询条件（含 contractBusNo、批次等）
     * @return 平台返回的进销存查询结果
     */
    PlatformInvQueryResult query3509(QueryPlatformInvQuery query);

    /**
     * 3510 平台进销存查询（只读）。
     *
     * @param query 查询条件（含 contractBusNo、批次等）
     * @return 平台返回的进销存查询结果
     */
    PlatformInvQueryResult query3510(QueryPlatformInvQuery query);

    /**
     * 3511 平台进销存查询（只读）。
     *
     * @param query 查询条件（含 contractBusNo、批次等）
     * @return 平台返回的进销存查询结果
     */
    PlatformInvQueryResult query3511(QueryPlatformInvQuery query);

    /**
     * 3512 平台进销存查询（只读）。
     *
     * @param query 查询条件（含 contractBusNo、批次等）
     * @return 平台返回的进销存查询结果
     */
    PlatformInvQueryResult query3512(QueryPlatformInvQuery query);

    /**
     * 3513 平台进销存查询（只读）。
     *
     * @param query 查询条件（含 contractBusNo、批次等）
     * @return 平台返回的进销存查询结果
     */
    PlatformInvQueryResult query3513(QueryPlatformInvQuery query);
}
