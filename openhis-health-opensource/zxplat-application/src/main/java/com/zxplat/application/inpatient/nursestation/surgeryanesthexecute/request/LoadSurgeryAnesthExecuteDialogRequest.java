/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.surgeryanesthexecute.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 装载手术/麻醉执行弹窗请求（IF-IP-SAE-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "装载手术/麻醉执行弹窗请求", requiredProperties = {"encounterId", "serviceRequestId", "planTime"})
public class LoadSurgeryAnesthExecuteDialogRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "就诊主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 服务申请主键（手术/麻醉 SR）。 */
    @NotNull(message = "服务申请标识不能为空")
    @Schema(description = "服务申请主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /**
     * 计划执行槽位时刻（对齐 IF-IP-EXE；将写入 Procedure.occurrence_time）。
     */
    @NotNull(message = "计划执行时刻不能为空")
    @Schema(description = "计划执行槽位时刻", required = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime planTime;
}
