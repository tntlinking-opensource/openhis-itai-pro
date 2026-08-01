/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.command;

import lombok.Data;

/**
 * 应用通知文件命令（W-03）。
 */
@Data
public class ApplyNoticeDocCommand {

    /** 租户。 */
    private Long tenantId;

    /** 文件头主键。 */
    private Long docId;

    /** 操作人。 */
    private Long operatorId;

    /**
     * 每批落库行数；空或 ≤0 时用领域默认（短事务分批）。
     */
    private Integer batchSize;

    /**
     * 进度基数：多文件串行时，前面文件已处理的行数；单文件为 0。
     */
    private int progressBase;

    /**
     * 进度分母：多文件时为全部待处理行合计；空则用本文件待处理行数。
     */
    private Integer progressTotalOverride;

    /**
     * 可选进度回调：(已处理行数, 总行数)。
     * <p>异步应用轮询用；同步调用可留空。不参与持久化。</p>
     */
    private transient ApplyProgressCallback progressCallback;

    /**
     * 应用进度回调。
     */
    @FunctionalInterface
    public interface ApplyProgressCallback {
        /**
         * @param processed 已处理行数（含当前）
         * @param total     总行数
         */
        void onProgress(int processed, int total);
    }
}
