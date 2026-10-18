/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 医技执行单·开立带出耗材预览行（只读，不参与批量执行/取消）。
 *
 * <p>住院：医生开立不落耗材请求（BR-IP-ORD-036），执行页按诊疗 ACTIVITY 绑定模板带出名称/数量等供核对；
 * 计费仍走医技计费，本行不推进发放。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技执行单开立带出耗材预览")
public class MedicalTechBoundSupplyItemDto {

    /**
     * 稳定展示键（bindingId + 源 ServiceRequest）。
     */
    @Schema(description = "展示主键")
    private String previewKey;

    /** 绑定模板主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "绑定模板主键")
    private Long bindingId;

    /** 源诊疗请求主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源诊疗请求主键")
    private Long sourceRequestId;

    /** 源诊疗项目名。 */
    @Schema(description = "源诊疗项目名")
    private String sourceItemName;

    /** 耗材定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材定义主键")
    private Long deviceDefinitionId;

    /** 耗材名称。 */
    @Schema(description = "耗材名称")
    private String itemName;

    /** 目录编号（耗材定义 bus_no）。 */
    @Schema(description = "目录编号")
    private String itemCode;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 带出数量（模板量 × 诊疗数量）。 */
    @Schema(description = "带出数量")
    private BigDecimal quantity;

    /** 单位展示。 */
    @Schema(description = "单位")
    private String unitName;

    /** 参考单价。 */
    @Schema(description = "参考单价")
    private BigDecimal unitPrice;

    /** 参考总价（单价 × 带出数量）。 */
    @Schema(description = "参考总价")
    private BigDecimal totalPrice;
}
