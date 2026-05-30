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
@Schema(description = "门诊收费队列行")
public class ChargeQueueRowDto {
    @Schema(description = "就诊 ID")
    /**
     * encounterId。
     */
    private Long encounterId;
    @Schema(description = "就诊业务号")
    /**
     * encounterBusNo。
     */
    private String encounterBusNo;
    @Schema(description = "患者 ID")
    /**
     * patientId。
     */
    private Long patientId;
    @Schema(description = "患者姓名")
    /**
     * patientName。
     */
    private String patientName;
    @Schema(description = "性别编码")
    /**
     * genderCode。
     */
    private String genderCode;

    @Schema(description = "性别展示（AdministrativeGender.display）")
    private String genderEnumText;
    @Schema(description = "年龄文本")
    /**
     * ageText。
     */
    private String ageText;
    @Schema(description = "科室名称")
    /**
     * organizationName。
     */
    private String organizationName;
    @Schema(description = "接诊医生")
    /**
     * doctorName。
     */
    private String doctorName;
    @Schema(description = "就诊开始时间")
    /**
     * startTime。
     */
    private LocalDateTime startTime;
    @Schema(description = "队列标签编码")
    /**
     * queueLabelCode。
     */
    private String queueLabelCode;
    @Schema(description = "费用总额")
    /**
     * totalAmount。
     */
    private BigDecimal totalAmount;
    @Schema(description = "已支付金额")
    /**
     * paidAmount。
     */
    private BigDecimal paidAmount;

    @Schema(description = "本就诊下符合门诊收银口径的收费明细摘要（待收费/已结算；不含草稿；不含挂号锚点）")
    private List<ChargeQueueChargeItemBriefDto> cashierChargeItems;

    @Schema(description = "收费状态汇总码（由本就诊收银明细 ChargeItem.status 推导：含待收费→PLANNED；全非 PLANNED 且全非 SETTLED→MIXED）")
    private String cashierChargeRollupStatusCode;

    @Schema(description = "收费状态汇总文案")
    private String cashierChargeRollupStatusText;
}
