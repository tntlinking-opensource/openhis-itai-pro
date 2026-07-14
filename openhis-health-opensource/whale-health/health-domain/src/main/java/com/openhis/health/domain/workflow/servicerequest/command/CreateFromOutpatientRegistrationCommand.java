/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 挂号登记建单命令。
 */
@Getter
@Setter
public class CreateFromOutpatientRegistrationCommand extends BatchCommand<CreateFromOutpatientRegistrationCommand.CreateFromOutpatientRegistrationItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建来源门诊挂号项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateFromOutpatientRegistrationItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 活动主键。 */
        private Long activityId;
        /** 患者主键。 */
        private Long patientId;
        /** requester主键。 */
        private Long requesterId;
        /** 类别枚举编码。 */
        private String categoryEnum;
        /** unit编码。 */
        private String unitCode;
    }
}
