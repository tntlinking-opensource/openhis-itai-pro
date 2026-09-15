/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 门诊退费申请撤销：作废待退耗（{@code PENDING_REFUND}）对冲发放命令。
 *
 * <p>原正向已发完成发放保持不动；仅软删本批待退对冲行。</p>
 */
@Getter
@Setter
public class VoidPendingRefundDeviceDispenseCommand
    extends BatchCommand<VoidPendingRefundDeviceDispenseCommand.Item> {

    /** 操作人标识（批量条目共用，写入删除审计）。 */
    private Long operatorId;

    /**
     * 单条待退发放作废子命令。
     */
    @Getter
    @Setter
    public static class Item {
        /** 待退耗发放单主键（须为 {@code PENDING_REFUND}）。 */
        private Long id;
    }
}
