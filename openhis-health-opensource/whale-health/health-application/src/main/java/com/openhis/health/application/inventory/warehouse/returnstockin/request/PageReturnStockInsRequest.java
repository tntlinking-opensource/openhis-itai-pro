/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 退库入库壳单分页列表请求（IF-WHS-RSI-02 · W-05）。
 *
 * <p>分页字段继承 {@link AbstractHealthPageRequest}；{@code purposeLocationId} 为接收退库的药库位置，与前端对齐。</p>
 */
@Getter
@Setter
@Schema(description = "退库入库壳单分页列表 IF-WHS-RSI-02")
public class PageReturnStockInsRequest extends AbstractHealthPageRequest {

    /** 目的药库 purposeLocationId。 */
    @Schema(description = "目的药库 purposeLocationId")
    private Long purposeLocationId;

    /** 单据状态 statusEnum（SupplyStatus 排序号）。 */
    @Schema(description = "单据状态 statusEnum（SupplyStatus 排序号）")
    private Integer statusEnum;

    /** 申请时间起 applyTimeFrom。 */
    @Schema(description = "申请时间起 applyTimeFrom")
    private LocalDateTime applyTimeFrom;

    /** 申请时间止 applyTimeTo。 */
    @Schema(description = "申请时间止 applyTimeTo")
    private LocalDateTime applyTimeTo;

    /** 供应类型 typeEnum，固定为 5（退库入库壳单）。 */
    @Schema(description = "供应类型 typeEnum，固定为 5（退库入库壳单）")
    private Integer typeEnum;
}
