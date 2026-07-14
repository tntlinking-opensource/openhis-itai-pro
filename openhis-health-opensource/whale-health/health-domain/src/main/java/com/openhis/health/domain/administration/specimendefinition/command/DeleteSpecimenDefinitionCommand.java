/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.specimendefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除样本定义命令。
 */
@Getter
@Setter
public class DeleteSpecimenDefinitionCommand extends BatchCommand<DeleteSpecimenDefinitionCommand.DeleteSpecimenDefinitionItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 删除项。
     */
    @Getter
    @Setter
    public static class DeleteSpecimenDefinitionItemCommand {
        /** 样本定义主键。 */
        private Long id;
    }
}
