/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 护士划价建单命令。
 */
@Getter
@Setter
public class CreateFromNursePricingCommand extends BatchCommand<CreateFromNursePricingCommand.CreateFromNursePricingItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建来源护理计价项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateFromNursePricingItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 活动主键。 */
        private Long activityId;
        /** 数量。 */
        private BigDecimal quantity;
        /** unit编码。 */
        private String unitCode;
        /** 患者主键。 */
        private Long patientId;
        /** requester主键。 */
        private Long requesterId;
        /** 执行位置。 */
        private Long performLocation;
        /** 上级医嘱来源表（补费路径；直接划价须为空）。 */
        private String basedOnTable;
        /** 上级医嘱来源 ID（补费路径；直接划价须为空）。 */
        private Long basedOnId;
    }
}
