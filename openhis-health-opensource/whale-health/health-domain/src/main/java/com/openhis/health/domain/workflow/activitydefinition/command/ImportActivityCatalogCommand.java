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
 * 批量导入诊疗目录命令。
 *
 * <p>支持单条与批量双形态输入：items 优先，item 次之。</p>
 */
@Getter
@Setter
public class ImportActivityCatalogCommand extends BatchCommand<ImportActivityCatalogCommand.ImportActivityCatalogItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 单条导入子命令。
     */
    @Getter
    @Setter
    public static class ImportActivityCatalogItemCommand {
        private List<ActivityCatalogRowCommand> rows;
        private String mode;
    }

    /**
     * 导入行子命令。
     *
     * <p>本类型作为导入行载体，字段与创建/维护逻辑保持一致。</p>
     */
    @Getter
    @Setter
    public static class ActivityCatalogRowCommand {
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
     * 子项子命令（与创建命令一致，但为本命令独立类型，避免跨命令依赖）。
     */
    @Getter
    @Setter
    public static class ActivityDefinitionChildItemCommand {
        private Long childrenId;
        private Integer childrenNum;
        private String childrenName;
    }
}

