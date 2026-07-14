/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.access;

import com.openhis.chs.domain.platformdict.command.SyncPlatformDictFrom1901Command;
import com.openhis.chs.domain.platformdict.result.PlatformDictSyncResult;

/**
 * 【1901】平台字典手动同步编排出站端口。
 *
 * <p><b>职责：</b>BFS 分页拉取 1901 字典、探测区划并写入同步日志。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.foundation.platformdict.workflow.Dict1901SyncWorkflow}</p>
 * <p><b>调用方：</b>{@code InsurancePlatformDictAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P15</p>
 */
public interface PlatformDictSyncAccess {

    /**
     * 执行 1901 字典同步并写入同步日志。
     *
     * @param command 同步命令（含字典类型与区划）
     * @return 同步结果摘要（行数、耗时等）
     */
    PlatformDictSyncResult sync(SyncPlatformDictFrom1901Command command);
}
