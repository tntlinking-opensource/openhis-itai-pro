/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.servicelocationtemplate.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 批量插入发放规则命令。
 */
@Getter
@Setter
public class BatchInsertDistributionRulesCommand extends BatchCommand<BatchInsertDistributionRulesCommand.DistributionRuleItemCommand> {
    /** 机构主键。 */
    private Long organizationId;
    /** 操作人主键。 */
    private Long operatorId;

/**
 * 分发rule项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class DistributionRuleItemCommand {
        /** 项来源表。 */
        private String itemTable;
        /** 分发类别编码。 */
        private String distributionCategoryCode;
        /** def位置主键。 */
        private Long defLocationId;
        /** 项目录主键。 */
        private Long itemDefinitionId;
        /** 耗材种类编码（device_type_code）；空为通配。 */
        private String typeCode;
        /** method编码。 */
        private String methodCode;
        /** 开始时间。 */
        private LocalTime startTime;
        /** 结束时间。 */
        private LocalTime endTime;
        /** 优先。 */
        private Integer priority;
        /** display医嘱。 */
        private Integer displayOrder;
        /** remark。 */
        private String remark;
    }
}
