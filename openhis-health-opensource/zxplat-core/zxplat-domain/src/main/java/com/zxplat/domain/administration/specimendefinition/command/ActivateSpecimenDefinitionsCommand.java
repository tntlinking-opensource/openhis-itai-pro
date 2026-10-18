/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.specimendefinition.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量启用样本定义命令。
 */
@Getter
@Setter
public class ActivateSpecimenDefinitionsCommand extends BatchCommand<ActivateSpecimenDefinitionsCommand.ActivateSpecimenDefinitionItemCommand> {

    /**
     * 启用项。
     */
    @Getter
    @Setter
    public static class ActivateSpecimenDefinitionItemCommand {
        /** 样本定义主键。 */
        private Long id;
    }
}
