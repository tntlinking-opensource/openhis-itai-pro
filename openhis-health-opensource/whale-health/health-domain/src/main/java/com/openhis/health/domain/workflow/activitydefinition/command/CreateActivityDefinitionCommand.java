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
        /** 诊疗项目分类编码（字典）。 */
        private String categoryCode;
        /** 业务编码（主数据唯一键）。 */
        private String busNo;
        /** 项目名称。 */
        private String name;
        /** 销售/包装单位编码。 */
        private String packageUnitCode;
        /** 项目版本号（目录变更追踪）。 */
        private String activityVersion;
        /** 商品名/商用名。 */
        private String merchandiseName;
        /** 默认门诊/就诊单位编码。 */
        private String defEncounterUnitCode;
        /** 默认住院单位编码。 */
        private String defInhospitalUnitCode;
        /** 项目类型持久化码。 */
        private String typeEnum;
        /** 生命周期状态持久化码。 */
        private String statusEnum;
        /** 项目说明文本。 */
        private String descriptionText;
        /** 采集/操作部位编码。 */
        private String bodySiteCode;
        /** 标本类型编码。 */
        private String specimenCode;
        /** 所属科室/机构单元主键。 */
        private Long orgId;
        /** 默认执行位置主键。 */
        private Long locationId;
        /** 关联计价/分方规则标识。 */
        private Integer ruleId;
        /** 是否参与定价（0/1）。 */
        private Boolean pricingFlag;
        /** 医保标记（0/1）。 */
        private Boolean ybFlag;
        /** 医保编码。 */
        private String ybNo;
        /** 医保对码标记（0/1）。 */
        private Boolean ybMatchFlag;
        /** 医保等级。 */
        private Integer chrgitmLv;
        /** 病案结算类型编码。 */
        private String mrSettlementTypeCode;
        /** 子项/套餐明细行。 */
        private List<ActivityDefinitionChildItemCommand> children;
        /** 子项 JSON 快照（导入/批量场景）。 */
        private String childrenJson;
        /** 是否套餐/含子项标志。 */
        private Boolean childrenFlag;
        /** 适用性别持久化码（{@code ActivityPermittedGender}）。 */
        private String permittedGenderEnum;
        /** 目录展示顺序（数值越小越靠前）。 */
        private Integer sortOrder;
    }

    /**
     * 子项子命令（仅承载业务字段，不携带 operatorId 等顶层审计字段）。
     */
    @Getter
    @Setter
    public static class ActivityDefinitionChildItemCommand {
        /** 子项诊疗项目主键 {@code wkf_activity_definition.id}。 */
        private Long childrenId;
        /** 子项数量/次数。 */
        private Integer childrenNum;
        /** 子项展示名称（冗余）。 */
        private String childrenName;
    }
}
