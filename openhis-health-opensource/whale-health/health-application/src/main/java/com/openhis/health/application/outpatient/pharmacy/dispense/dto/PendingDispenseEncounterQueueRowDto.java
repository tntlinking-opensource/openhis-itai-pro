/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 门诊发药就诊队列行。
 *
 * <p>展示字段来自门诊信息读模型；待发摘要可由后续聚合接入。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊发药就诊队列行")
public class PendingDispenseEncounterQueueRowDto {

    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 就诊业务流水号。 */
    @Schema(description = "就诊业务号")
    private String encounterBusNo;

    /** 患者主键。 */
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名（脱敏策略由读模型侧决定）。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别持久化 code。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 年龄展示文案（如「3岁」）。 */
    @Schema(description = "年龄展示")
    private String ageText;

    /** 接诊科室/机构名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 接诊医师姓名。 */
    @Schema(description = "接诊医生")
    private String doctorName;

    /** 就诊状态持久化 code。 */
    @Schema(description = "就诊状态 code")
    private String statusEnum;

    /** 就诊状态展示名。 */
    @Schema(description = "就诊状态文案")
    private String statusEnumText;

    /** 待发药摘要（接口预留；当前可为空）。 */
    @Schema(description = "待发药摘要（目标态可二次聚合）")
    private String dispensePendingSummaryText;

    /** 就诊开始时刻。 */
    @Schema(description = "就诊开始时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime startTime;
}
