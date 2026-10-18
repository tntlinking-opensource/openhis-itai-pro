/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.encountermanage.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 医生站门诊患者暂离请求（IF-DOR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-门诊患者暂离请求")
public class MarkOutpatientLeaveRequest {

    /** 门诊就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "门诊就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 暂离原因说明。 */
    @Schema(description = "暂离原因说明")
    private String leaveReason;

    /** 发生时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 暂离发生时间，空则取当前时间。 */
    @Schema(description = "暂离发生时间，空则取当前时间")
    private LocalDateTime occurredAt;

    /** 操作人主键。 */
    @NotNull(message = "操作人不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 操作人主键。 */
    @Schema(description = "操作人主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long operatorId;
}
