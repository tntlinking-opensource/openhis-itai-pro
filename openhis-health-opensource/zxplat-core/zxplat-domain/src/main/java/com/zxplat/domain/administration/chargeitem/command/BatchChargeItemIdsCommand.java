/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 批量费用明细主键命令：用于按 ID 集合触发批量领域动作（如批量状态变更），带操作人。
 *
 * <p>结算/退费完成时：{@code operatorId} = 收费人（{@code adm_practitioner.id}），
 * {@code billDate} = 收费时间（与 payment {@code bill_date} 同值），写入 ChargeItem {@code enterer_*}。</p>
 */
@Getter
@Setter
public class BatchChargeItemIdsCommand extends BatchCommand<BatchChargeItemIdsCommand.Item> {
    /** 操作人主键（结算/退费完成时为收费人 practitionerId）。 */
    private Long operatorId;

    /**
     * 结算/退费业务时刻（与 payment {@code bill_date} 对齐）；缺省时领域取当前时刻写入 {@code entered_date}。
     */
    private LocalDateTime billDate;

    /**
     * 单条：费用明细主键。
     */
    @Getter
    @Setter
    public static class Item {
        /** 费用明细主键。 */
        private Long chargeItemId;
    }
}
