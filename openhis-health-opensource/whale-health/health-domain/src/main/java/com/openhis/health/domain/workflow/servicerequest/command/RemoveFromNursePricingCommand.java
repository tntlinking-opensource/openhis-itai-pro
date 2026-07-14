/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 逻辑删除护士划价诊疗服务申请命令（与 {@code createFromNursePricing} 对称，AS-IP-NUR-005 IF-IP-PRC-06）。
 */
@Getter
@Setter
public class RemoveFromNursePricingCommand extends BatchCommand<RemoveFromNursePricingCommand.Item> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条删除项。
     */
    @Getter
    @Setter
    public static class Item {

        /** 服务申请主键。 */
        private Long id;
    }
}
