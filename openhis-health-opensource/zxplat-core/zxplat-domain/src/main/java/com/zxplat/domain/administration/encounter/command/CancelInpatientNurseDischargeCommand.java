/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消护士办理出院命令：待结算（{@code PENDING}）→ 待出院（{@code ON_HOLD}）或已入科（{@code IN_PROGRESS}）。
 *
 * <p>对齐 DM-ADM-002 §6.1.19a、AS-IP-NUR-002 IF-IP-NUR-ATD-08。</p>
 */
@Getter
@Setter
public class CancelInpatientNurseDischargeCommand
    extends BatchCommand<CancelInpatientNurseDischargeCommand.CancelInpatientNurseDischargeItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条取消办理出院项。 */
    @Getter
    @Setter
    public static class CancelInpatientNurseDischargeItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /**
         * 是否回已入科。
         *
         * <p>为 true：无有效出院嘱，{@code PENDING→IN_PROGRESS} 并清 {@code outWayCode}；
         * 为 false：仍有有效出院嘱，{@code PENDING→ON_HOLD} 保留离院方式。</p>
         */
        private Boolean restoreToInProgress;
    }
}
