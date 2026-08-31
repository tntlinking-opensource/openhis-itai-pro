/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.response;

import com.openhis.health.application.report.nurseworkload.dto.NurseWorkloadNurseCardDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 护士工作量卡片批量查询出参。
 *
 * <p>页脚合计与卡片同源事实行（同一次扫描 + 同一次金额 enrich），禁止前端再打汇总 IF 扫第二遍。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士工作量卡片批量查询出参")
public class ListNurseWorkloadNurseCardsResponse {

    /** 有数据的护士卡片（前端与下拉合并补零）。 */
    @Schema(description = "护士卡片行")
    private List<NurseWorkloadNurseCardDto> cards;

    /** 诊疗执行总次数（与 IF-02 totalExecutionCount 同口径；含无主执行人行）。 */
    @Schema(description = "诊疗执行总次数")
    private Long totalExecutionCount;

    /** 诊疗执行售价合计（结算刻度）。 */
    @Schema(description = "诊疗执行售价合计")
    private BigDecimal totalExecutionSaleAmount;

    /** 耗材总数量（与 IF-04 totalQuantity 同口径）。 */
    @Schema(description = "耗材总数量")
    private BigDecimal totalConsumableQuantity;

    /** 耗材售价合计（结算刻度）。 */
    @Schema(description = "耗材售价合计")
    private BigDecimal totalConsumableSaleAmount;

    /** 耗材进价合计；全无进价时为 null。 */
    @Schema(description = "耗材进价合计")
    private BigDecimal totalConsumablePurchaseAmount;
}
