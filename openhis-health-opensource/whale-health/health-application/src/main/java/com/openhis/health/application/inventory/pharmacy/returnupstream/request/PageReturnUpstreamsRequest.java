/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PageReturnUpstreamsRequest extends AbstractHealthPageRequest {

    /** 源库位主键。 */
    private Long sourceLocationId;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
    /** 申请时间起（含）。 */
    private LocalDateTime applyTimeFrom;
    /** 申请时间止（含）。 */
    private LocalDateTime applyTimeTo;
    /** 固定为 3（药库退货单 sortOrder）。 */
    private Integer typeEnum;
}
