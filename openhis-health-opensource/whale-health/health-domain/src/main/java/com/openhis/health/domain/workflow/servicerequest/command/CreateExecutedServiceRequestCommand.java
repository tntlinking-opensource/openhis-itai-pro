/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建执行完成申请命令（执行复制/药品绑定派生）。
 */
@Getter
@Setter
public class CreateExecutedServiceRequestCommand extends BatchCommand<CreateExecutedServiceRequestCommand.CreateExecutedServiceRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建已执行服务请求项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateExecutedServiceRequestItemCommand {
        /** basedon来源表。 */
        private String basedOnTable;
        /** basedon主键。 */
        private Long basedOnId;
        /** 生成source枚举编码。 */
        private String generateSourceEnum;
        /** 活动主键。 */
        private Long activityId;
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /** requester主键。 */
        private Long requesterId;
        /** 原始业务号。 */
        private String originalBusNo;
    }
}
