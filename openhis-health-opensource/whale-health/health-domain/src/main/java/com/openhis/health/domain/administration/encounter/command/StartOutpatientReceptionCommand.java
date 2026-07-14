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
 * 门诊接诊命令：医师开始门诊接诊，可标识是否由计划直接接诊。
 */
@Getter
@Setter
public class StartOutpatientReceptionCommand extends BatchCommand<StartOutpatientReceptionCommand.StartOutpatientReceptionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条接诊项。 */
    @Getter
    @Setter
    public static class StartOutpatientReceptionItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 接诊医师主键。 */
        private Long doctorId;
        /**
         * 接诊医师姓名展示快照（应用层按医师主键解析）；供领域事件携带，驱动读模型医生列展示。
         */
        private String doctorName;
        /** 接诊时间。 */
        private LocalDateTime receptionTime;
        /** 是否由预约/计划直接接诊。 */
        private Boolean directFromPlanned;
    }
}
