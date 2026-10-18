/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 体检收费明细行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检收费明细行")
public class PhysicalExamChargeItemRowDto {

    /** 收费明细主键。 */
    @Schema(description = "收费明细主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemId;

    /** 收费状态 code。 */
    @Schema(description = "收费状态 code")
    private String status;

    /** 项目展示名称。 */
    @Schema(description = "项目展示名称")
    private String itemLabel;

    /** 行总价。 */
    @Schema(description = "行总价")
    private BigDecimal totalPrice;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 目录原价（base_amount，改价前参考）。 */
    @Schema(description = "目录原价")
    private BigDecimal catalogUnitPrice;

    /** 是否可改价（待收费且锚加项 SR 或套餐 AD）。 */
    @Schema(description = "是否可改价")
    private Boolean priceEditable;

    /**
     * 收费锚点表名。
     * 个检套餐整单为 {@code wkf_activity_definition}，加项为 {@code wkf_service_request}。
     */
    @Schema(description = "收费锚点表名")
    private String serviceTable;

    /** 服务申请主键（wkf_service_request 锚点时）。 */
    @Schema(description = "服务申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /** 改价原因码。 */
    @Schema(description = "改价原因码")
    private String overrideReasonCode;
}
