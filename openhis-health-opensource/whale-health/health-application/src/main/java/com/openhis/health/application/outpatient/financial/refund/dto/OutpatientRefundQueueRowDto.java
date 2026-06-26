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

package com.openhis.health.application.outpatient.financial.refund.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待退费患者队列行（IF-REF-02）。
 */
@Data
@Accessors(chain = true)
/** 待退费患者队列行。 */
@Schema(description = "待退费患者队列行")
public class OutpatientRefundQueueRowDto {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    /** 就诊主键（业务字段）。 */
    private Long encounterId;

    /** 就诊业务号。 */
    @Schema(description = "就诊业务号")
    /** 就诊业务号（业务字段）。 */
    private String encounterBusNo;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者主键。 */
    @Schema(description = "患者主键")
    /** 患者主键（业务字段）。 */
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    /** 患者姓名（业务字段）。 */
    private String patientName;

    /** 性别编码。 */
    @Schema(description = "性别编码")
    /** 性别编码（业务字段）。 */
    private String genderCode;

    /** 性别展示（AdministrativeGender.display）。 */
    @Schema(description = "性别展示（AdministrativeGender.display）")
    /** 性别展示（业务字段）。 */
    private String genderEnumText;

    /** 年龄展示文本。 */
    @Schema(description = "年龄文本")
    /** 年龄文本（业务字段）。 */
    private String ageText;

    /** 队列标签 code（PENDING=待退费，SETTLED=已退费；见 {@link com.openhis.health.domain.share.constants.OutpatientRefundQueueTab}）。 */
    @Schema(description = "队列标签编码 PENDING/SETTLED（SETTLED 对外展示已退费）")
    /** 队列标签编码 PENDING/SETTLED（SETTLED 对外展示已退费）（业务字段）。 */
    private String queueLabelCode;

    /** 收费项状态摘要展示。 */
    @Schema(description = "收费项状态摘要")
    /** 收费项状态摘要（业务字段）。 */
    private String chargeItemStatusSummary;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    /** 科室名称（业务字段）。 */
    private String organizationName;

    /** 接诊医生姓名。 */
    @Schema(description = "接诊医生")
    /** 接诊医生（业务字段）。 */
    private String doctorName;

    /** 就诊开始时间。 */
    @Schema(description = "就诊开始时间")
    /** 就诊开始时间（业务字段）。 */
    private LocalDateTime startTime;

    /** 费用总额。 */
    @Schema(description = "费用总额")
    /** 费用总额（业务字段）。 */
    private BigDecimal totalAmount;

    /** 已付金额。 */
    @Schema(description = "已付金额")
    /** 已付金额（业务字段）。 */
    private BigDecimal paidAmount;
}
