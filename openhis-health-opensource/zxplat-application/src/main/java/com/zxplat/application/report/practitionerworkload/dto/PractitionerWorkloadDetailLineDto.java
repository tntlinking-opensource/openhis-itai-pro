/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 工作量明细行 DTO（IF-RPT-WKL-03/04；DD-报表中心-进售价口径）。
 *
 * <p>一行一账单；金额仅售价/进价；本轮不含应收/实收。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "工作量明细行")
public class PractitionerWorkloadDetailLineDto {

    /** 开单科室 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开单科室 id")
    private Long requestingOrgId;

    /** chargeItem 主键（明细行唯一键）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "chargeItem 主键")
    private Long chargeItemId;

    /** 开单科室名称。 */
    @Schema(description = "开单科室名称")
    private String requestingOrgName;

    /** 医嘱锚点表 service_table。 */
    @Schema(description = "医嘱锚点表 service_table")
    private String serviceTable;

    /** 医嘱 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "医嘱 id")
    private Long serviceId;

    /** 目录类型展示。 */
    @Schema(description = "目录类型展示")
    private String catalogTypeDisplay;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String productName;

    /** 项目编码。 */
    @Schema(description = "项目编码")
    private String productCode;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 开单医生 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开单医生 id")
    private Long practitionerId;

    /** 开单医生姓名。 */
    @Schema(description = "开单医生姓名")
    private String practitionerName;

    /** 开立时间。 */
    @Schema(description = "开立时间")
    private String enteredDate;

    /** 售价金额（账单 total_price；带收退符号）。 */
    @Schema(description = "售价金额")
    private BigDecimal saleAmount;

    /** 优惠金额（售价 − 折后应收；未命中折扣配置为 0）。 */
    @Schema(description = "优惠金额")
    private BigDecimal discountAmount;

    /** 实收金额（折后应收；未打折等于售价）。 */
    @Schema(description = "实收金额")
    private BigDecimal actualReceivedAmount;

    /** 进价金额（批号进价包装锚；诊疗无进价则为 null）。 */
    @Schema(description = "进价金额")
    private BigDecimal purchaseAmount;
}
