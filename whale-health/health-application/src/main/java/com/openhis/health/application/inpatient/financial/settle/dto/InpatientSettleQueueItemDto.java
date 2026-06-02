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

package com.openhis.health.application.inpatient.financial.settle.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院结算队列卡片 DTO（IF-IP-STL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算队列行")
public class InpatientSettleQueueItemDto {

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

    @Schema(description = "待结算费用合计")
    private BigDecimal pendingSettlementTotal;

    @Schema(description = "最近结算时间（已结算页签）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSettleTime;

    @Schema(description = "费用性质展示")
    private String contractDisplay;

    @Schema(description = "展示余额：预交金净值 −（待结算 + 已结算占用）")
    private BigDecimal accountBalance;
}
