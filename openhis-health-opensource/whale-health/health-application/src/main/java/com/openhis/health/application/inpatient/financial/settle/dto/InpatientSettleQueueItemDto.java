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
/** 住院结算队列行。 */
@Schema(description = "住院结算队列行")
public class InpatientSettleQueueItemDto {

    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 患者主键 {@code adm_patient.id}，须与就诊归属一致。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者 ID（业务字段）。 */
    private Long patientId;

    /** 住院号（就诊业务编号），列表主展示列。 */
    @Schema(description = "住院号", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 住院号（业务字段）。 */
    private String encounterBusNo;

    /** 患者姓名（读模型投影）。 */
    @Schema(description = "患者姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 患者姓名（业务字段）。 */
    private String patientName;

    /** 性别枚举 code（{@link com.openhis.health.domain.share.enums.AdministrativeGender}）。 */
    @Schema(description = "性别 code")
    /** 性别 code（业务字段）。 */
    private String genderEnum;

    /** 性别中文展示。 */
    @Schema(description = "性别展示")
    /** 性别展示（业务字段）。 */
    private String genderEnumText;

    /** 年龄展示文案（由出生日期计算）。 */
    @Schema(description = "年龄展示")
    /** 年龄展示（业务字段）。 */
    private String ageText;

    /** 住院科室名称。 */
    @Schema(description = "科室名称")
    /** 科室名称（业务字段）。 */
    private String organizationName;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    /** 病区名称（业务字段）。 */
    private String wardName;

    /** 床号展示（病区-床位）。 */
    @Schema(description = "床号展示")
    /** 床号展示（业务字段）。 */
    private String bedName;

    /** 就诊状态 code（{@link com.openhis.health.domain.share.enums.EncounterStatus}）。 */
    @Schema(description = "就诊状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 就诊状态 code（业务字段）。 */
    private String statusEnum;

    /** 就诊状态中文展示。 */
    @Schema(description = "就诊状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 就诊状态展示（业务字段）。 */
    private String statusEnumText;

    /** 入院时间。 */
    @Schema(description = "入院时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 入院时间（业务字段）。 */
    private LocalDateTime startTime;

    /** 当前待结算费用合计（PENDING 收费项 total_price 汇总）。 */
    @Schema(description = "待结算费用合计")
    /** 待结算费用合计（业务字段）。 */
    private BigDecimal pendingSettlementTotal;

    /** 最近一次成功结算时间（已结算页签展示）。 */
    @Schema(description = "最近结算时间（已结算页签）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 最近结算时间（已结算页签）（业务字段）。 */
    private LocalDateTime lastSettleTime;

    /** 费用性质展示（合同名称/编号）。 */
    @Schema(description = "费用性质展示")
    /** 费用性质展示（业务字段）。 */
    private String contractDisplay;

    /** 登记账户关联合同 ID，医保读卡/预结算时使用。 */
    @Schema(description = "登记账户合同 ID（医保读卡用）")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 登记账户合同 ID（医保读卡用）（业务字段）。 */
    private Long contractId;

    /** 展示余额：预交金净值 −（待结算 + 已结算占用）。 */
    @Schema(description = "展示余额：预交金净值 −（待结算 + 已结算占用）")
    /** 展示余额：预交金净值 −（待结算 + 已结算占用）（业务字段）。 */
    private BigDecimal accountBalance;
}
