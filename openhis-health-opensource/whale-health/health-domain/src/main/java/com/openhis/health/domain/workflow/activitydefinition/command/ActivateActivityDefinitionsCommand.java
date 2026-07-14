/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.activitydefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量启用诊疗项目命令。
 *
 * <p>支持单条与批量双形态输入：items 优先，item 次之。</p>
 */
@Getter
@Setter
public class ActivateActivityDefinitionsCommand extends BatchCommand<ActivateActivityDefinitionsCommand.ActivateActivityDefinitionsItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 单条启用子命令。
     */
    @Getter
    @Setter
    public static class ActivateActivityDefinitionsItemCommand {
        /** 待启用诊疗项目主键列表。 */
        private List<Long> idList;
        /** 启用原因字典码。 */
        private String reasonCode;
    }
}
