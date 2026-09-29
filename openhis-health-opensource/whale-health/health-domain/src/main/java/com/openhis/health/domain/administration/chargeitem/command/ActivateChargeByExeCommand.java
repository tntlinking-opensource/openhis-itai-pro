/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行后激活收费明细命令（临时医嘱：DRAFT → PENDING_SETTLEMENT 并挂 procedureId）。
 */
@Getter
@Setter
public class ActivateChargeByExeCommand extends BatchCommand<ActivateChargeByExeCommand.Item> {

    /**
     * 单条执行激活项。
     */
    @Getter
    @Setter
    public static class Item {
        /** 收费明细主键。 */
        private Long chargeItemId;
        /** 执行记录主键。 */
        private Long procedureId;
        /** 执行发生时刻。 */
        private LocalDateTime performedAt;
    }
}
