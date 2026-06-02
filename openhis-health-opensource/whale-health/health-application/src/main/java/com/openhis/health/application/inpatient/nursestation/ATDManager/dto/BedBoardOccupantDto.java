/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.nursestation.ATDManager.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 床位列表 — 占床患者块（对齐 001 列表卡片核心展示，BR-IP-ATD-001）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "占床患者块")
public class BedBoardOccupantDto {

    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "性别 code")
    private String genderEnum;

    @Schema(description = "年龄文本")
    private String ageText;

    @Schema(description = "住院号")
    private String encounterBusNo;

    @Schema(description = "就诊状态 code")
    private String statusEnum;

    @Schema(description = "就诊状态展示文案")
    private String statusEnumText;

    @Schema(description = "病情 code")
    private String priorityEnum;

    @Schema(description = "入科时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime admissionTime;

    @Schema(description = "住院医师姓名摘要")
    private String attendingDoctorName;

    @Schema(description = "责任护士姓名摘要")
    private String primaryNurseName;

    @Schema(description = "诊断一行摘要")
    private String diagnosisSummary;

    @Schema(description = "展示用账户余额")
    private BigDecimal accountBalance;

    @Schema(description = "低余额预警")
    private Boolean lowBalanceWarning;
}
