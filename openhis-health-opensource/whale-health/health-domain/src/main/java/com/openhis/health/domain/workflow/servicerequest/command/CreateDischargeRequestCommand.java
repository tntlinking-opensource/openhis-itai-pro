/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 开立出院申请命令。
 */
@Getter
@Setter
public class CreateDischargeRequestCommand extends BatchCommand<CreateDischargeRequestCommand.CreateDischargeRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建出院请求项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateDischargeRequestItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键（与就诊一致，供列表与读模型投影）。 */
        private Long patientId;
        /** 出院时间。 */
        private LocalDateTime dischargeTime;
        /** 原因文本。 */
        private String reasonText;
    }
}
