/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.specimendefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量停用样本定义命令。
 */
@Getter
@Setter
public class DeactivateSpecimenDefinitionsCommand extends BatchCommand<DeactivateSpecimenDefinitionsCommand.DeactivateSpecimenDefinitionItemCommand> {

    /**
     * 停用项。
     */
    @Getter
    @Setter
    public static class DeactivateSpecimenDefinitionItemCommand {
        /** 样本定义主键。 */
        private Long id;
    }
}
