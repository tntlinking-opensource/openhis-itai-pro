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
 * 门诊取消接诊命令：医生同意退号，将在诊/暂离/完诊回退为已挂号待诊。
 */
@Getter
@Setter
public class CancelOutpatientReceptionCommand
    extends BatchCommand<CancelOutpatientReceptionCommand.CancelOutpatientReceptionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条取消接诊项。 */
    @Getter
    @Setter
    public static class CancelOutpatientReceptionItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 取消接诊发生时刻；空则领域取当前时间。 */
        private LocalDateTime cancelledAt;
    }
}
