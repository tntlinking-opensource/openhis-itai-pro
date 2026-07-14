/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 工作量明细行 DTO（IF-RPT-WKL-03/04）。
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

    /** payment 主键（明细行唯一键）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "payment 主键")
    private Long paymentId;

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

    /** 应收金额。 */
    @Schema(description = "应收金额")
    private BigDecimal receivableAmount;

    /** 实收金额。 */
    @Schema(description = "实收金额")
    private BigDecimal actualReceivedAmount;

    /** 成本金额。 */
    @Schema(description = "成本金额")
    private BigDecimal costAmount;

    /** 净收入。 */
    @Schema(description = "净收入")
    private BigDecimal netIncomeAmount;
}
