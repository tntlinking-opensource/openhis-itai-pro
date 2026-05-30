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
@Schema(description = "待退费患者队列行")
public class OutpatientRefundQueueRowDto {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 就诊业务号。 */
    @Schema(description = "就诊业务号")
    private String encounterBusNo;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 队列标签 code（PENDING=待退费，SETTLED=已退费；见 {@link com.openhis.health.domain.share.constants.OutpatientRefundQueueTab}）。 */
    @Schema(description = "队列标签编码 PENDING/SETTLED（SETTLED 对外展示已退费）")
    private String queueLabelCode;

    /** 收费项状态摘要展示。 */
    @Schema(description = "收费项状态摘要")
    private String chargeItemStatusSummary;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 接诊医生姓名。 */
    @Schema(description = "接诊医生")
    private String doctorName;

    /** 就诊开始时间。 */
    @Schema(description = "就诊开始时间")
    private LocalDateTime startTime;

    /** 费用总额。 */
    @Schema(description = "费用总额")
    private BigDecimal totalAmount;

    /** 已付金额。 */
    @Schema(description = "已付金额")
    private BigDecimal paidAmount;
}
