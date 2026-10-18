/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量删除（逻辑删除）诊疗项目命令。
 */
@Getter
@Setter
public class DeleteActivityDefinitionsCommand
    extends BatchCommand<DeleteActivityDefinitionsCommand.DeleteActivityDefinitionsItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 单条删除子命令。
     */
    @Getter
    @Setter
    public static class DeleteActivityDefinitionsItemCommand {
        /** 待逻辑删除诊疗项目主键列表。 */
        private List<Long> idList;
    }
}
