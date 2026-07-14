/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.treatment.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 医嘱执行单抽屉行 DTO（IF-NUR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱执行单抽屉行")
public class OrderExecutionSheetItemDto {

    /**
     * 执行记录主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行记录主键")
    /** 执行记录主键。 */
    private Long procedureId;

    /**
     * 来源请求主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源请求主键")
    /** 来源请求主键。 */
    private Long requestId;

    /**
     * 来源表名。
     */
    @Schema(description = "来源表名")
    private String requestTable;

    /**
     * 执行状态持久化码。
     */
    @Schema(description = "执行状态持久化码")
    private String statusEnum;

    /**
     * 执行状态展示文案。
     */
    @Schema(description = "执行状态展示文案")
    private String statusEnumText;

    /**
     * 执行分类持久化码。
     */
    @Schema(description = "执行分类持久化码")
    private String categoryEnum;

    /**
     * 患者主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    /** 患者主键。 */
    private Long patientId;

    /**
     * 就诊主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    /** 就诊主键。 */
    private Long encounterId;

    /**
     * 发生时间。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "发生时间")
    /** 发生时间。 */
    private LocalDateTime occurrenceTime;

    /**
     * 记录时间。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "记录时间")
    /** 记录时间。 */
    private LocalDateTime recordedTime;

    /**
     * 记录人主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "记录人主键")
    /** 记录人主键。 */
    private Long recorderId;

    /**
     * 结果摘要。
     */
    @Schema(description = "结果摘要")
    private String resultText;

    /**
     * 对应医嘱/项目展示名（读侧由目录定义回填，供执行记录抽屉「目标」列直接展示）。
     */
    @Schema(description = "对应医嘱/项目展示名")
    private String orderItemDisplayName;

    /**
     * 操作展示文案（如「执行完成」「取消执行」，禁止前端解析 JSON 或自行拼接状态码）。
     */
    @Schema(description = "操作展示文案")
    private String actionDisplayText;

    /**
     * 本条记录操作人展示名（完成时为执行人，取消时为取消执行人）。
     */
    @Schema(description = "操作人展示名")
    private String operatorDisplayName;

    /**
     * 操作人列标题语义（「执行人」或「取消执行人」），与 {@link #operatorDisplayName} 成对下发。
     */
    @Schema(description = "操作人列标题语义")
    private String operatorRoleDisplayText;
}
