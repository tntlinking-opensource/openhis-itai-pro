/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 护士诊疗执行明细行（IF-RPT-NWL-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士诊疗执行明细行")
public class NurseExecutionDetailRowDto {

    /** 执行记录主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行记录主键")
    private Long procedureId;

    /** 服务申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "服务申请主键")
    private Long serviceRequestId;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 诊疗类目码。 */
    @Schema(description = "诊疗类目码")
    private String categoryEnum;

    /** 诊疗类目展示名。 */
    @Schema(description = "诊疗类目展示名")
    private String categoryDisplay;

    /** 生成来源码。 */
    @Schema(description = "生成来源码")
    private String generateSourceEnum;

    /** 生成来源展示名。 */
    @Schema(description = "生成来源展示名")
    private String generateSourceDisplay;

    /** 执行护士主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行护士主键")
    private Long practitionerId;

    /** 执行护士姓名。 */
    @Schema(description = "执行护士姓名")
    private String practitionerName;

    /** 执行时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "执行时间")
    private LocalDateTime recordedTime;

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 就诊号。 */
    @Schema(description = "就诊号")
    private String encounterBusNo;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 金额。 */
    @Schema(description = "金额")
    private BigDecimal amount;
}
