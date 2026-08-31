/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.dailysettlement.response;

import com.openhis.health.application.physicalexam.financial.dailysettlement.dto.PhysicalExamDailySettlementLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 查询体检日结清单响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询体检日结清单响应")
public class ListPhysicalExamDailySettlementResponse {

    /**
     * 查询时间展示，如 {@code 2026-08-26至2026-08-26}。
     */
    @Schema(description = "查询时间展示")
    private String queryTimeText;

    /**
     * 体检人次（去重就诊数）。
     */
    @Schema(description = "体检人次")
    private Integer visitCount;

    /**
     * 人次展示，如 {@code 2人}。
     */
    @Schema(description = "人次展示")
    private String visitCountText;

    /**
     * 金额合计（结算刻度 2 位）。
     */
    @Schema(description = "金额合计")
    private BigDecimal totalAmount;

    /**
     * 一日收费明细，按科室、开单人、病人、项目排序。
     */
    @Schema(description = "一日收费明细")
    private List<PhysicalExamDailySettlementLineDto> lines = new ArrayList<>();
}
