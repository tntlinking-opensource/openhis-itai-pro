/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 撤销转科/出院过渡态命令：{@code ON_HOLD} → {@code IN_PROGRESS}（GAP-ATD-008；办理出院前）。
 *
 * <p>由 {@code InpatientTransferDischargeHoldOrchestrator} 组装；领域
 * {@code EncounterDomainService#revertTransferOrDischargeHold} 执行。</p>
 */
@Getter
@Setter
public class RevertTransferOrDischargeHoldCommand
    extends BatchCommand<RevertTransferOrDischargeHoldCommand.RevertTransferOrDischargeHoldItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条撤销项。 */
    @Getter
    @Setter
    public static class RevertTransferOrDischargeHoldItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 为 true 表示转科过渡撤销（恢复参与者）；false 表示待出院过渡撤销。 */
        private Boolean transferHold;
        /** 里程碑诊疗申请主键（审计对齐）。 */
        private Long serviceRequestId;
        /** 撤销生效时刻。 */
        private LocalDateTime revertedAt;
    }
}
