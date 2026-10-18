/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.bodystructure.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 停用身体部位命令。
 */
@Getter
@Setter
public class InactiveBodyStructureCommand extends BatchCommand<InactiveBodyStructureCommand.InactiveBodyStructureItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

/**
 * 停用身体结构项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class InactiveBodyStructureItemCommand {
        /** 主键。 */
        private Long id;
    }
}
