/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.runtime;

import java.util.List;

/**
 * 框架运行时洞察存储接口。
 * 由 whale-runtime 等模块实现，负责同步运行时状态（如远程兼容性校验）。
 */
public interface IRuntimeInsightStore {

    /**
     * 记录单条运行时洞察。
     *
     * @param info 洞察信息
     */
    void record(RuntimeInsightInfo info);

    /**
     * 批量记录运行时洞察。
     *
     * @param infos 洞察信息列表
     */
    default void recordBatch(List<RuntimeInsightInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return;
        }
        for (RuntimeInsightInfo info : infos) {
            record(info);
        }
    }
}
