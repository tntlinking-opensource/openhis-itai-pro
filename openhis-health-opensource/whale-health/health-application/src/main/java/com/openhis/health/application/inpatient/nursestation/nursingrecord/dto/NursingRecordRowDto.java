/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.nursingrecord.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 护理记录列表/保存回显行 DTO（对齐 UI {@code NursingRecordRow}，IF-IP-NRC-02/03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护理记录行")
public class NursingRecordRowDto {

    /** 主表 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主表 id")
    private Long vitalSignId;

    /** 记录日期。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "记录日期")
    private LocalDate recordDate;

    /** 记录时间。 */
    @Schema(description = "记录时间")
    private String recordTime;

    /** 意识。 */
    @Schema(description = "意识")
    private String consciousness;

    /** 体温。 */
    @Schema(description = "体温")
    private String temperature;

    /** 心率。 */
    @Schema(description = "心率")
    private String heartRate;

    /** 脉搏。 */
    @Schema(description = "脉搏")
    private String pulse;

    /** 呼吸。 */
    @Schema(description = "呼吸")
    private String respiratory;

    /** 血压。 */
    @Schema(description = "血压")
    private String bloodPressure;

    /** 瞳孔左。 */
    @Schema(description = "瞳孔左")
    private String pupilLeft;

    /** 瞳孔右。 */
    @Schema(description = "瞳孔右")
    private String pupilRight;

    /** 血氧饱和度。 */
    @Schema(description = "血氧饱和度")
    private String oxygenSaturation;

    /** 吸氧。 */
    @Schema(description = "吸氧")
    private String oxygenRate;

    /** 吸氧方式。 */
    @Schema(description = "吸氧方式")
    private String oxygenMode;

    /** 氧流量。 */
    @Schema(description = "氧流量")
    private String oxygenFlow;

    /** 入量名称。 */
    @Schema(description = "入量名称")
    private String intakeName;

    /** 入量。 */
    @Schema(description = "入量")
    private String intakeAmount;

    /** 入量途径。 */
    @Schema(description = "入量途径")
    private String intakeRoute;

    /** 出量名称。 */
    @Schema(description = "出量名称")
    private String outputName;

    /** 出量。 */
    @Schema(description = "出量")
    private String outputAmount;

    /** 出量颜色性状。 */
    @Schema(description = "出量颜色性状")
    private String outputColorCharacter;

    /** 皮肤情况。 */
    @Schema(description = "皮肤情况")
    private String skinStatus;

    /** 管路护理。 */
    @Schema(description = "管路护理")
    private String tubeCare;

    /** 病情观察及护理。 */
    @Schema(description = "病情观察及护理")
    private String conditionMeasure;

    /** 护士签名。 */
    @Schema(description = "护士签名")
    private String nurseSignature;
}
