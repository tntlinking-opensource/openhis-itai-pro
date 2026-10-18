/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.nursingrecord.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-NRC-03：保存/更新单条护理记录请求（对齐 UI {@code NursingRecordRow}）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存单条护理记录请求", requiredProperties = {
    "encounterId", "patientId", "recordDate", "recordTime"
})
public class SaveNurseStationNursingRecordRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者标识。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 已有主表 id（更新）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "已有主表 id（更新）")
    private Long vitalSignId;

    /** 记录日期。 */
    @NotNull(message = "记录日期不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "记录日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate recordDate;

    /** 记录时间。 */
    @Schema(description = "记录时间", requiredMode = Schema.RequiredMode.REQUIRED)
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

    /** 记录人（护士签名对应医务人员 id；空则取当前登录人）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "记录人医务人员 id")
    private Long recorderId;
}
