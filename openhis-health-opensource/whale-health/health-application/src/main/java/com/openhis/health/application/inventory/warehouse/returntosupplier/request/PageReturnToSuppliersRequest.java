/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class PageReturnToSuppliersRequest extends AbstractHealthPageRequest {

    /** 源药库位置主键。 */
    @Schema(description = "源药库 sourceLocationId")
    private Long sourceLocationId;
    /** 单据状态持久化码。 */
    @Schema(description = "单据状态 statusEnum")
    private Integer statusEnum;
    /** 申请时间区间起。 */
    @Schema(description = "申请时间起 applyTimeFrom")
    private LocalDateTime applyTimeFrom;
    /** 申请时间区间止。 */
    @Schema(description = "申请时间止 applyTimeTo")
    private LocalDateTime applyTimeTo;
    /** 固定为 3（药库退货单 sortOrder）。 */
    private Integer typeEnum;
}
