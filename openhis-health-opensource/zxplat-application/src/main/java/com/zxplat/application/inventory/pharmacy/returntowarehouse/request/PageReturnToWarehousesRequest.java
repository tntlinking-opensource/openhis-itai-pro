/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.returntowarehouse.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药房退库单列表分页查询条件。
 */
@Getter
@Setter
public class PageReturnToWarehousesRequest extends AbstractHealthPageRequest {

    /** 源库位主键。 */
    private Long sourceLocationId;
    /** 单据状态持久化码。 */
    private Integer statusEnum;
    /** 申请时间起（含）。 */
    private LocalDateTime applyTimeFrom;
    /** 申请时间止（含）。 */
    private LocalDateTime applyTimeTo;
    /** 固定为 14（药房退库单 sortOrder）。 */
    private Integer typeEnum;
}
