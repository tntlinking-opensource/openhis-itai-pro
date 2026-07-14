/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.activitydefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 同步医保对码回写命令。
 *
 * <p>支持单条与批量双形态输入：items 优先，item 次之。</p>
 */
@Getter
@Setter
public class SyncActivityYbMappingCommand extends BatchCommand<SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 单条医保对码回写子命令。
     */
    @Getter
    @Setter
    public static class SyncActivityYbMappingItemCommand {
        /** 诊疗项目主键 {@code wkf_activity_definition.id}。 */
        private Long activityId;
        /** 医保编码。 */
        private String ybNo;
        /** 医保对码标记（0/1）。 */
        private Boolean ybMatchFlag;
        /** 医保等级。 */
        private Integer chrgitmLv;
    }
}
