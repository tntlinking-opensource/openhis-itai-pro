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
 * 住院出院命令：办理出院并记录出院方式、时间与摘要。
 */
@Getter
@Setter
public class DischargeInpatientEncounterCommand extends BatchCommand<DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条出院项。 */
    @Getter
    @Setter
    public static class DischargeInpatientEncounterItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 出院方式编码。 */
        private String outWayCode;
        /** 出院时间。 */
        private LocalDateTime dischargedAt;
        /** 出院摘要。 */
        private String summary;
    }
}
