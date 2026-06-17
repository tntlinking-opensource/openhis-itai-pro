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

package com.openhis.health.application.inpatient.financial.prepay.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预交金在院患者队列卡片 DTO（IF-IP-PREP-01）。
 */
@Data
@Accessors(chain = true)
/** 预交金在院患者队列行。 */
@Schema(description = "预交金在院患者队列行")
public class PrepayQueueItemDto {

    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 住院号。 */
    @Schema(description = "住院号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterBusNo;

    /** 患者姓名。 */
    @Schema(description = "患者姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientName;

    /** 性别 code。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 性别展示。 */
    @Schema(description = "性别展示")
    private String genderEnumText;

    /** 年龄展示。 */
    @Schema(description = "年龄展示")
    private String ageText;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 床号展示。 */
    @Schema(description = "床号展示")
    private String bedName;

    /** 就诊状态 code。 */
    @Schema(description = "就诊状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    /** 就诊状态展示。 */
    @Schema(description = "就诊状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnumText;

    /** 入院时间。 */
    @Schema(description = "入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 账户余额。 */
    @Schema(description = "账户余额（展示）：预交金收退净值 − 在院累计费用，动态计算", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal accountBalance;

    /** 费用性质/医保类型展示。 */
    @Schema(description = "费用性质/医保类型展示")
    private String contractDisplay;
}
