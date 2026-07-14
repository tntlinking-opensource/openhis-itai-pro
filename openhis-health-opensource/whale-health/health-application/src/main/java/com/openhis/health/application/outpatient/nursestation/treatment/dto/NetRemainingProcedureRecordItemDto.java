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
 * 净剩可取消执行记录行 DTO（IF-NUR-03B）。
 *
 * <p>仅包含尚未被 {@code CANCEL.refund_id} 对冲的 {@code COMPLETED} 行；已对冲的 COMPLETED 与对应 CANCEL 均不返回。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "净剩可取消执行记录行")
public class NetRemainingProcedureRecordItemDto {

    /**
     * 执行记录主键（前端取消时回传 {@code procedureId}）。
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
     * 来源表持久化名。
     */
    @Schema(description = "来源表持久化名")
    private String requestTable;

    /**
     * 执行状态持久化码（净剩列表固定为已完成 {@code EventStatus.COMPLETED}）。
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
     * 就诊主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    /** 就诊主键。 */
    private Long encounterId;

    /**
     * 患者主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    /** 患者主键。 */
    private Long patientId;

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
}
