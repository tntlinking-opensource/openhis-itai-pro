/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药库退供应商单列表分页查询条件（IF-WHS-RT-02，按 {@code bus_no} 聚合）。
 */
@Getter
@Setter
@Schema(description = "退供应商单列表分页 IF-WHS-RT-02")
public class PageReturnToSuppliersRequest extends AbstractHealthPageRequest {

    /** 源药库 sourceLocationId。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;

    /** 单据状态 statusEnum。 */
    @Schema(description = "单据状态 statusEnum")
    private Integer statusEnum;

    /** 申请时间起 applyTimeFrom。 */
    @Schema(description = "申请时间起 applyTimeFrom")
    private LocalDateTime applyTimeFrom;

    /** 申请时间止 applyTimeTo。 */
    @Schema(description = "申请时间止 applyTimeTo")
    private LocalDateTime applyTimeTo;

    /**
     * 供应类型排序号；固定为 {@code 3}（药库退供应商单）。可空时服务端仍按退货类型查询。
     */
    @Schema(description = "供应类型 typeEnum，固定为 3（药库退供应商）")
    private Integer typeEnum;
}
