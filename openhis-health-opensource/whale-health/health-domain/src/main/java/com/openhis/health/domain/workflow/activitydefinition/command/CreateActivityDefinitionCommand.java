/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.workflow.activitydefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 新建诊疗项目命令。
 *
 * <p>支持单条与批量双形态输入：items 优先，item 次之。</p>
 */
@Getter
@Setter
public class CreateActivityDefinitionCommand extends BatchCommand<CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 单行诊疗项目创建子命令。
     */
    @Getter
    @Setter
    public static class CreateActivityDefinitionItemCommand {
        private String categoryCode;
        private String busNo;
        private String name;
        private String packageUnitCode;
        private String activityVersion;
        private String merchandiseName;
        private String defEncounterUnitCode;
        private String defInhospitalUnitCode;
        private String typeEnum;
        private String statusEnum;
        private String descriptionText;
        private String bodySiteCode;
        private String specimenCode;
        private Long orgId;
        private Long locationId;
        private Integer ruleId;
        private Boolean pricingFlag;
        private Boolean ybFlag;
        private String ybNo;
        private Boolean ybMatchFlag;
        private Integer chrgitmLv;
        private List<ActivityDefinitionChildItemCommand> children;
        private String childrenJson;
        private Boolean childrenFlag;
    }

    /**
     * 子项子命令（仅承载业务字段，不携带 operatorId 等顶层审计字段）。
     */
    @Getter
    @Setter
    public static class ActivityDefinitionChildItemCommand {
        private Long childrenId;
        private Integer childrenNum;
        private String childrenName;
    }
}

