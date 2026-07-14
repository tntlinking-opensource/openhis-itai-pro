/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.bodystructure.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除身体部位命令。
 */
@Getter
@Setter
public class DeleteBodyStructureCommand extends BatchCommand<DeleteBodyStructureCommand.DeleteBodyStructureItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

/**
 * 删除身体结构项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class DeleteBodyStructureItemCommand {
        /** 主键。 */
        private Long id;
    }
}
