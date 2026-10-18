/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.conditiontemplate.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 解绑诊断模板命令（支持单条与批量）。
 */
@Getter
@Setter
public class UnbindConditionTemplateCommand extends BatchCommand<UnbindConditionTemplateCommand.UnbindConditionTemplateItemCommand> {
    /**
     * 操作人标识。
     */
    private Long operatorId;

    @Getter
    @Setter
    public static class UnbindConditionTemplateItemCommand {
        /**
         * 模板行主键。
         */
        private Long templateId;
    }
}
