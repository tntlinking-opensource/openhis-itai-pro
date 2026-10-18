/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.bodystructure.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 启用身体部位命令。
 */
@Getter
@Setter
public class ActiveBodyStructureCommand extends BatchCommand<ActiveBodyStructureCommand.ActiveBodyStructureItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

/**
 * 启用身体结构项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ActiveBodyStructureItemCommand {
        /** 主键。 */
        private Long id;
    }
}
