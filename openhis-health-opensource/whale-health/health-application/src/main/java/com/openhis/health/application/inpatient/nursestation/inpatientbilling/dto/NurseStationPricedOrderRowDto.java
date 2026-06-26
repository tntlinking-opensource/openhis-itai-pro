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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已划价列表行（IF-IP-PRC-05，BR-IP-PRC-015）。
 *
 * <p>数据来源 OrderInfo 骨架 + ChargeItem + 可选 DeviceDispense；区分 DIRECT/SUPPLEMENT。</p>
 */
@Data
@Accessors(chain = true)
/** 已划价列表行。 */
@Schema(description = "已划价列表行")
public class NurseStationPricedOrderRowDto {

    @Schema(description = "稳定行键")
    private String listRowKey;

    @Schema(description = "OrderInfo/请求主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 医嘱类型：SERVICE_REQUEST / DEVICE_REQUEST。 */
    @Schema(description = "医嘱类型：SERVICE_REQUEST / DEVICE_REQUEST")
    private String orderType;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 产品展示名（ProductDefinition 读模型）。 */
    @Schema(description = "项目名称")
    private String productDisplayName;

    /** 划价数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 计价单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 计价单位展示（可空，由前端字典补全）。 */
    @Schema(description = "单位展示")
    private String unitDisplay;

    /** 费用单价（ChargeItem）。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 费用总价（ChargeItem）。 */
    @Schema(description = "总价")
    private BigDecimal totalPrice;

    /** 划价时间（OrderInfo.createdAt）。 */
    @Schema(description = "划价时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime pricedTime;

    /** 费用状态 code（如 PENDING_SETTLEMENT）。 */
    @Schema(description = "费用状态 code")
    private String chargeStatusEnum;

    /** 费用状态展示名。 */
    @Schema(description = "费用状态展示")
    private String chargeStatusDisplay;

    /** 耗材发放状态 code（有发放单时为 PREPARATION）。 */
    @Schema(description = "发放状态 code")
    private String dispenseStatusEnum;

    /** 耗材发放状态展示名。 */
    @Schema(description = "发放状态展示")
    private String dispenseStatusDisplay;

    /** 划价模式：DIRECT / SUPPLEMENT。 */
    @Schema(description = "划价模式：DIRECT / SUPPLEMENT")
    private String pricingModeCode;

    /** 划价模式中文展示。 */
    @Schema(description = "划价模式展示")
    private String pricingModeDisplay;

    /**
     * 上级医嘱来源表（补费子行；与域 {@code based_on_table} 一致，如 {@code med_medication_request}、{@code wkf_service_request}）。
     */
    @Schema(description = "上级医嘱来源表（补费）")
    private String basedOnTable;

    /**
     * 上级医嘱来源主键（补费子行；与域 {@code based_on_id} 一致，对应父医嘱 {@code requestId}）。
     */
    @Schema(description = "上级医嘱来源主键（补费）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long basedOnId;
}
