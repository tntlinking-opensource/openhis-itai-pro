/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医技已划价行（AS-MT-CHG-001 IF-MT-CHG-03；耗材 + 诊疗）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技已划价行")
public class MedicalTechPricedDeviceRowDto {

    /**
     * 行类型：{@code DEVICE_REQUEST} 或 {@code SERVICE_REQUEST}。
     */
    @Schema(description = "行类型 DEVICE_REQUEST|SERVICE_REQUEST")
    private String orderType;

    /** 请求主键（耗材 DeviceRequest / 诊疗 ServiceRequest）。 */
    @Schema(description = "请求主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /** 主发放主键。 */
    @Schema(description = "主发放主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 主费用行主键（有则填）。 */
    @Schema(description = "主费用行主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemId;

    /** 品名。 */
    @Schema(description = "品名")
    private String itemName;

    /** 项目编码。 */
    @Schema(description = "项目编码")
    private String itemCode;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 行金额。 */
    @Schema(description = "行金额")
    private BigDecimal totalPrice;

    /** 耗材房主键。 */
    @Schema(description = "耗材房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 收费状态码。 */
    @Schema(description = "收费状态码")
    private String chargeStatusEnum;

    /** 发放状态码。 */
    @Schema(description = "发放状态码")
    private String dispenseStatusEnum;

    /** 划价展示时刻（主发放时间优先）。 */
    @Schema(description = "划价展示时刻")
    private LocalDateTime pricedTime;

    /**
     * 确费人主键；空=未确费（费用清单一键确费写入）。
     */
    @Schema(description = "确费人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long feeConfirmerId;

    /**
     * 确费人展示名；未确费时为空。
     */
    @Schema(description = "确费人展示名")
    private String feeConfirmerName;

    /**
     * 计费人主键（划价操作人；真源优先 DeviceRequest.requester_id，
     * 否则 DeviceDispense 发放/配药人）。
     */
    @Schema(description = "计费人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long billingPractitionerId;

    /**
     * 计费人展示名；与 {@link #billingPractitionerId} 成对。
     */
    @Schema(description = "计费人展示名")
    private String billingPractitionerName;

    /** 待结算且未冲销时可冲为 true。 */
    @Schema(description = "是否可冲销")
    private Boolean cancellable;
}
