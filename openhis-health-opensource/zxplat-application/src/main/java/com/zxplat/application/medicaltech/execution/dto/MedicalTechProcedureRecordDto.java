/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 医技执行记录行（AS-MT-EXE-001 IF-MT-EXE-03 / 03B）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技执行记录行")
public class MedicalTechProcedureRecordDto {

    /** 执行记录主键 {@code cln_procedure.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行记录主键")
    private Long procedureId;

    /** 来源请求主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源请求主键")
    private Long requestId;

    /** 项目展示名。 */
    @Schema(description = "项目展示名")
    private String orderItemDisplayName;

    /** 动作展示文案（完成/取消等）。 */
    @Schema(description = "动作展示文案")
    private String actionDisplayText;

    /** 执行状态码。 */
    @Schema(description = "执行状态码")
    private String statusEnum;

    /** 记录时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "记录时间")
    private LocalDateTime recordedTime;

    /** 操作人展示名。 */
    @Schema(description = "操作人展示名")
    private String operatorDisplayName;
}
