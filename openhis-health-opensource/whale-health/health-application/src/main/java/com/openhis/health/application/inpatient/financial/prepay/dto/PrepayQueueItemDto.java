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
@Schema(description = "预交金在院患者队列行")
public class PrepayQueueItemDto {

    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @Schema(description = "住院号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterBusNo;

    @Schema(description = "患者姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientName;

    @Schema(description = "性别 code")
    private String genderEnum;

    @Schema(description = "性别展示")
    private String genderEnumText;

    @Schema(description = "年龄展示")
    private String ageText;

    @Schema(description = "科室名称")
    private String organizationName;

    @Schema(description = "病区名称")
    private String wardName;

    @Schema(description = "床号展示")
    private String bedName;

    @Schema(description = "就诊状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    @Schema(description = "就诊状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnumText;

    @Schema(description = "入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "账户余额（展示）：预交金收退净值 − 在院累计费用，动态计算", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal accountBalance;

    @Schema(description = "费用性质/医保类型展示")
    private String contractDisplay;
}
