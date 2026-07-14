/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 门诊药房拒绝耗材退药（待退药态推进为已拒绝）命令。
 */
@Getter
@Setter
public class DeclinePendingRefundDeviceDispenseCommand extends BatchCommand<DeclinePendingRefundDeviceDispenseCommand.Item> {

    /** 操作人标识（批量条目共用）。 */
    private Long operatorId;

    /**
     * 单条拒绝子命令。
     */
    @Getter
    @Setter
    public static class Item {
        /** 发放单标识。 */
        private Long id;
        /** 拒绝原因（手写文本）。 */
        private String declineReasonText;
        /** 执行药师主键（写入 {@code performer_id}）。 */
        private Long practitionerId;
    }
}
