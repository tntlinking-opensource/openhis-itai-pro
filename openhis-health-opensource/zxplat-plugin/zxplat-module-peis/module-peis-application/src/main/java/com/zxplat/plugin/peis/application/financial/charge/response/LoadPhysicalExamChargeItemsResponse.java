/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.plugin.peis.application.financial.charge.dto.PhysicalExamChargeItemRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 加载体检收费明细响应（IF-PE-CHG-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载体检收费明细响应")
public class LoadPhysicalExamChargeItemsResponse {

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 体检流水号。 */
    @Schema(description = "体检流水号")
    private String busNo;

    /** 套餐名称。 */
    @Schema(description = "套餐名称")
    private String packageName;

    /** 明细行。 */
    @Schema(description = "明细行")
    private List<PhysicalExamChargeItemRowDto> items;

    /** 待收合计。 */
    @Schema(description = "待收合计")
    private BigDecimal plannedTotalAmount;

    /** 已收合计。 */
    @Schema(description = "已收合计")
    private BigDecimal settledTotalAmount;

    /**
     * 懒生成非阻断警告（如应收项目缺 SALE）；有值时前端提示，不阻断明细加载。
     */
    @Schema(description = "定价/挂费警告列表")
    private List<String> pricingWarnings;
}
