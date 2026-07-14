/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-ARG-08：住院滚费定时任务请求（基础设施 POST 触发）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院滚费定时任务请求")
public class ExecuteInpatientAutoRollJobRequest {

    /** 滚费业务日。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "滚费业务日")
    private LocalDate billDate;

    /** 操作人主键（可选；空则系统操作人）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "操作人主键（可选；空则系统操作人）")
    private Long operatorId;
}
