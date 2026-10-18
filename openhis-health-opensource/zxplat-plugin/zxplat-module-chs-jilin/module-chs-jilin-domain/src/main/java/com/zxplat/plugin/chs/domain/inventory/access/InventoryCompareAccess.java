/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.access;

import com.zxplat.plugin.chs.domain.inventory.queries.CompareLocalVsPlatformQuery;
import com.zxplat.plugin.chs.domain.inventory.result.CompareLocalVsPlatformResult;

/**
 * 本地 vs 平台进销存差异比对编排出站端口。
 *
 * <p><b>职责：</b>本地 yb_inv_upload 与平台 3508–3513 行级差异比对。</p>
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.chs.bridge.inventory.workflow.InventoryCompareWorkflow}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.zxplat.plugin.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
public interface InventoryCompareAccess {

    /**
     * 比对本地流水与平台 3508–3513 查询结果。
     *
     * @param query 比对查询（含时间范围与 infno）
     * @return 差异行与统计摘要
     */
    CompareLocalVsPlatformResult compare(CompareLocalVsPlatformQuery query);
}
