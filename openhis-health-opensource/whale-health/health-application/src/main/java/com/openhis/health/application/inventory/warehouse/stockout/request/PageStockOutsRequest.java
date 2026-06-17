/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.stockout.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * <p>分页与关键字字段继承 {@link AbstractHealthPageRequest}（B.10.11：对外 {@code pageNo} 为 1-based；{@link AbstractPageQuery#springPageIndex()} 供内部转 0-based）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class PageStockOutsRequest extends AbstractHealthPageRequest {

    @Schema(description = "源药库主键，0 表示机构内全部可见药库")
    private Long sourceLocationId;

    @Schema(description = "目的位置筛选")
    private Long purposeLocationId;

    /** 单据状态持久化身份，须为 {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}；可空表示不按状态筛选。 */
    @Schema(description = "状态 SupplyStatus#getCode")
    private String statusEnum;

    /** 申请时间窗下限（含）；可空，空时由应用层回落 {@link AbstractPageQuery#getStartDate()}。 */
    @Schema(description = "申请时间起")
    private LocalDateTime applyTimeFrom;

    /** 申请时间窗上限（含）；可空，空时由应用层回落 {@link AbstractPageQuery#getEndDate()}。 */
    @Schema(description = "申请时间止")
    private LocalDateTime applyTimeTo;

    /**
     * 供应类型持久化身份筛选；若传入则须为药库出库 {@link com.openhis.health.domain.share.enums.SupplyType#CABINET_STOCK_OUT} 的
     * {@code getCode()}，否则参数门禁拒绝。
     */
    @Schema(description = "单据类型，须为 CABINET_STOCK_OUT")
    private String typeEnum;
}
