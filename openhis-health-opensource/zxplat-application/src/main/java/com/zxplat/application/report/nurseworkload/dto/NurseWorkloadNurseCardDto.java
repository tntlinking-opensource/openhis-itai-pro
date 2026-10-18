/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 护士工作量卡片行（轻量：类目/耗材 chip + 合计；不含患者就诊展示 enrich）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士工作量卡片行")
public class NurseWorkloadNurseCardDto {

    /** 护士主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护士主键")
    private Long practitionerId;

    /** 诊疗执行次数。 */
    @Schema(description = "诊疗执行次数")
    private Long executionTotalCount;

    /** 诊疗执行售价合计。 */
    @Schema(description = "诊疗执行售价合计")
    private BigDecimal executionSaleAmountTotal;

    /** 诊疗执行优惠合计。 */
    @Schema(description = "诊疗执行优惠合计")
    private BigDecimal executionDiscountAmountTotal;

    /** 诊疗执行实收合计。 */
    @Schema(description = "诊疗执行实收合计")
    private BigDecimal executionActualReceivedAmountTotal;

    /** 诊疗执行进价合计（诊疗无进价，恒 null）。 */
    @Schema(description = "诊疗执行进价合计")
    private BigDecimal executionPurchaseAmountTotal;

    /** 诊疗类目汇总行（供 chip）。 */
    @Schema(description = "诊疗类目汇总行")
    private List<NurseExecutionSummaryRowDto> executionSummaryRows;

    /** 耗材数量合计。 */
    @Schema(description = "耗材数量合计")
    private BigDecimal consumableTotalQuantity;

    /** 耗材售价合计。 */
    @Schema(description = "耗材售价合计")
    private BigDecimal consumableSaleAmountTotal;

    /** 耗材优惠合计。 */
    @Schema(description = "耗材优惠合计")
    private BigDecimal consumableDiscountAmountTotal;

    /** 耗材实收合计。 */
    @Schema(description = "耗材实收合计")
    private BigDecimal consumableActualReceivedAmountTotal;

    /** 耗材进价合计。 */
    @Schema(description = "耗材进价合计")
    private BigDecimal consumablePurchaseAmountTotal;

    /** 耗材高/低值汇总行。 */
    @Schema(description = "耗材高/低值汇总行")
    private List<NurseConsumableSummaryRowDto> consumableSummaryRows;

    /** 耗材品名 chip 行。 */
    @Schema(description = "耗材品名 chip")
    private List<NurseConsumableItemChipDto> consumableItemChips;
}
