/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院预入院命令：启动预入院流程并记录待办项编码列表。
 */
@Getter
@Setter
public class StartPreAdmissionCommand extends BatchCommand<StartPreAdmissionCommand.StartPreAdmissionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条预入院项。 */
    @Getter
    @Setter
    public static class StartPreAdmissionItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 预入院待办或检查项编码列表。 */
        private List<String> preAdmissionItems;
        /** 发生时间。 */
        private LocalDateTime occurredAt;
    }
}
