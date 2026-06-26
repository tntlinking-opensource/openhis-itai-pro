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

package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 门诊收费队列行 DTO。
 */
@Data
@Accessors(chain = true)
/** 门诊收费队列行。 */
@Schema(description = "门诊收费队列行")
public class ChargeQueueRowDto {

    @Schema(description = "就诊 ID")
    private Long encounterId;

    @Schema(description = "就诊业务号")
    private String encounterBusNo;

    /** 患者主键。 */
    @Schema(description = "患者 ID")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别编码。 */
    @Schema(description = "性别编码")
    private String genderCode;

    /** 性别展示（AdministrativeGender.display）。 */
    @Schema(description = "性别展示（AdministrativeGender.display）")
    private String genderEnumText;

    /** 年龄展示文本。 */
    @Schema(description = "年龄文本")
    private String ageText;

    /** 接诊科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 接诊医生姓名。 */
    @Schema(description = "接诊医生")
    private String doctorName;

    /** 就诊开始时间。 */
    @Schema(description = "就诊开始时间")
    private LocalDateTime startTime;

    /** 队列页签标签编码（待收/已结等）。 */
    @Schema(description = "队列标签编码")
    private String queueLabelCode;

    /** 本就诊费用总额。 */
    @Schema(description = "费用总额")
    private BigDecimal totalAmount;

    /** 本就诊已支付金额。 */
    @Schema(description = "已支付金额")
    private BigDecimal paidAmount;

    /** 本就诊下符合门诊收银口径的收费明细摘要（待收费/已结算；不含草稿；不含挂号锚点）。 */
    @Schema(description = "本就诊下符合门诊收银口径的收费明细摘要（待收费/已结算；不含草稿；不含挂号锚点）")
    private List<ChargeQueueChargeItemBriefDto> cashierChargeItems;

    /** 收费状态汇总码（由本就诊收银明细 ChargeItem.status 推导）。 */
    @Schema(description = "收费状态汇总码（由本就诊收银明细 ChargeItem.status 推导：含待收费→PLANNED；全非 PLANNED 且全非 SETTLED→MIXED）")
    private String cashierChargeRollupStatusCode;

    /** 收费状态汇总文案。 */
    @Schema(description = "收费状态汇总文案")
    private String cashierChargeRollupStatusText;

    /** 最近一次成功支付流水主键（补打收费小票）。 */
    @Schema(description = "最近一次成功支付流水ID")
    private Long paymentId;
}
