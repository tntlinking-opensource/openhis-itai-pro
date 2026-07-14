/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 护士站办理出院命令：待出院（{@code ON_HOLD}）→ 待出院结算（{@code PENDING}）。
 *
 * <p>对齐 DM-ADM-002 §6.1.22、AS-IP-NUR-002 IF-IP-NUR-ATD-05。</p>
 */
@Getter
@Setter
public class ExecuteInpatientNurseDischargeCommand
    extends BatchCommand<ExecuteInpatientNurseDischargeCommand.ExecuteInpatientNurseDischargeItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条护士办理出院项。 */
    @Getter
    @Setter
    public static class ExecuteInpatientNurseDischargeItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 出院时间（可选；缺省由领域服务取当前时间写入 {@code end_time}）。 */
        private LocalDateTime dischargedAt;
    }
}
