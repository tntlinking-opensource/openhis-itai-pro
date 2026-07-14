/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消执行逆激活收费明细命令（临时医嘱：PENDING_SETTLEMENT → PLANNED 并清 procedureId，AS-IP-NUR-012 BR-IP-CXL-008）。
 */
@Getter
@Setter
public class DeactivateChargeByExeCommand extends BatchCommand<DeactivateChargeByExeCommand.Item> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条逆激活项。
     */
    @Getter
    @Setter
    public static class Item {
        /** 收费明细主键。 */
        private Long chargeItemId;
    }
}
