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
 * 门诊完诊命令：结束门诊就诊并写入摘要。
 */
@Getter
@Setter
public class CompleteOutpatientEncounterCommand extends BatchCommand<CompleteOutpatientEncounterCommand.CompleteOutpatientEncounterItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条完诊项。 */
    @Getter
    @Setter
    public static class CompleteOutpatientEncounterItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 完诊时间。 */
        private LocalDateTime completedAt;
        /** 就诊小结或摘要。 */
        private String summary;
    }
}
