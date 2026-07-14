/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.access;

import com.openhis.chs.domain.inventory.command.RetryPendingInvUploadsCommand;
import com.openhis.chs.domain.inventory.result.RetryPendingInvUploadsResult;

/**
 * 【3501–3506】失败/待报记录重试编排出站端口。
 *
 * <p><b>职责：</b>扫描可重试行并重放 3501–3506 上报。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.inventory.workflow.InventoryRetryWorkflow}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
public interface InventoryRetryAccess {

    /**
     * 重试失败或待报的 3501–3506 记录。
     *
     * @param command 重试命令（含筛选条件）
     * @return 重试结果摘要
     */
    RetryPendingInvUploadsResult retry(RetryPendingInvUploadsCommand command);
}
