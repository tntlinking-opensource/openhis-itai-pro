/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 由请求生成发放草稿命令（临时路径）。
 */
@Getter
@Setter
public class GenerateDeviceDispenseFromTemporaryPathCommand extends BatchCommand<GenerateDeviceDispenseFromTemporaryPathCommand.GenerateDeviceDispenseFromTemporaryPathItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 生成设备发药来源临时路径项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class GenerateDeviceDispenseFromTemporaryPathItemCommand {
        /** 设备申请主键。 */
        private Long deviceReqId;
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /** 设备def主键。 */
        private Long deviceDefId;
        /** 数量。 */
        private BigDecimal quantity;
        /** unit编码。 */
        private String unitCode;
        /** limit时间。 */
        private LocalDateTime limitTime;
    }
}
