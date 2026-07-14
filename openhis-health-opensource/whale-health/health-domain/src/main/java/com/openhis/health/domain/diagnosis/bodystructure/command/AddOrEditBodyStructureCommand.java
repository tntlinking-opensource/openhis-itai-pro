/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.bodystructure.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 新增或编辑身体部位命令。
 */
@Getter
@Setter
public class AddOrEditBodyStructureCommand extends BatchCommand<AddOrEditBodyStructureCommand.AddOrEditBodyStructureItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

/**
 * 新增或编辑身体结构项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class AddOrEditBodyStructureItemCommand {
        /** 主键。 */
        private Long id;
        /** 名称。 */
        private String name;
        /** 上级主键。 */
        private Long parentId;
        /** 状态枚举编码。 */
        private String statusEnum;
    }
}
