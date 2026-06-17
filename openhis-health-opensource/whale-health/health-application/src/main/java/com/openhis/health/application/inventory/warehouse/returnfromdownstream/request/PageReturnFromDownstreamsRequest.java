/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returnfromdownstream.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class PageReturnFromDownstreamsRequest extends AbstractHealthPageRequest {

    /** 接收退库的药库位置（列表筛选用，与前端 purposeLocationId 对齐）。 */
    private Long purposeLocationId;
    /** 兼容旧契约；未传 purposeLocationId 时可回落使用。 */
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
